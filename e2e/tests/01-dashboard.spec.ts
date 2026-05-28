import { test, expect } from '@playwright/test'

test.describe('Dashboard 总览大屏', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
  })

  test('Hero + KPI + 7 日趋势加载完成', async ({ page }) => {
    await expect(page.locator('.hero .eyebrow').first()).toContainText('态势总览')

    const heroNum = page.locator('.hero-num').first()
    await expect(heroNum).toBeVisible()
    const heroText = (await heroNum.textContent())?.trim() ?? ''
    expect(heroText).toMatch(/^\d+$/)

    await expect(page.locator('.kpi-card')).toHaveCount(4)

    const trendCanvas = page.locator('.panel-block.trend canvas').first()
    await expect(trendCanvas).toBeVisible({ timeout: 20_000 })

    await expect(page.locator('.hero .dot-anim').first()).toBeVisible()
  })

  test('每日 AI 简报卡显示', async ({ page }) => {
    const card = page.locator('.brief-card')
    await expect(card).toBeVisible({ timeout: 30_000 })

    await expect(card.locator('.brand')).toContainText('AI 简报')

    const narrative = card.locator('.narrative')
    await expect(narrative).toBeVisible()
    const text = (await narrative.textContent())?.trim() ?? ''
    expect(text.length).toBeGreaterThan(20)

    await expect(card.locator('.snap-cell')).toHaveCount(6)

    const anyStatus = card.locator('.ok-pill, .warn-pill, .fail-pill')
    await expect(anyStatus.first()).toBeVisible()

    await expect(card.locator('.refresh')).toBeVisible()
  })

  test('点击刷新简报 → 进入 thinking 状态', async ({ page }) => {
    const card = page.locator('.brief-card')
    await expect(card).toBeVisible()
    await expect(page.locator('.dashboard-v .el-loading-mask')).toBeHidden({ timeout: 30_000 })
    await card.locator('.refresh').click()

    const loading = card.locator('.live-thinking-stream')
    await expect(loading).toBeVisible({ timeout: 3_000 })
    await expect(loading).toContainText('实时思考流')

    await expect(card.locator('.refresh')).toContainText(/生成中|刷新简报/)
  })

  test('NOC 大屏模式：进入 → ESC 退出', async ({ page }) => {
    const btn = page.locator('.noc-btn')
    await expect(btn).toBeVisible()
    await expect(btn).toContainText('大屏模式')

    await btn.click()

    await expect(page.locator('html')).toHaveAttribute('data-noc', '1', { timeout: 3_000 })
    await expect(page.locator('.sidebar')).toBeHidden()
    await expect(btn).toContainText('退出大屏')

    await page.keyboard.press('Escape')

    await expect(page.locator('html')).not.toHaveAttribute('data-noc', '1', { timeout: 3_000 })
    await expect(page.locator('.sidebar')).toBeVisible()
    await expect(btn).toContainText('大屏模式')
  })
})
