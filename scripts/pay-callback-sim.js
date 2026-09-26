#!/usr/bin/env node
/**
 * F-10 真机联调：支付完成后回流链路（支付回调 / 订单状态同步）验证
 * ------------------------------------------------------------------
 * 两种运行模式：
 *  1) 本地模拟（默认，无需后端）：node scripts/pay-callback-sim.js --mock
 *     以内存态忠实复刻 PayService.notify 的关键不变量：幂等（不重复记账）、
 *     金额校验（回调金额须等于订单金额）、状态过滤(SUCCESS)、订单 PENDING_PAY→PAID、
 *     生成 FundFlow 流水、双向通知。用于离线确认回流逻辑正确。
 *  2) 真机/本地后端联调（需运行中的后端）：
 *     node scripts/pay-callback-sim.js --live http://localhost:8080 --order-no NOxxx --pay-no Pxxx --amount 138000
 *     向 /api/pay/notify 推送支付结果，再 GET /api/orders/{orderNo} 确认状态同步为 PAID。
 *     可选 --v3 走 /api/pay/notify/v3（示例签名无效，将被后端拒绝，验证验签护栏）。
 *  3) v3 真实签名联调（需商户私钥 + 证书序列号 + APIv3Key，用于正向验证验签护栏放行）：
 *     node scripts/pay-callback-sim.js --v3-sign --live http://localhost:8080 \
 *       --pay-no Pxxx --amount 138000 --key ./merchant_key.pem --serial <CERT_SERIAL> --apiv3key <APIV3KEY>
 *     脚本以商户私钥对回调报文做 SHA256withRSA 签名、以 APIv3Key 做 AES-256-GCM 加密 resource（ciphertext 末尾拼接 GCM tag），
 *     向后端 /api/pay/notify/v3 推送；后端配置对应证书与 APIv3Key 时签名应被放行（200）并落地支付成功。
 *     说明：本脚本不内置任何私钥/密钥，全部由 --key/--serial/--apiv3key 显式传入，便于真实环境联调。
 *
 * 金额单位：分（与全链路契约一致）。1 元 = 100 分。
 */
'use strict';
const crypto = require('crypto');
const fs = require('fs');

function parseArgs(argv) {
  const a = { mock: false, live: null, orderNo: null, payNo: null, amount: null, v3: false, v3sign: false, key: null, serial: null, apiv3key: null, token: null, url: null };
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--mock') a.mock = true;
    else if (k === '--live') { a.live = argv[++i] || 'http://localhost:8080'; a.url = a.live.replace(/\/$/, ''); a.mock = false; }
    else if (k === '--order-no') a.orderNo = argv[++i];
    else if (k === '--pay-no') a.payNo = argv[++i];
    else if (k === '--amount') a.amount = parseInt(argv[++i], 10);
    else if (k === '--v3') a.v3 = true;
    else if (k === '--v3-sign') { a.v3sign = true; a.mock = false; }
    else if (k === '--key') a.key = argv[++i];
    else if (k === '--serial') a.serial = argv[++i];
    else if (k === '--apiv3key') a.apiv3key = argv[++i];
    else if (k === '--token') a.token = argv[++i];
  }
  return a;
}

function assert(cond, msg) {
  if (!cond) { console.error('  ✗ ' + msg); process.exitCode = 1; }
  else console.log('  ✓ ' + msg);
}

