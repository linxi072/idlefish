#!/usr/bin/env bash
# 启动 PC 运营后台（Vite 开发服务器，Vue3 + Element Plus 单文件组件）
# 依赖：先执行 npm install 安装 vue/element-plus/axios/echarts/vite 及 @vitejs/plugin-vue
# 用法：./start.sh [端口]   默认 8787（与 vite.config.js 中 server.port 一致）
cd "$(dirname "$0")" || exit 1
PORT="${1:-8787}"
echo "闲置集 PC 运营后台已启动： http://localhost:${PORT}   （Ctrl+C 停止）"
exec npx vite --port "${PORT}"
