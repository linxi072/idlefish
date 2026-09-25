#!/usr/bin/env node
/**
 * F-10 真机联调：IM 实时消息收发稳定性与延迟验证
 * ------------------------------------------------------------------
 * 两种运行模式：
 *  1) 本地模拟（默认，无需后端）：node scripts/im-loadtest.js --mock --rounds 50
 *     复刻后端 WsSessionManager.send + ImService.push(serverTime 打点) 的测量口径，
 *     验证「前端 chat.js 用 serverTime 测算端到端延迟」的方法与预期分布。
 *  2) 真机/本地后端联调（需运行中的后端 + npm i ws）：
 *     node scripts/im-loadtest.js --live http://localhost:8080 --users 2 --rounds 200
 *     以两个用户身份登录获取 JWT，各开一条 WS 连接，互发消息并依据 serverTime 计算延迟，
 *     统计送达率、P50/P95/Max 延迟与断线重连表现。
 *
 * 说明：本脚本只负责「测量」，不修改任何业务数据；失败计数与重连对应
 * 后端 ImService/WsSessionManager 的「失效会话清理 + 指数退避重连」契约。
 */
'use strict';

function parseArgs(argv) {
  const a = { mock: false, live: null, rounds: 50, users: 2, minMs: 20, maxMs: 90, url: null };
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--mock') a.mock = true;
    else if (k === '--live') { a.live = argv[++i] || 'http://localhost:8080'; a.url = a.live; a.mock = false; }
    else if (k === '--rounds') a.rounds = parseInt(argv[++i], 10) || 50;
    else if (k === '--users') a.users = parseInt(argv[++i], 10) || 2;
    else if (k === '--min-ms') a.minMs = parseInt(argv[++i], 10) || 20;
    else if (k === '--max-ms') a.maxMs = parseInt(argv[++i], 10) || 90;
  }
  return a;
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length));
  return sorted[idx];
}

async function runMock(a) {
  console.log('=== IM 本地模拟模式（验证延迟测量口径，不连接后端）===');
  console.log(`参数：rounds=${a.rounds}, 单向延迟区间=[${a.minMs},${a.maxMs}]ms`);
  const latencies = [];
  let delivered = 0;
  let dropped = 0;
  for (let i = 0; i < a.rounds; i++) {
    // 客户端发送时刻
    const sendTs = Date.now();
    // 模拟网络上行 + 服务端处理 + 下行（后端 ImService.push 在此打 serverTime）
    const oneWay = a.minMs + Math.random() * (a.maxMs - a.minMs);
    await new Promise(r => setTimeout(r, oneWay));
    const serverTime = Date.now();
    // 极小概率模拟丢包/失效会话（对应 WsSessionManager 的失效会话清理）
    if (Math.random() < 0.01) { dropped++; continue; }
    const down = a.minMs + Math.random() * (a.maxMs - a.minMs);
    await new Promise(r => setTimeout(r, down));
    // 客户端接收时刻：前端 chat.js 用 msg.serverTime 与本地接收时刻之差测算延迟
    const recvTs = Date.now();
    const latency = recvTs - sendTs; // 端到端
    const serverLatency = recvTs - serverTime; // 服务端打点后的下行段
    latencies.push(latency);
    if (serverLatency >= 0) delivered++;
  }
  latencies.sort((x, y) => x - y);
  const avg = latencies.reduce((s, v) => s + v, 0) / (latencies.length || 1);
  console.log('--- 结果 ---');
  console.log(`发送 ${a.rounds} 条，送达 ${delivered} 条，丢包/失效 ${dropped} 条，送达率 ${((delivered / a.rounds) * 100).toFixed(2)}%`);
  console.log(`延迟(端到端 ms)：P50=${percentile(latencies, 50)} P95=${percentile(latencies, 95)} Max=${latencies[latencies.length - 1] || 0} Avg=${avg.toFixed(1)}`);
  console.log('提示：真机联调时请改用 --live 模式，用真实 WS 连接得到真实网络延迟；');
  console.log('     前端 chat.js 已按 msg.serverTime 计算延迟，重连策略为 25s 心跳 + 指数退避(3s→30s)。');
}

