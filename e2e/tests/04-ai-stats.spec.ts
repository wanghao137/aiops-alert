import { test, expect } from '@playwright/test'

test.describe('AI 调用统计页', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/settings?tab=ai-stats', { waitUntil: 'domcontentloaded' })
  })

  test('系统设置内展示 AI 调用统计 + KPI + 双图表 + 慢调用 + 流水', async ({ page }) => {
    await expect(page.locator('.settings-tab.active')).toContainText('AI 调用统计')
    await expect(page.locator('.ai-stats-v .hero .eyebrow').first()).toContainText('AI 调用统计')
    await expect(page.locator('.ai-stats-v .hero-num').first()).toBeVisible()

    await expect(page.locator('.ai-stats-v .kpi-card')).toHaveCount(3)

    await expect(page.locator('.chart-row .chart')).toHaveCount(2)

    await expect(page.locator('.chart-row .panel-block').first()).toContainText('今日场景分布')
    await expect(page.locator('.chart-row .panel-block').nth(1)).toContainText('近 7 天调用趋势')

    const slowTable = page.locator('.slow-table')
    await expect(slowTable).toBeVisible()

    const logTable = page.locator('.log-table')
    await expect(logTable).toBeVisible()
  })

  test('慢调用展开行 → 显示 prompt+response payload', async ({ page }) => {
    const firstRow = page.locator('.slow-table tbody tr.data-row').first()
    await expect(firstRow).toBeVisible({ timeout: 15_000 })

    const linkBtn = firstRow.locator('.link-btn')
    await expect(linkBtn).toBeVisible()
    await linkBtn.click()

    const expandRow = page.locator('.slow-table .expand-row').first()
    await expect(expandRow).toBeVisible({ timeout: 10_000 })

    const heads = expandRow.locator('.payload-head')
    expect(await heads.count()).toBeGreaterThanOrEqual(2)
    await expect(heads.first()).toContainText(/REQUEST|RESPONSE/)

    const pres = expandRow.locator('pre')
    expect(await pres.count()).toBeGreaterThanOrEqual(2)
  })

  test('点流水行 → 详情抽屉显示 4 字段', async ({ page }) => {
    const firstLog = page.locator('.log-table tbody tr.data-row').first()
    await expect(firstLog).toBeVisible({ timeout: 15_000 })
    await firstLog.click()

    const drawer = page.getByRole('dialog')
    await expect(drawer).toBeVisible({ timeout: 10_000 })

    const metaCells = page.locator('.drawer-meta .meta-cell')
    expect(await metaCells.count()).toBeGreaterThanOrEqual(6)

    const sections = page.locator('.payload-section')
    expect(await sections.count()).toBeGreaterThanOrEqual(2)
  })

  test('scene 筛选纯净度', async ({ page }) => {
    await expect(page.locator('.log-table tbody tr.data-row').first()).toBeVisible({ timeout: 15_000 })

    await page.locator('.f-select').first().click()
    const opt = page.locator('.el-select-dropdown__item', { hasText: 'NL2Rule 建规则' })
    await expect(opt).toBeVisible({ timeout: 3_000 })
    await opt.click()

    await page.waitForTimeout(1500)
    const tags = page.locator('.log-table tbody .scene-tag')
    const count = await tags.count()
    if (count > 0) {
      for (let i = 0; i < count; i++) {
        const txt = (await tags.nth(i).textContent())?.trim()
        expect(txt).toContain('NL2Rule')
      }
    }
  })
})
