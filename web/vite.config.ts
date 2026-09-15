import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],

  define: {
    global: 'globalThis'
  },

  server: {
    port: 5173
  }
})