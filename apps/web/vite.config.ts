import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';

export default defineConfig({
  plugins: [
    react({
      babel: {
        plugins: [['@babel/plugin-proposal-decorators', { legacy: true }]],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': resolve(__dirname, './src'),
      '@algoverse/shared-types': resolve(__dirname, '../../libs/shared-types/src/index.ts'),
    },
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/problems': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/v1/submissions': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        ws: true,
      },
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/execution': {
        target: 'http://localhost:8087',
        changeOrigin: true,
      },
      '/api/v1/collab': {
        target: 'http://localhost:8088',
        changeOrigin: true,
      },
      '/api/v1/gamification': {
        target: 'http://localhost:8084',
        changeOrigin: true,
      },
      '/api/v1/analytics': {
        target: 'http://localhost:8085',
        changeOrigin: true,
      },
      '/api/v1/sysdesign': {
        target: 'http://localhost:8086',
        changeOrigin: true,
      },
      '/api/v1/ai': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
      '/ws-collab': {
        target: 'http://localhost:8088',
        changeOrigin: true,
        ws: true,
      },
    },
  },
  build: {
    target: 'ES2022',
    outDir: '../../dist/apps/web',
    reportCompressedSize: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'react-vendor': ['react', 'react-dom', 'react-router-dom'],
          'query-vendor': ['@tanstack/react-query'],
          'motion-vendor': ['framer-motion'],
          'editor-vendor': ['@monaco-editor/react', 'monaco-editor'],
          'markdown-vendor': ['react-markdown', 'rehype-katex', 'remark-math', 'katex'],
        },
      },
    },
  },
  optimizeDeps: {
    include: ['react', 'react-dom', 'framer-motion', '@monaco-editor/react'],
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
    },
  },
});
