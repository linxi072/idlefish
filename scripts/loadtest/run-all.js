#!/usr/bin/env node
/**
 * F-15.4 全链路压测编排器
 * ------------------------------------------------------------------
 * 复用既有真机联调脚本，统一编排「IM 实时收发」与「支付回流」两类压测，
 * 汇总为一份 loadtest-report.json，便于回归与容量评估。
 *
 * 用法：
 *   离线（无需后端，仅校验脚本与测量口径不崩）：
 *     node scripts/loadtest/run-all.js --mock
 *   真机/本地后端联调：
 *     node scripts/loadtest/run-all.js --live http://localhost:8080 --rounds 200 --users 4
 *
 * 说明：本编排器只负责调度与汇总，压测逻辑与测量口径分别由
 *   scripts/im-loadtest.js、scripts/pay-callback-sim.js 持有，避免逻辑重复。
 */
'use strict';
const path = require('path');
const fs = require('fs');
const { spawnSync } = require('child_process');

function parseArgs(argv) {
  const a = { mock: false, live: null, rounds: 100, users: 2, amount: 138000, out: 'loadtest-report.json' };
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i];
    if (k === '--mock') a.mock = true;
    else if (k === '--live') { a.live = (argv[++i] || 'http://localhost:8080').replace(/\/$/, ''); a.mock = false; }
    else if (k === '--rounds') a.rounds = parseInt(argv[++i], 10) || 100;
    else if (k === '--users') a.users = parseInt(argv[++i], 10) || 2;
    else if (k === '--amount') a.amount = parseInt(argv[++i], 10) || 138000;
    else if (k === '--out') a.out = argv[++i] || 'loadtest-report.json';
  }
  return a;
}

function runScript(scriptRel, extraArgs) {
  const script = path.join(__dirname, '..', scriptRel);
  const cmd = [script, ...extraArgs];
  const res = spawnSync(process.execPath, cmd, { encoding: 'utf8', timeout: 10 * 60 * 1000 });
  const stdout = (res.stdout || '').trim();
  const stderr = (res.stderr || '').trim();
  const exit = res.status == null ? (res.error ? 1 : 0) : res.status;
  return { script: scriptRel, exit, stdout, stderr, error: res.error ? String(res.error) : null };
}

function lastLine(text) {
  const lines = text.split('\n').map(s => s.trim()).filter(Boolean);
  return lines.length ? lines[lines.length - 1] : '';
}

function main() {
  const a = parseArgs(process.argv);
  const mode = a.mock ? ['--mock'] : ['--live', a.live];
  const ts = new Date().toISOString();

  console.log(`=== 全链路压测编排（${a.mock ? '离线 mock' : 'live ' + a.live}）===\n`);

  const imArgs = a.mock
    ? ['--mock', '--rounds', String(a.rounds)]
    : ['--live', a.live, '--users', String(a.users), '--rounds', String(a.rounds)];
  const payArgs = a.mock
    ? ['--mock']
    : ['--live', a.live, '--amount', String(a.amount)];

  const im = runScript('im-loadtest.js', imArgs);
  const pay = runScript('pay-callback-sim.js', payArgs);

  console.log(`[im-loadtest] exit=${im.exit} | ${lastLine(im.stdout)}`);
  if (im.stderr) console.log(`[im-loadtest][stderr] ${lastLine(im.stderr)}`);
  console.log(`[pay-callback-sim] exit=${pay.exit} | ${lastLine(pay.stdout)}`);
  if (pay.stderr) console.log(`[pay-callback-sim][stderr] ${lastLine(pay.stderr)}`);

  // 汇总：只要任一子脚本非 0 退出即视为压测异常
  const failed = [im, pay].filter(r => r.exit !== 0);
  const report = {
    tool: 'idlefish-loadtest-orchestrator',
    generatedAt: ts,
    mode: a.mock ? 'mock' : 'live',
    target: a.mock ? null : a.live,
    params: { rounds: a.rounds, users: a.users, amount: a.amount },
    results: {
      im: { script: 'scripts/im-loadtest.js', exit: im.exit, summary: lastLine(im.stdout) },
      pay: { script: 'scripts/pay-callback-sim.js', exit: pay.exit, summary: lastLine(pay.stdout) }
    },
    status: failed.length === 0 ? 'PASS' : 'FAIL'
  };

  fs.writeFileSync(a.out, JSON.stringify(report, null, 2));
  console.log(`\n汇总状态: ${report.status}（明细见 ${a.out}）`);
  process.exit(failed.length === 0 ? 0 : 1);
}

main();
