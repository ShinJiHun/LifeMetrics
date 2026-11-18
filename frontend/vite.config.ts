import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': '/src',
    },
  },
  server: {
    // 문서 뷰어가 저장소 루트의 docs/*.md 를 읽는다
    fs: { allow: ['..'] },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // 로컬 백엔드
        changeOrigin: true,
      },
    },
  },
})