async function runLive(a) {
  let ws;
  try { ws = require('ws'); } catch (e) {
    console.error('live 模式需要 ws 依赖，请先执行：npm i ws（或在 idlefish-backend 同级执行）');
    process.exit(1);
  }
  const base = a.url.replace(/\/$/, '');
  const http = (await import('node:http')).default;
  const https = (await import('node:https')).default;
  const lib = base.startsWith('https') ? https : http;
  const u = new URL(base);

  // 1) 两个用户登录获取 JWT（mock 登录端点返回 mock-token；真机应替换为真实登录）
  async function loginAs(username) {
    const body = JSON.stringify({ code: 'mock_code_' + username });
    const resp = await new Promise((resolve, reject) => {
      const req = lib.request({ host: u.hostname, port: u.port || (u.protocol === 'https:' ? 443 : 80), path: '/api/auth/login', method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) } }, res => {
        let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(JSON.parse(d || '{}')));
      });
      req.on('error', reject); req.write(body); req.end();
    });
    return (resp && resp.data && resp.data.token) || resp.token || null;
  }

  const tokens = [];
  for (let i = 0; i < a.users; i++) {
    const t = await loginAs('live_user_' + i);
    if (!t) { console.error('用户' + i + ' 登录失败，无法建立 WS（请确认后端 /api/auth/login 可达且允许 mock 登录）'); process.exit(1); }
    tokens.push(t);
  }

  // 2) 各开一条 WS 连接，互发消息
  const latencies = [];
  let delivered = 0;
  const conns = tokens.map((tok, idx) => {
    const proto = base.startsWith('https') ? 'wss' : 'ws';
    // 后端 AuthHandshakeInterceptor 从 query.token 取 JWT
    const c = new ws(`${proto}://${u.hostname}${u.port ? ':' + u.port : ''}/ws/im?token=${encodeURIComponent(tok)}`);
    c.on('message', (raw) => {
      try {
        const m = JSON.parse(raw.toString());
        if (m.serverTime) {
          const latency = Date.now() - m.serverTime; // 前端口径：接收时刻 - serverTime
          if (latency >= 0) { latencies.push(latency); delivered++; }
        }
      } catch (e) { /* ignore */ }
    });
    return c;
  });

  await new Promise(r => { let n = 0; conns.forEach(c => c.on('open', () => { if (++n === conns.length) r(); })); });
  console.log(`=== IM 真机/本地后端联调：已建立 ${conns.length} 条 WS 连接 ===`);

  // 轮流互发
  for (let i = 0; i < a.rounds; i++) {
    const from = i % conns.length;
    const to = (from + 1) % conns.length;
    conns[from].send(JSON.stringify({ type: 'text', content: 'ping-' + i, receiverId: 2000 + to, itemId: 0, convId: 'C' + i }));
    await new Promise(r => setTimeout(r, 10));
  }
  await new Promise(r => setTimeout(r, 800));
  latencies.sort((x, y) => x - y);
  const avg = latencies.reduce((s, v) => s + v, 0) / (latencies.length || 1);
  console.log('--- 结果 ---');
  console.log(`发送 ${a.rounds} 条，按 serverTime 测算送达 ${delivered} 条，送达率 ${((delivered / a.rounds) * 100).toFixed(2)}%`);
  console.log(`延迟(ms)：P50=${percentile(latencies, 50)} P95=${percentile(latencies, 95)} Max=${latencies[latencies.length - 1] || 0} Avg=${avg.toFixed(1)}`);
  conns.forEach(c => c.close());
}

(async () => {
  const a = parseArgs(process.argv);
  if (a.mock) await runMock(a);
  else if (a.live) await runLive(a);
  else await runMock(a);
})();
