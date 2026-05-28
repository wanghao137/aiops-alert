import { test, expect } from '@playwright/test'

test.describe('导航 / 路由 / 主题切换', () => {
  test('侧栏菜单 7 项 + AI 调用统计归入系统设置', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })

    const navItems = page.locator('.sidebar .nav-item')
    const count = await navItems.count()
    expect(count).toBe(7)

    const titles: string[] = []
    for (let i = 0; i < count; i++) {
      const t = (await navItems.nth(i).textContent())?.trim()
      if (t) titles.push(t)
    }

    expect(titles.some((t) => t.includes('总览大屏'))).toBeTruthy()
    expect(titles.some((t) => t.includes('告警事件'))).toBeTruthy()
    expect(titles.some((t) => t.includes('故障组'))).toBeTruthy()
    expect(titles.some((t) => t.includes('告警规则'))).toBeTruthy()
    expect(titles.some((t) => t.includes('监控对象'))).toBeTruthy()
    expect(titles.some((t) => t.includes('通知渠道'))).toBeTruthy()
    expect(titles.some((t) => t.includes('系统设置'))).toBeTruthy()
    expect(titles.some((t) => t.includes('AI 调用统计'))).toBeFalsy()

    expect(titles.some((t) => t.includes('页面未找到'))).toBeFalsy()
    expect(titles.some((t) => /^\s*Incident\s*$/.test(t))).toBeFalsy()
  })

  test('切到所有主导航 view → 每页加载中文眉标', async ({ page }) => {
    const routes = [
      { path: '/dashboard',  hero: '态势总览' },
      { path: '/events',     hero: '事件中心' },
      { path: '/incidents',  hero: '故障组' },
      { path: '/rules',      hero: '告警规则' },
      { path: '/objects',    hero: '监控对象' },
      { path: '/channels',   hero: '通知渠道' },
      { path: '/settings',   hero: '系统设置' }
    ]

    for (const r of routes) {
      await page.goto(r.path, { waitUntil: 'domcontentloaded' })
      await expect(page).toHaveURL(new RegExp(r.path + '$'))
      await page.waitForSelector('.hero, .error-page, main', { timeout: 10_000 }).catch(() => { /* ok */ })

      if (r.hero) {
        await expect(page.locator('.hero .eyebrow').first()).toContainText(r.hero, { timeout: 8_000 })
      }
    }
  })

  test('兼容入口 /ai-stats 跳转到系统设置的统计页签', async ({ page }) => {
    await page.goto('/ai-stats', { waitUntil: 'domcontentloaded' })

    await expect(page).toHaveURL(/\/settings\?tab=ai-stats$/)
    await expect(page.locator('.settings-tabs')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.settings-tab.active')).toContainText('AI 调用统计')
    await expect(page.locator('.ai-stats-v')).toBeVisible({ timeout: 10_000 })
  })

  test('404 catch-all 页面', async ({ page }) => {
    await page.goto('/no-such-route-xyz', { waitUntil: 'domcontentloaded' })

    const eyepage = page.locator('.error-page.variant-404')
    await expect(eyepage).toBeVisible({ timeout: 5_000 })

    await expect(eyepage.locator('.big-num')).toContainText('404')

    await expect(page.locator('.sidebar')).toBeVisible()
  })

  test('Cmd+K 命令面板：打开 + ESC 关闭', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    await page.keyboard.press('Control+K')

    const palette = page.locator('.palette')
    await expect(palette).toBeVisible({ timeout: 3_000 })

    await expect(palette.locator('.term-dot.r')).toBeVisible()
    await expect(palette.locator('.term-name')).toContainText('aiops:command')

    await expect(palette.locator('.prompt-mark')).toContainText('›')

    const sugs = palette.locator('.sug-item')
    expect(await sugs.count()).toBeGreaterThanOrEqual(5)

    await palette.locator('.palette-foot').click()
    await page.keyboard.press('Escape')
    await expect(palette).not.toBeVisible({ timeout: 3_000 })
  })

  test('Cmd+K 命令面板：提交后实时显示思考流', async ({ page }) => {
    await page.route('**/api/ai/command', async (route) => {
      await page.waitForTimeout(4200)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: {
            intent: 'count_events',
            answer: '当前有 2 个对象处于告警状态。',
            total: 2,
            pending: 2,
            critical: 1,
            modelName: 'mock-model',
            durationMs: 2200
          }
        })
      })
    })

    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    await page.keyboard.press('Control+K')

    const palette = page.locator('.palette')
    await expect(palette).toBeVisible({ timeout: 3_000 })
    await palette.locator('.head-input').fill('现在哪些对象在告警？')
    await page.keyboard.press('Enter')

    const stream = palette.locator('.live-thinking-stream')
    await expect(stream).toBeVisible({ timeout: 1_000 })
    await expect(stream).toContainText('实时思考流')

    await expect(palette.locator('.ans-text')).toContainText('当前有 2 个对象处于告警状态', { timeout: 15_000 })
  })

  test('默认主题：无本地偏好时跟随系统浅色', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' })
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })
    await page.evaluate(() => localStorage.removeItem('aiops:theme-mode'))
    await page.reload({ waitUntil: 'domcontentloaded' })

    const html = page.locator('html')
    await expect(html).toHaveAttribute('data-theme', 'light', { timeout: 3_000 })
    await expect(page.locator('body')).not.toHaveClass(/dark/, { timeout: 3_000 })

    const switcher = page.locator('.theme-switcher').first()
    await expect(switcher.locator('button[title="跟随系统"]')).toHaveClass(/active/)
  })

  test('主题切换：system → light', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })

    const html = page.locator('html')
    const initial = await html.getAttribute('data-theme')
    expect(['dark', 'light']).toContain(initial)

    const switcher = page.locator('.theme-switcher, [data-testid="theme-switcher"]').first()
    await expect(switcher).toBeVisible({ timeout: 5_000 })

    const lightBtn = switcher.locator('button[title="浅色"]').first()
    if (await lightBtn.count() > 0) {
      await lightBtn.click()
      await expect(html).toHaveAttribute('data-theme', 'light', { timeout: 3_000 })

      await expect(page.locator('body')).not.toHaveClass(/dark/, { timeout: 3_000 })
    }
  })
})
