// pc-admin/scripts/filterTemplates.selfcheck.mjs
// 离线自测：筛选模板工具（localStorage 缺失时退化为内存 shim，无需浏览器）
import * as ft from '../src/utils/filterTemplates.js';

let passed = 0;
let failed = 0;
function ok(cond, msg) {
  if (cond) { passed++; console.log('  ✓ ' + msg); }
  else { failed++; console.error('  ✗ ' + msg); }
}

ft._clearAll();

// 保存两个模板，t2 设为默认
const t1 = ft.save('users', '已封禁用户', { keyword: 'spam', statusFilter: '1' });
const t2 = ft.save('users', '正常用户', { keyword: '', statusFilter: '0' }, { isDefault: true });
ok(t1 && t1.id, 'save 返回带 id 的模板');
ok(t1.filters.statusFilter === '1', 'save 保留筛选条件');
ok(t2.isDefault === true, 'save 支持设为默认');

// 列表：默认置顶
const list = ft.list('users');
ok(list.length === 2, 'list 返回 2 个模板');
ok(list[0].isDefault === true, '默认模板排在首位');

// 应用：返回深拷贝，原始对象不受影响
const applied = ft.apply('users', t1.id);
ok(applied && applied.statusFilter === '1', 'apply 返回筛选条件');
applied.statusFilter = '999';
ok(ft.apply('users', t1.id).statusFilter === '1', 'apply 返回深拷贝（不污染存储）');

// 空值归一化：保存时不落 undefined/null
const t3 = ft.save('users', '含空值', { keyword: null, statusFilter: '', page: 2 });
ok(Object.keys(t3.filters).length === 1 && t3.filters.page === 2, 'save 归一化去除空值，仅保留 page');

// 同名覆盖：保留 id，更新条件
const t1b = ft.save('users', '已封禁用户', { keyword: 'spam2', statusFilter: '1' });
ok(t1b.id === t1.id, '同名保存视为覆盖（保留原 id）');
ok(ft.apply('users', t1.id).keyword === 'spam2', '覆盖后条件更新');

// 切换默认：原默认被取消
ft.setDefault('users', t1.id, true);
ok(ft.getDefault('users').id === t1.id, 'setDefault 切换默认');
ok(ft.list('users').filter((t) => t.isDefault).length === 1, '同一页仅一个默认');

// 删除
ok(ft.remove('users', t1.id) === true, 'remove 返回 true');
ok(!ft.list('users').some((t) => t.id === t1.id), 'remove 后列表不含该模板');
ok(ft.remove('users', 'nope') === false, '删除不存在的 id 返回 false');

// 导出 / 导入 往返
ft._clearAll();
ft.save('orders', '退款单', { keyword: 'R', statusFilter: 'refunding' });
const exported = ft.exportAll();
ft._clearAll();
ok(ft.list('orders').length === 0, '清空后可导入还原');
const n = ft.importAll(exported, { merge: true });
ok(n === 1, 'importAll 导入 1 个模板');
ok(ft.apply('orders', ft.list('orders')[0].id).statusFilter === 'refunding', '导入后条件完整');

// 合并导入：不同 pageKey 互不覆盖
ft.importAll(JSON.stringify({ users: [{ name: 'u1', filters: { keyword: 'x' } }] }), { merge: true });
ok(ft.list('orders').length === 1 && ft.list('users').length >= 1, 'merge 模式跨 pageKey 共存');

// 错误：空名
let threw = false;
try { ft.save('users', '   ', { keyword: 'x' }); } catch (e) { threw = true; }
ok(threw, '空模板名抛错');

// 错误：非法 JSON
threw = false;
try { ft.importAll('{bad json}'); } catch (e) { threw = true; }
ok(threw, '非法 JSON 抛错');

console.log('\n筛选模板自测：' + passed + ' 通过 / ' + failed + ' 失败');
process.exit(failed === 0 ? 0 : 1);
