import { defineConfig } from '@playwright/test'

/**
 * AIOps Alert · 端到端 UI 自动化测试
 *
 * 假设前端 (vite dev) 在 http://localhost:5173，后端在 http://localhost:8090
 * 这两个进程由用户/上一轮启动，本测试不负责。
 */
export default defineConfig({
  testDir: './tests',
  timeout: 180_000, // 单测试最多 3 分钟（涵盖 LLM 慢调用）
  expect: { timeout: 15_000 },
  fullyParallel: false,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 60_000,
    locale: 'zh-CN',
    viewport: { width: 1440, height: 900 }
  },
  projects: [
    {
      name: 'chromium-dark',
      use: {
        browserName: 'chromium',
        colorScheme: 'dark',
        viewport: { width: 1440, height: 900 }
      }
    },
    {
      name: 'chromium-light',
      use: {
        browserName: 'chromium',
        colorScheme: 'light',
        viewport: { width: 1440, height: 900 }
      }
    }
  ],
  outputDir: 'test-results'
})
