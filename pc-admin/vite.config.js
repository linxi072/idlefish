// pc-admin/vite.config.js —— Vite 构建配置（Vue3 单文件组件 + @ 别名 + 后端代理）
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 8787,
    strictPort: false,
    // R-23 生产化 · 联调期将 /api 代理到本地 Spring Boot 后端（默认 8080），避免跨域。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  }
});
