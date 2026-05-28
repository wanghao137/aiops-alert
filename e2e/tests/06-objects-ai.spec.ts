import { test, expect } from '@playwright/test'

test.describe('监控对象 AI 智能配置', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/api/monitor-objects/stats', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: {
            total: 1,
            enabled: 1,
            byType: [
              { objectType: 'SERVER', objectTypeName: '服务器', total: 0, enabled: 0 },
              { objectType: 'DATABASE', objectTypeName: '数据库', total: 1, enabled: 1 },
              { objectType: 'SYNC_JOB', objectTypeName: '数据同步作业', total: 0, enabled: 0 },
              { objectType: 'PROCESS_JOB', objectTypeName: '数据加工作业', total: 0, enabled: 0 }
            ]
          }
        })
      })
    })

    await page.route('**/api/monitor-objects?**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: [{
            id: 10,
            objectCode: 'OBJ-DB-PROD',
            objectName: '生产 MySQL 主库',
            objectType: 'DATABASE',
            objectTypeName: '数据库',
            ownerName: 'DBA',
            ownerPhone: '13800000000',
            tags: 'prod,core,7x24',
            status: 'ENABLED',
            description: '核心交易库',
            extConfig: '{"host":"10.0.0.21","port":3306}'
          }]
        })
      })
    })

    await page.route('**/api/monitor-objects', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.fallback()
        return
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: [{
            id: 10,
            objectCode: 'OBJ-DB-PROD',
            objectName: '生产 MySQL 主库',
            objectType: 'DATABASE',
            objectTypeName: '数据库',
            ownerName: 'DBA',
            ownerPhone: '13800000000',
            tags: 'prod,core,7x24',
            status: 'ENABLED',
            description: '核心交易库',
            extConfig: '{"host":"10.0.0.21","port":3306}'
          }]
        })
      })
    })

    await page.route('**/api/ai/objects/availability', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, message: 'ok', data: true })
      })
    })

    await page.route('**/api/ai/objects/draft', async (route) => {
      await page.waitForTimeout(800)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: {
            understanding: '需要添加一个生产 MySQL 主库监控对象，并补齐连接与责任人信息。',
            modelName: 'mock-object-model',
            durationMs: 800,
            draft: {
              objectName: '生产 MySQL 主库',
              objectType: 'DATABASE',
              objectCode: 'MYSQL-PROD-MAIN',
              ownerName: 'DBA 值班',
              ownerPhone: '13800000000',
              tags: 'prod,核心,7x24',
              status: 'ENABLED',
              description: '核心交易库主库，重点关注连接数、主从延迟和慢查询。',
              extConfig: '{\n  "host" : "10.0.0.21",\n  "port" : 3306,\n  "env" : "prod"\n}'
            },
            warnings: ['对象编码如果已存在，保存时可留空让系统自动生成']
          }
        })
      })
    })
  })

  test('AI 生成监控对象草稿后填入对象表单', async ({ page }) => {
    await page.goto('/objects', { waitUntil: 'domcontentloaded' })

    await page.getByRole('button', { name: /AI 智能配置/ }).click()

    const aiDialog = page.locator('.ai-object-dialog')
    await expect(aiDialog).toBeVisible({ timeout: 5_000 })
    await expect(aiDialog.locator('.term-name')).toContainText('aiops:object-builder')

    await aiDialog.locator('.term-input').fill('添加生产 MySQL 主库，IP 10.0.0.21，DBA 值班负责，核心 7x24')
    await aiDialog.getByRole('button', { name: /生成对象配置|AI 思考中/ }).click()

    await expect(aiDialog.locator('.live-thinking-stream')).toBeVisible({ timeout: 1_000 })
    await expect(aiDialog.locator('.object-result-card')).toContainText('生产 MySQL 主库', { timeout: 5_000 })
    await expect(aiDialog.locator('.object-result-card')).toContainText('DATABASE')

    await aiDialog.getByRole('button', { name: /采用并填入表单/ }).click()

    const formDialog = page.locator('.object-dialog')
    await expect(formDialog).toBeVisible({ timeout: 5_000 })
    await expect(formDialog.locator('.el-dialog__title')).toContainText('新增监控对象')

    const nameInput = formDialog.locator('.el-form-item', { hasText: '对象名称' }).locator('input')
    await expect(nameInput).toHaveValue('生产 MySQL 主库')
    const ownerInput = formDialog.locator('.el-form-item', { hasText: '负责人' }).locator('input')
    await expect(ownerInput).toHaveValue('DBA 值班')
    await expect(formDialog.locator('textarea').nth(0)).toHaveValue(/核心交易库主库/)
    await expect(formDialog.locator('textarea').nth(1)).toHaveValue(/"host"/)
  })
})