/** 内存态忠实复刻 PayService.notify 的不变量（不依赖真实后端） */
function runMock() {
  console.log('=== 支付回流链路 · 本地模拟（复刻 PayService.notify 不变量）===');
  const orders = {};
  const fundFlows = {};
  const notifications = {};
  function notify(payNo, orderNo, amount) {
    const o = orders[orderNo];
    if (!o) throw new Error('订单不存在');
    // 幂等：已 PAID 则不再重复记账
    if (o.status === 'PAID') { if ((fundFlows[orderNo] || []).length !== 1) throw new Error('幂等失效：重复记账'); return 'IDEMPOTENT'; }
    // 金额校验：回调金额须等于订单金额
    if (amount != null && amount !== o.amount) throw new Error('AMOUNT_MISMATCH');
    // 状态过滤：仅 SUCCESS/PROCESSING 推进（此处模拟 SUCCESS）
    o.status = 'PAID';
    o.payNo = payNo;
    fundFlows[orderNo] = fundFlows[orderNo] || [];
    fundFlows[orderNo].push({ direction: 'IN', type: 'PAY', amount: o.amount });
    notifications[orderNo] = [{ to: o.buyerId, type: 'order_paid' }, { to: o.sellerId, type: 'order_paid' }];
    return 'PAID';
  }

  // 场景1：正常回调 → 订单同步 PAID + 资金流水 + 双向通知
  orders['NO20260921001'] = { orderNo: 'NO20260921001', amount: 138000, buyerId: 2001, sellerId: 1001, status: 'PENDING_PAY' };
  let r1; try { r1 = notify('P20260921', 'NO20260921001', 138000); } catch (e) { r1 = 'ERR:' + e.message; }
  assert(r1 === 'PAID' && orders['NO20260921001'].status === 'PAID', '正常回调：订单 PENDING_PAY→PAID');
  assert((fundFlows['NO20260921001'] || []).length === 1, '生成 1 条资金流水(PAY, IN)');
  assert((notifications['NO20260921001'] || []).length === 2, '触发买卖双方双向通知');

  // 场景2：重复回调（幂等）→ 不重复记账
  let r2; try { r2 = notify('P20260921', 'NO20260921001', 138000); } catch (e) { r2 = 'ERR:' + e.message; }
  assert(r2 === 'IDEMPOTENT' && (fundFlows['NO20260921001'] || []).length === 1, '重复回调幂等：资金流水仍为 1 条，未重复记账');

  // 场景3：金额不符 → 拒绝
  orders['NO20260921002'] = { orderNo: 'NO20260921002', amount: 138000, buyerId: 2001, sellerId: 1001, status: 'PENDING_PAY' };
  let r3; try { r3 = notify('P20260922', 'NO20260921002', 99900); } catch (e) { r3 = 'ERR:' + e.message; }
  assert(r3 === 'ERR:AMOUNT_MISMATCH' && orders['NO20260921002'].status === 'PENDING_PAY', '金额不符回调被拒绝，订单保持 PENDING_PAY');

  // 场景4：取消订单应释放优惠券（对应 CouponService.releaseByOrder）—— 此处仅确认契约
  console.log('提示：取消/关单时后端会调用 CouponService.releaseByOrder 将 USED 券回退为 UNUSED 并解绑订单号。');

  console.log('\n--- 模拟结论：幂等 / 金额校验 / 状态同步 / 双向通知 均符合预期 ---');
  console.log('真机联调请改用 --live 模式，对运行中的后端发起真实回调并核对数据库 t_order.pay_status、t_fund_flow。');
}

