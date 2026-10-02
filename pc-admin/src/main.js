// pc-admin/src/main.js —— Vite 入口：装配 Vue + Element Plus + ECharts + 路由
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as ElIcons from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import App from './App.vue';
import router from './router';
import PageHeader from './components/PageHeader.vue';
import TreeFlatTable from './components/TreeFlatTable.vue';
import './styles/global.css';

// 视图内通过 window.echarts 引用图表库（保留免构建期「无 echarts 降级」逻辑，npm 形态下始终可用）。
window.echarts = echarts;

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
// 渲染期错误（如视图方法缺失/模板表达式抛错）直达页面顶部诊断条，避免静默白屏
app.config.errorHandler = (err, vm, info) => {
  const msg = (err && (err.stack || err.message)) || String(err);
  if (window.__showErr) window.__showErr('[vue] ' + msg + '\n  info: ' + info);
  else console.error(err);
};
app.mount('#app');
