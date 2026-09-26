#!/usr/bin/env node
/**
 * F-15.4 小程序端到端（后端契约）校验运行器
 * ------------------------------------------------------------------
 * 以声明式 flows.json 描述小程序核心用户旅程（登录→发布→下单→...），
 * 对运行中的后端逐流逐步校验响应状态与字段契约。
 *
 * 用法：
 *   离线（仅校验 flows.json 结构与变量引用，不连后端）：
 *     node miniprogram/e2e/run-e2e.js --mock
 *   真机 / 本地后端联调：
 *     node miniprogram/e2e/run-e2e.js --live http://localhost:8080
 *
 * 说明：沙箱无微信开发者工具，故「端到端」在此指校验小程序所依赖的后端
 *       契约（端点存在性、鉴权、响应字段），等价覆盖小程序关键路径。
 *       支付真实回调回流由 scripts/pay-callback-sim.js 单独覆盖。
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ALLOWED_METHODS = new Set(['GET', 'POST', 'PUT', 'DELETE', 'PATCH']);
const ALLOWED_AUTH = new Set(['none', 'user', 'admin']);

function parseArgs(argv) {
  const a = { mock: false, live: null, flows: 'flows.json', out: 'e2e-report.json' };
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--mock') a.mock = true;
    else if (k === '--live') { a.live = (argv[++i] || 'http://localhost:8080').replace(/\/$/, ''); a.mock = false; }
    else if (k === '--flows') a.flows = argv[++i];
    else if (k === '--out') a.out = argv[++i];
  }
  return a;
}

function getPath(obj, p) {
  if (!p) return obj;
  return p.split('.').reduce((acc, key) => (acc == null ? undefined : acc[key]), obj);
}

function resolveTemplate(obj, ctx) {
  if (obj == null) return obj;
  let s = JSON.stringify(obj);
  for (const [k, v] of Object.entries(ctx)) {
    s = s.split('{{' + k + '}}').join(typeof v === 'string' ? v : JSON.stringify(v));
  }
  return JSON.parse(s);
}

// ===== 离线结构校验 =====

function collectRefs(obj, refs) {
  const s = JSON.stringify(obj);
  const re = /\{\{(\w+)\}\}/g;
  let m;
  while ((m = re.exec(s)) !== null) refs.add(m[1]);
}

function validateStructure(doc) {
  const errors = [];
  if (typeof doc.baseUrl !== 'string' || !doc.baseUrl) errors.push('baseUrl 必填且为字符串');
  if (typeof doc.setup !== 'object' || doc.setup == null) errors.push('setup 必填且为对象');
  if (!Array.isArray(doc.flows) || doc.flows.length === 0) { errors.push('flows 必填且非空数组'); return errors; }

  const setupKeys = new Set(Object.keys(doc.setup));
  for (const flow of doc.flows) {
    if (typeof flow.name !== 'string' || !flow.name) errors.push('flow.name 必填');
    if (!Array.isArray(flow.steps) || flow.steps.length === 0) { errors.push(`flow ${flow.name}: steps 必填非空`); continue; }
    const captured = new Set();
    for (const step of flow.steps) {
      if (!ALLOWED_METHODS.has(step.method)) errors.push(`[${flow.name}/${step.name}] 非法 method: ${step.method}`);
      if (typeof step.path !== 'string' || !step.path.startsWith('/')) errors.push(`[${flow.name}/${step.name}] path 非法: ${step.path}`);
      if (!ALLOWED_AUTH.has(step.auth)) errors.push(`[${flow.name}/${step.name}] 非法 auth: ${step.auth}`);
      if (step.expectStatus != null && (step.expectStatus < 200 || step.expectStatus >= 600))
        errors.push(`[${flow.name}/${step.name}] expectStatus 越界`);
      if (step.expectFields != null && (!Array.isArray(step.expectFields) || !step.expectFields.every(f => typeof f === 'string')))
        errors.push(`[${flow.name}/${step.name}] expectFields 须为字符串数组`);
      if (step.body != null && typeof step.body !== 'object') errors.push(`[${flow.name}/${step.name}] body 须为对象`);
      if (step.form != null && typeof step.form !== 'object') errors.push(`[${flow.name}/${step.name}] form 须为对象`);
      if (step.capture != null && typeof step.capture !== 'object') errors.push(`[${flow.name}/${step.name}] capture 须为对象`);

      // 变量引用：必须来自 setup 或本流此前步骤的 capture
      const refs = new Set();
      collectRefs({ path: step.path, body: step.body, form: step.form, headers: step.headers }, refs);
      for (const r of refs) {
        if (!setupKeys.has(r) && !captured.has(r)) errors.push(`[${flow.name}/${step.name}] 引用未声明变量: {{${r}}}`);
      }
      if (step.capture) for (const vk of Object.keys(step.capture)) captured.add(vk);
    }
  }
  return errors;
}

// ===== 实时执行 =====

async function runLive(doc, baseUrl) {
  const ctx = { ...doc.setup };
  const report = { tool: 'idlefish-e2e', baseUrl, generatedAt: new Date().toISOString(), flows: [] };
  let failed = 0;
  for (const flow of doc.flows) {
    const flowRes = { name: flow.name, steps: [] };
    for (const step of flow.steps) {
      const r = { name: step.name, method: step.method, path: step.path, pass: true, detail: '' };
      try {
        const resolved = resolveTemplate({ path: step.path, body: step.body, form: step.form, headers: step.headers }, ctx);
        const headers = { 'Content-Type': 'application/json' };
        if (step.auth === 'user' && ctx.token) headers['Authorization'] = 'Bearer ' + ctx.token;
        if (step.auth === 'admin' && ctx.adminToken) headers['Authorization'] = 'Bearer ' + ctx.adminToken;

        let bodyData = undefined;
        if (resolved.body != null) { bodyData = JSON.stringify(resolved.body); headers['Content-Type'] = 'application/json'; }
        else if (resolved.form != null) { bodyData = new URLSearchParams(resolved.form).toString(); headers['Content-Type'] = 'application/x-www-form-urlencoded'; }

        const resp = await fetch(baseUrl + resolved.path, { method: step.method, headers, body: bodyData });
        const text = await resp.text();
        let json = null; try { json = JSON.parse(text); } catch (_) {}
        if (step.expectStatus != null && resp.status !== step.expectStatus) {
          r.pass = false; r.detail = `status ${resp.status} != 期望 ${step.expectStatus}`;
        }
        if (json && step.expectFields) {
          for (const f of step.expectFields) {
            if (getPath(json, f) === undefined) { r.pass = false; r.detail = (r.detail ? r.detail + '; ' : '') + `缺字段 ${f}`; }
          }
        }
        if (step.capture && json) {
          for (const [vk, vp] of Object.entries(step.capture)) { const v = getPath(json, vp); if (v !== undefined) ctx[vk] = v; }
        }
      } catch (e) {
        r.pass = false; r.detail = 'EXCEPTION ' + e.message;
      }
      if (!r.pass) failed++;
      flowRes.steps.push(r);
      console.log(`  [${r.pass ? 'PASS' : 'FAIL'}] ${flow.name}/${step.name} ${step.method} ${step.path}` + (r.detail ? ` -> ${r.detail}` : ''));
    }
    report.flows.push(flowRes);
  }
  report.status = failed === 0 ? 'PASS' : 'FAIL';
  report.failedSteps = failed;
  return report;
}

function main() {
  const a = parseArgs(process.argv);
  const flowPath = path.resolve(__dirname, a.flows);
  if (!fs.existsSync(flowPath)) { console.error('flows 文件不存在: ' + flowPath); process.exit(1); }
  const doc = JSON.parse(fs.readFileSync(flowPath, 'utf8'));

  if (a.mock) {
    const errs = validateStructure(doc);
    if (errs.length) {
      console.log('=== 结构校验：FAIL ===');
      errs.forEach(e => console.log('  ✗ ' + e));
      process.exit(1);
    }
    const steps = doc.flows.reduce((n, f) => n + f.steps.length, 0);
    console.log(`=== 结构校验：PASS（${doc.flows.length} 个流 / ${steps} 步，变量引用全部可解析）===`);
    process.exit(0);
  }

  runLive(doc, a.live).then(report => {
    fs.writeFileSync(a.out, JSON.stringify(report, null, 2));
    console.log(`\n汇总状态: ${report.status}（失败步骤 ${report.failedSteps || 0}，明细见 ${a.out}）`);
    process.exit(report.status === 'PASS' ? 0 : 1);
  }).catch(e => { console.error('运行器异常: ' + e); process.exit(1); });
}

main();
