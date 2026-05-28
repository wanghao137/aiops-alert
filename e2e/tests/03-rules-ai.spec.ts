import { test, expect } from '@playwright/test'

test.describe('Rules 视图 + AI 终端横幅', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/rules', { waitUntil: 'domcontentloaded' })
  })

  test('Hero + AI 终端横幅 + 规则卡', async ({ page }) => {
    await expect(page.locator('.hero .eyebrow').first()).toContainText('告警规则 / RULES')

    const banner = page.locator('.ai-banner')
    await expect(banner).toBeVisible()
    await expect(banner.locator('.ai-eyebrow')).toContainText(/AI 建规则/)
    await expect(banner.locator('.ai-title')).toContainText('一句话')

    const term = banner.locator('.ai-terminal')
    await expect(term).toBeVisible()
    await expect(term.locator('.prompt-mark')).toContainText('$')
    await expect(term.locator('.caret')).toBeVisible()

    const typewriter = term.locator('.typewriter')
    await expect(typewriter).toBeVisible()
    const txt = (await typewriter.textContent())?.trim() ?? ''
    expect(txt.length).toBeGreaterThan(0)

    await expect(page.locator('.level-bar-row')).toBeVisible()

    await expect(page.locator('.rule-card').first()).toBeVisible({ timeout: 10_000 })
    const ruleCount = await page.locator('.rule-card').count()
    expect(ruleCount).toBeGreaterThanOrEqual(6)
  })

  test('点击 AI 横幅 → 打开 NlRuleDialog 终端外壳', async ({ page }) => {
    await page.locator('.ai-banner').click()

    const dlg = page.locator('.nl-rule-dialog')
    await expect(dlg).toBeVisible({ timeout: 5_000 })

    const shell = dlg.locator('.terminal-shell')
    await expect(shell).toBeVisible()
    await expect(shell.locator('.term-dot.r')).toBeVisible()
    await expect(shell.locator('.term-dot.y')).toBeVisible()
    await expect(shell.locator('.term-dot.g')).toBeVisible()
    await expect(shell.locator('.term-name')).toContainText('aiops:rule-builder')

    await expect(shell.locator('.term-status').first()).toBeVisible()

    const input = shell.locator('.term-input')
    await expect(input).toBeVisible()
    await expect(input).toBeEditable()

    const chips = dlg.locator('.example-chip')
    expect(await chips.count()).toBeGreaterThanOrEqual(4)

    await page.keyboard.press('Escape')
    await expect(dlg).not.toBeVisible({ timeout: 3_000 })
  })

  test('AI 建规则提交后实时显示思考流', async ({ page }) => {
    await page.route('**/api/ai/rules/draft', async (route) => {
      await page.waitForTimeout(4200)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: {
            understanding: '需要为 MySQL 主从延迟创建紧急告警规则。',
            modelName: 'mock-model',
            durationMs: 2200,
            draft: {
              ruleName: 'MySQL 主从延迟告警',
              objectType: 'DATABASE',
              objectTypeName: '数据库',
              alertLevel: 'CRITICAL',
              conditionLogic: 'AND',
              triggerTimes: 3,
              timeWindowMinutes: 5,
              conditions: [{
                metricCode: 'db_replica_lag',
                metricName: '主从延迟',
                compareOp: 'GT',
                thresholdValue: '300',
                thresholdUnit: '秒'
              }],
              objectIds: [],
              channelBindings: []
            },
            warnings: []
          }
        })
      })
    })

    await page.locator('.ai-banner').click()
    const dlg = page.locator('.nl-rule-dialog')
    await expect(dlg).toBeVisible({ timeout: 5_000 })
    await dlg.locator('.term-input').fill('MySQL 主从延迟超过 5 分钟连续 3 次，紧急告警')
    await dlg.getByRole('button', { name: /生成规则|AI 思考中/ }).click()

    const stream = dlg.locator('.live-thinking-stream')
    await expect(stream).toBeVisible({ timeout: 1_000 })
    await expect(stream).toContainText('实时思考流')
    await expect(stream.locator('.stream-line').first()).toBeVisible()
    const first = (await stream.textContent()) || ''
    await page.waitForTimeout(1200)
    const second = (await stream.textContent()) || ''
    expect(second.length).toBeGreaterThan(first.length)

    await expect(dlg.locator('.result-card')).toBeVisible({ timeout: 5_000 })
  })

  test('采用 AI 规则草稿后只展示规则编辑框且顶部不被遮挡', async ({ page }) => {
    await page.route('**/api/ai/rules/availability', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, message: 'ok', data: true })
      })
    })

    await page.route('**/api/ai/rules/draft', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: {
            understanding: '需要为 MySQL 主从延迟创建紧急告警规则。',
            modelName: 'mock-model',
            durationMs: 320,
            draft: {
              ruleName: '生产 MySQL 主从延迟告警',
              objectType: 'DATABASE',
              objectTypeName: '数据库',
              alertLevel: 'CRITICAL',
              conditionLogic: 'AND',
              triggerTimes: 3,
              timeWindowMinutes: 5,
              minAlertIntervalMinutes: 30,
              recoverNotify: true,
              repeatNotify: false,
              status: 'ENABLED',
              priority: 10,
              description: 'AI 生成的主从延迟告警草稿。',
              conditions: [{
                metricCode: 'replication_lag',
                metricName: '主从延迟',
                compareOp: 'GT',
                thresholdValue: '300',
                thresholdUnit: '秒'
              }],
              objectIds: [],
              channelBindings: []
            },
            warnings: []
          }
        })
      })
    })

    await page.locator('.ai-banner').click()
    const aiDialog = page.locator('.nl-rule-dialog')
    await expect(aiDialog).toBeVisible({ timeout: 5_000 })
    await aiDialog.locator('.term-input').fill('生产 MySQL 主从延迟超过 5 分钟连续 3 次紧急告警')
    await aiDialog.getByRole('button', { name: /生成规则|AI 思考中/ }).click()
    await expect(aiDialog.locator('.result-card')).toBeVisible({ timeout: 5_000 })
    await aiDialog.getByRole('button', { name: /采用并填入表单/ }).click()

    await page.waitForTimeout(120)
    await expect(page.locator('.nl-rule-dialog')).not.toBeVisible()

    const editDialog = page.locator('.rule-dialog')
    await expect(editDialog).toBeVisible({ timeout: 5_000 })
    await expect(editDialog.locator('.el-dialog__title')).toContainText('新增告警规则')
    await expect(editDialog.locator('.condition-row')).toHaveCount(1)

    expect(await page.locator('.el-dialog:visible').count()).toBe(1)
    const overlayParentTag = await page.locator('.el-overlay:visible').last()
      .evaluate((el) => el.parentElement?.tagName)
    expect(overlayParentTag).toBe('BODY')

    const box = await editDialog.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.y).toBeGreaterThanOrEqual(12)
    expect(box!.y).toBeLessThanOrEqual(56)

    const header = await editDialog.locator('.el-dialog__header').boundingBox()
    const body = await editDialog.locator('.el-dialog__body').boundingBox()
    expect(header).not.toBeNull()
    expect(body).not.toBeNull()
    expect(body!.y).toBeGreaterThan(header!.y + header!.height - 1)
  })
})
