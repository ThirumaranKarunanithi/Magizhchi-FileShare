import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const apiTarget = env.VITE_API_URL || 'http://localhost:8080';

  return {
    plugins: [react()],

    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
        },
        '/ws': {
          target: apiTarget,
          changeOrigin: true,
          secure: false,
          ws: true,
        },
      },
    },

    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1000,
      rollupOptions: {
        output: {
          // Do NOT put React in its own manualChunk — Rollup can initialise the app
          // chunk before the React chunk is ready, causing a TDZ ReferenceError
          // ("Cannot access 'X' before initialization") at runtime in production.
          // Let Vite/Rollup handle React splitting automatically via its built-in
          // vendor-detection heuristics.
          manualChunks: {
            // ONLY split libraries that have NO React peer-dependency.
            // Putting react-hot-toast here caused a TDZ crash because
            // the ui chunk tried to access React before index.js was ready.
            stomp: ['@stomp/stompjs'],
            utils: ['date-fns'],
          },
        },
      },
    },
  };
});
