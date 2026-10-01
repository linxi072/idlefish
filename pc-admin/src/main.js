// pc-admin/src/main.js —— Vite 入口：装配 Vue + Element Plus + ECharts + 路由
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as ElIcons from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import App from './App.vue';
import router from './router';
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
// 渲染期错误（如视图方法缺失/模板表达式抛错）直达页面顶部诊断条，避免静默白屏
app.config.errorHandler = (err, vm, info) => {
  const msg = (err && (err.stack || err.message)) || String(err);
  if (window.__showErr) window.__showErr('[vue] ' + msg + '\n  info: ' + info);
  else console.error(err);
};
app.mount('#app');
