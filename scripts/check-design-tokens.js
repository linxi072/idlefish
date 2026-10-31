#!/usr/bin/env node
/**
 * ITER-U04 设计令牌扫描器（零依赖）
 * ---------------------------------------------------------------------------
 * 用途：检出 pc-admin 源码中「绕过设计令牌、直接写死」的颜色值（#hex / rgb() / rgba() / hsl()）。
 * 规则：色值只允许出现在唯一令牌文件 src/styles/tokens.css；
 *       视图、组件、布局样式一律引用 var(--token)。
 *
 * 用法：
 *   node scripts/check-design-tokens.js                       # 扫描并判定（阈值 0）
 *   node scripts/check-design-tokens.js --max 5               # 允许最多 5 处（渐进迁移期）
 *   node scripts/check-design-tokens.js --json                # 机器可读输出
 *   node scripts/check-design-tokens.js --verbose             # 打印命中行内容
 *   node scripts/check-design-tokens.js --dir pc-admin/src    # 指定扫描目录
 *   node scripts/check-design-tokens.js --include-js          # 额外扫描 .js（默认只扫 .vue/.css）
 *
 * 退出码：0 = 通过（未超阈值）；1 = 超阈值；2 = 参数/目录错误。
 * 豁免：src/styles/tokens.css（令牌定义）、src/mock（模拟数据色字段）、node_modules、dist。
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');

// 匹配 #hex（3/4/6/8 位）与 rgb()/rgba()/hsl()/hsla()
// 前后断言避免误伤 Vue 插槽简写（#default）与 URL 锚点（#app）
const COLOR_RE =
  /(?<![0-9a-zA-Z_&])#(?:[0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-zA-Z])|\b(?:rgb|hsl)a?\([^)]*\)/g;

const ALWAYS_SKIP_DIRS = new Set(['node_modules', 'dist', '.git', 'coverage']);
// 豁免文件：令牌定义本身
const EXEMPT_FILES = new Set(['styles/tokens.css']);
// 豁免目录：模拟数据中的业务色字段（非样式）
const EXEMPT_DIRS = ['mock'];

function parseArgs(argv) {
  const opts = { max: 0, json: false, verbose: false, dir: null, includeJs: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') opts.json = true;
    else if (a === '--verbose') opts.verbose = true;
    else if (a === '--include-js') opts.includeJs = true;
    else if (a === '--max') {
      const n = Number(argv[++i]);
      if (!Number.isFinite(n) || n < 0) throw new Error('--max 需要非负整数');
      opts.max = n;
    } else if (a === '--dir') {
      opts.dir = argv[++i];
      if (!opts.dir) throw new Error('--dir 需要目录参数');
    } else if (a === '-h' || a === '--help') {
      opts.help = true;
    } else {
      throw new Error('未知参数: ' + a);
    }
  }
  return opts;
}

function listFiles(dir, includeJs, out) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) {
      if (ALWAYS_SKIP_DIRS.has(name)) continue;
      const rel = path.relative(dir, full);
      if (EXEMPT_DIRS.some((d) => name === d || rel === d)) continue;
      listFiles(full, includeJs, out);
    } else if (/\.(vue|css)$/.test(name) || (includeJs && /\.js$/.test(name))) {
      out.push(full);
    }
  }
}

function scanFile(full, baseDir) {
  const rel = path.relative(baseDir, full).split(path.sep).join('/');
  if (EXEMPT_FILES.has(rel)) return [];
  const lines = fs.readFileSync(full, 'utf8').split('\n');
  const hits = [];
  lines.forEach((line, idx) => {
    COLOR_RE.lastIndex = 0;
    let m;
    while ((m = COLOR_RE.exec(line)) !== null) {
      hits.push({ file: rel, line: idx + 1, value: m[0], text: line.trim() });
    }
  });
  return hits;
}

function main() {
  let opts;
  try {
    opts = parseArgs(process.argv.slice(2));
  } catch (e) {
    console.error('[design:check] 参数错误：' + e.message);
    process.exit(2);
  }
  if (opts.help) {
    console.log('用法: node scripts/check-design-tokens.js [--max N] [--json] [--verbose] [--dir DIR] [--include-js]');
    return;
  }

  const target = opts.dir ? path.resolve(ROOT, opts.dir) : path.join(ROOT, 'pc-admin', 'src');
  if (!fs.existsSync(target) || !fs.statSync(target).isDirectory()) {
    console.error('[design:check] 目录不存在：' + target);
    process.exit(2);
  }

  const files = [];
  listFiles(target, opts.includeJs, files);
  files.sort();

  const all = [];
  for (const f of files) all.push(...scanFile(f, target));

  const byFile = new Map();
  for (const h of all) {
    if (!byFile.has(h.file)) byFile.set(h.file, []);
    byFile.get(h.file).push(h);
  }

  const pass = all.length <= opts.max;

  if (opts.json) {
    console.log(JSON.stringify({
      pass,
      threshold: opts.max,
      total: all.length,
      files: [...byFile.entries()].map(([file, hits]) => ({
        file,
        count: hits.length,
        hits: hits.map((h) => ({ line: h.line, value: h.value }))
      }))
    }, null, 2));
    process.exit(pass ? 0 : 1);
  }

  console.log('ITER-U04 设计令牌扫描');
  console.log('扫描目录: ' + path.relative(ROOT, target) + '  (文件 ' + files.length + ' 个)');
  console.log('令牌文件: styles/tokens.css（豁免，唯一允许原始色值）');
  console.log('豁免目录: src/mock（模拟数据色字段）');
  console.log('');

  if (all.length === 0) {
    console.log('✓ 未发现硬编码颜色，全部通过设计令牌引用。');
    process.exit(0);
  }

  console.log((pass ? '△' : '✗') + ' 检出 ' + all.length + ' 处硬编码颜色：');
  for (const [file, hits] of [...byFile.entries()].sort((a, b) => b[1].length - a[1].length)) {
    console.log('  ' + file + '  (' + hits.length + ')');
    for (const h of hits) {
      console.log('    - L' + h.line + '  ' + h.value + (opts.verbose ? '   | ' + h.text : ''));
    }
  }
  console.log('');
  console.log('合计: ' + all.length + ' 处 / ' + byFile.size + ' 个文件（阈值 ' + opts.max + '）');
  if (!pass) {
    console.log('修复建议：在 styles/tokens.css 增补语义令牌，视图内改用 var(--token)。');
  }
  process.exit(pass ? 0 : 1);
}

main();
