// pc-admin/src/cdn-check.js —— CDN 资源加载兜底（RK-6 / R-23 生产化）
//
// 检测关键 CDN 全局变量是否就绪；任一缺失即展示友好面板，避免白屏与静默误用。
// 设计为「经典脚本（非 ESM）」，便于在 CSP 下以 'self' 白名单加载，无需 inline script。
// 注意：面板文案使用的库名来自固定数组（非用户输入），innerHTML 赋值安全，不会引入 XSS 面。
window.addEventListener('load', function () {
  setTimeout(function () {
    var required = [
      { key: 'Vue', label: 'Vue' },
      { key: 'ElementPlus', label: 'Element Plus' },
      { key: 'axios', label: 'axios' },
      { key: 'echarts', label: 'ECharts' }
    ];
    var missing = required.filter(function (r) { return !window[r.key]; }).map(function (r) { return r.label; });
    if (missing.length === 0) return; // 全部就绪，正常进入应用
    var app = document.getElementById('app');
    if (!app) return;
    app.innerHTML = '<div style="max-width:600px;margin:120px auto;padding:32px;background:#fff;'
      + 'border-radius:16px;box-shadow:0 10px 40px rgba(0,0,0,.1);text-align:center;color:#1A1A1A;">'
      + '<h2 style="color:#FF3939;">依赖资源加载失败</h2>'
      + '<p style="color:#666;line-height:1.7;">运营后台依赖以下 CDN 资源，当前环境可能无法访问 jsDelivr 外网：</p>'
      + '<p style="color:#FF3939;font-weight:600;">缺失：' + missing.join('、') + '</p>'
      + '<p style="color:#9A9A9A;font-size:13px;">建议：① 检查网络后刷新；② 生产部署请改用本地打包构建'
      + '并引入 SRI / 离线兜底，避免依赖公共 CDN。</p>'
      + '</div>';
  }, 1500);
});
