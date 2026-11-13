import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // host: true 让 Vite 同时监听 0.0.0.0，手机连同一个 Wi-Fi 就能用
    // http://<电脑局域网IP>:5173 访问（只影响本机开发，不影响构建产物）
    host: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 头像等上传文件由后端直接提供，开发时也要转发过去
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