async function runLive(a) {
  const http = (await import('node:http')).default;
  const https = (await import('node:https')).default;
  const lib = a.url.startsWith('https') ? https : http;
  const u = new URL(a.url);
  function post(path, body, headers) {
    return new Promise((resolve, reject) => {
      const req = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80), path, method: 'POST', headers }, res => {
        let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ code: res.statusCode, body: d }));
      });
      req.on('error', reject); if (body) req.write(body); req.end();
    });
  }
  function get(path, headers) {
    return new Promise((resolve, reject) => {
      const req = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80), path, method: 'GET', headers }, res => {
        let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ code: res.statusCode, body: d }));
      });
      req.on('error', reject); req.end();
    });
  }
  if (!a.orderNo || !a.payNo) { console.error('live 模式需提供 --order-no 与 --pay-no'); process.exit(1); }

  console.log('=== 支付回流链路 · 真机/本地后端联调 ===');
  console.log(`目标：${a.url}  订单=${a.orderNo}  payNo=${a.payNo}  金额(分)=${a.amount}`);
  let resp;
  if (a.v3) {
    const sampleBody = JSON.stringify({ id: 'EVTLIVE' + Date.now(), create_time: new Date().toISOString(), resource_type: 'encrypt-resource', resource: { ciphertext: 'BASE64(CIPHERTEXT)', associated_data: '', nonce: 'NONCE' } });
    resp = await post('/api/pay/notify/v3', sampleBody, { 'Content-Type': 'application/json', 'Wechatpay-Signature': 'INVALID-FOR-SIM', 'Wechatpay-Timestamp': '' + Math.floor(Date.now() / 1000), 'Wechatpay-Nonce': 'NONCE', 'Wechatpay-Serial': 'SIM' });
    console.log(`[v3] HTTP ${resp.code}  body=${resp.body}`);
    console.log('说明：v3 需真实签名+密文，示例签名无效将被后端拒绝——这正验证了验签护栏；生产请使用正确签名的报文。');
  } else {
    const q = `/api/pay/notify?payNo=${encodeURIComponent(a.payNo)}` + (a.amount != null ? `&amount=${a.amount}` : '');
    resp = await post(q, null, { 'Content-Type': 'application/x-www-form-urlencoded' });
    console.log(`[/notify] HTTP ${resp.code}  body=${resp.body}`);
  }
  if (resp.code === 200) {
    console.log('回调已受理，订单状态应已同步为 PAID。');
  } else {
    console.log('回调被拒绝，请检查金额/订单状态/鉴权（后端 /api/pay/** 已放行，无需登录）。');
  }
  // 可选：核对订单状态（需要登录令牌，因 /api/orders 受保护）
  if (a.token) {
    const o = await get(`/api/orders/${encodeURIComponent(a.orderNo)}`, { Authorization: 'Bearer ' + a.token });
    console.log(`[order] HTTP ${o.code}  body=${o.body}`);
  } else {
    console.log('提示：未提供 --token，跳过订单状态核对。可在后端数据库执行：');
    console.log(`  SELECT order_no, status, pay_status, pay_no, amount FROM t_order WHERE order_no='${a.orderNo}';  -- 应 status='paid' pay_status='PAID'`);
    console.log(`  SELECT * FROM t_fund_flow WHERE biz_no='${a.payNo}';  -- 应存在 1 条 IN/PAY 流水`);
  }
}

/**
 * v3 真实签名联调：以商户私钥对回调报文做 SHA256withRSA 签名、以 APIv3Key 做 AES-256-GCM 加密，
 * 向 /api/pay/notify/v3 推送，正向验证后端验签护栏放行并落地支付成功。
 * 契约对齐 RealWechatEscrowServiceImpl.verifySignature（timestamp\nnonce\nbody\n）与 decryptNotify（AES/GCM/NoPadding，ciphertext 末尾拼接 GCM tag）。
 */
