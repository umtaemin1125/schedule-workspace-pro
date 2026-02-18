import { defineConfig } from '@playwright/test'

export default defineConfig({
  use: {
    baseURL: 'http://localhost:8081'
  },
  webServer: {
    command: 'npm run dev -- --host --port 8081',
    port: 8081,
    reuseExistingServer: true
  }
})
