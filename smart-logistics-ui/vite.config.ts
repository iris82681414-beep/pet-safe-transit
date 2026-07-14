import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'
  const apiProxy = {
    '/api': {
      target: apiProxyTarget,
      changeOrigin: true,
      ws: true,
    },
  }

  return {
    base: './',
    plugins: [
      vue(),
      Components({
        resolvers: [ElementPlusResolver({ importStyle: 'css' })],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: apiProxy,
    },
    preview: {
      port: 5173,
      strictPort: true,
      proxy: apiProxy,
    },
    build: {
      chunkSizeWarningLimit: 1100,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules/element-plus') || id.includes('node_modules/@element-plus')) return 'element-plus'
            if (id.includes('node_modules/leaflet')) return 'leaflet'
            if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) return 'vue'
          },
        },
      },
    },
  }
})
