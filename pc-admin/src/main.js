// pc-admin/src/main.js —— Vite 入口：装配 Vue + Element Plus + ECharts + 路由
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import * as echarts from 'echarts';
import App from './App.vue';
import router from './router';
import './styles/global.css';

// 视图内通过 window.echarts 引用图表库（保留免构建期「无 echarts 降级」逻辑，npm 形态下始终可用）。
window.echarts = echarts;

const app = createApp(App);
app.use(ElementPlus);
app.use(router);
app.mount('#app');
