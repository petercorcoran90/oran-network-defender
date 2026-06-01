import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// defineConfig comes from 'vitest/config' so we can add the `test` block; `vite build`
// ignores it and works exactly as before.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{js,jsx}'],
      // Exclude the bootstrap, the WebGL map (not unit-testable in jsdom) and the tests themselves.
      exclude: ['src/main.jsx', 'src/NetworkMap3D.jsx', 'src/**/*.test.{js,jsx}'],
    },
  },
})