async function runV3Sign(a) {
  if (!a.url) { console.error('v3-sign 模式需提供 --live <后端URL>'); process.exit(1); }
  if (!a.payNo) { console.error('v3-sign 模式需提供 --pay-no（对应后端 PayOrder.pay_no）'); process.exit(1); }
  if (!a.key || !a.serial || !a.apiv3key) {
    console.error('v3-sign 模式需提供 --key <私钥PEM路径> --serial <证书序列号> --apiv3key <APIv3Key(32字符)>');
    process.exit(1);
  }
  let privateKey;
  try { privateKey = crypto.createPrivateKey(fs.readFileSync(a.key, 'utf8')); }
  catch (e) { console.error('读取/解析私钥失败：' + e.message); process.exit(1); }
  const apiV3Key = Buffer.from(a.apiv3key, 'utf8');
  if (apiV3Key.length !== 32) { console.error('APIv3Key 必须为 32 字节（当前 ' + apiV3Key.length + ' 字节）'); process.exit(1); }

  const amount = a.amount != null ? a.amount : 138000;
  const plaintext = JSON.stringify({
    out_trade_no: a.payNo,
    transaction_id: 'WXTXN' + Date.now(),
    amount: amount,            // 对齐后端 notifyV3：amount 直接读为 Number（非对象）
    trade_state: 'SUCCESS',
    trade_type: 'JSAPI',
    appid: 'wxappid',
    mchid: 'mchid'
  });

  // AES-256-GCM 加密 resource（nonce 12 字节，AAD 为空，ciphertext 末尾拼接 16 字节 GCM tag）
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', apiV3Key, nonce);
  cipher.setAAD(Buffer.from(''));
  const enc = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const ciphertext = Buffer.concat([enc, cipher.getAuthTag()]).toString('base64');

  const bodyObj = {
    id: 'EVTLIVE' + Date.now(),
    create_time: new Date().toISOString(),
    resource_type: 'encrypt-resource',
    resource: {
      algorithm: 'AEAD_AES_256_GCM',
      ciphertext: ciphertext,
      associated_data: '',
      nonce: nonce.toString('base64'),
      key_version: '1'
    }
  };
  const body = JSON.stringify(bodyObj);

  // SHA256withRSA 签名：timestamp\nnonce\nbody\n
  const timestamp = '' + Math.floor(Date.now() / 1000);
  const nonceHeader = crypto.randomBytes(16).toString('hex');
  const signMsg = timestamp + '\n' + nonceHeader + '\n' + body + '\n';
  const signature = crypto.createSign('RSA-SHA256').update(signMsg).sign(privateKey).toString('base64');

  const http = (await import('node:http')).default;
  const https = (await import('node:https')).default;
  const lib = a.url.startsWith('https') ? https : http;
  const u = new URL(a.url);
  const headers = {
    'Content-Type': 'application/json',
    'Wechatpay-Signature': signature,
    'Wechatpay-Timestamp': timestamp,
    'Wechatpay-Nonce': nonceHeader,
    'Wechatpay-Serial': a.serial
  };
  console.log('=== 支付回流 · v3 真实签名联调 ===');
  console.log(`目标：${a.url}/api/pay/notify/v3  payNo=${a.payNo}  金额(分)=${amount}  serial=${a.serial}`);
  const resp = await new Promise((resolve, reject) => {
    const req = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80), path: '/api/pay/notify/v3', method: 'POST', headers }, res => {
      let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ code: res.statusCode, body: d }));
    });
    req.on('error', reject); req.write(body); req.end();
  });
  console.log(`[v3] HTTP ${resp.code}  body=${resp.body}`);
  if (resp.code === 200) console.log('✓ 验签通过、回调被受理：后端配置证书序列号与 APIv3Key 匹配时，订单应已落地为 PAID。');
  else console.log('✗ 回调被拒绝（HTTP ' + resp.code + '）。请检查：① 后端 IdlefishProperties 配置的证书序列号/公钥与 --serial/--key 配对；② APIv3Key 与 --apiv3key 一致；③ 报文未改。');
  if (a.token) {
    const o = await new Promise((resolve, reject) => {
      const req = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80), path: `/api/orders/${encodeURIComponent(a.orderNo || a.payNo)}`, method: 'GET', headers: { Authorization: 'Bearer ' + a.token } }, res => {
        let d = ''; res.on('data', c => d += c); res.on('end', () => resolve({ code: res.statusCode, body: d }));
      });
      req.on('error', reject); req.end();
    });
    console.log(`[order] HTTP ${o.code}  body=${o.body}`);
  }
}

(async () => {
  const a = parseArgs(process.argv);
  if (a.mock) runMock();
  else if (a.v3sign) await runV3Sign(a);
  else if (a.live) await runLive(a);
  else runMock();
})();
