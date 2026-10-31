// pc-admin/src/main.js —— Vite 入口：装配 Vue + Element Plus + ECharts + 路由
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
// 暗色主题变量（ITER-U04）：配合 html.dark 生效，组件级变量由 Element Plus 提供
import 'element-plus/theme-chalk/dark/css-vars.css';
import * as ElIcons from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import App from './App.vue';
import router from './router';
import PageHeader from './components/PageHeader.vue';
import TreeFlatTable from './components/TreeFlatTable.vue';
import FilterBar from './components/FilterBar.vue';
import PageBar from './components/PageBar.vue';
import EmptyState from './components/EmptyState.vue';
import StatusTag from './components/StatusTag.vue';
import BatchActionBar from './components/BatchActionBar.vue';
import BatchResultDialog from './components/BatchResultDialog.vue';
import FilterTemplates from './components/FilterTemplates.vue';
import './styles/global.css';
import { applyTheme, resolveInitialTheme } from './utils/theme';

// 视图内通过 window.echarts 引用图表库（保留免构建期「无 echarts 降级」逻辑，npm 形态下始终可用）。
window.echarts = echarts;

// 挂载前应用主题，避免暗色用户在首帧看到亮色闪烁（不写回偏好）
applyTheme(resolveInitialTheme(), false);

const app = createApp(App);
app.use(ElementPlus);
app.use(router);
// 全局注册 Element Plus 图标（菜单/顶栏/表单 prefix-icon 等直接用 <组件名/>，无需逐个 import）
for (const [name, comp] of Object.entries(ElIcons)) {
  app.component(name, comp);
}
// 全局统一页面头部（标题 + 描述 + 面包屑 + 右侧操作插槽）
app.component('PageHeader', PageHeader);
// 全局统一「树状结构扁平表格」：层级缩进 + 层级列 + 全部字段保留，列定义/样式/交互统一协调
app.component('TreeFlatTable', TreeFlatTable);
// ITER-U04 统一设计系统：跨页复用组件（筛选栏 / 分页条 / 空态 / 状态标签）
app.component('FilterBar', FilterBar);
app.component('PageBar', PageBar);
app.component('EmptyState', EmptyState);
app.component('StatusTag', StatusTag);
// ITER-U03 批量操作：批量栏 + 结果弹窗
app.component('BatchActionBar', BatchActionBar);
app.component('BatchResultDialog', BatchResultDialog);
// ITER-U03 REQ-06 筛选模板：本地持久化的命名模板（保存/应用/默认/删除/导入导出）
app.component('FilterTemplates', FilterTemplates);
// 渲染期错误（如视图方法缺失/模板表达式抛错）直达页面顶部诊断条，避免静默白屏
app.config.errorHandler = (err, vm, info) => {
  const msg = (err && (err.stack || err.message)) || String(err);
  if (window.__showErr) window.__showErr('[vue] ' + msg + '\n  info: ' + info);
  else console.error(err);
};
app.mount('#app');
