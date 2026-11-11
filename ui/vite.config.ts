import react from '@vitejs/plugin-react';
import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';

// В разработке интерфейс ходит в /api/v1 и /ws того же адреса, а Vite проксирует их в шлюз (BFF).
// На стенде ту же роль выполняет Nginx. Адрес шлюза: UI_API_TARGET, по умолчанию локальный uvicorn.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.UI_API_TARGET || 'http://localhost:8000';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: apiTarget, changeOrigin: true },
        '/ws': { target: apiTarget.replace(/^http/, 'ws'), ws: true },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  };
});
