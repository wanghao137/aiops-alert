import { test, expect } from '@playwright/test'

test.describe('导航 / 路由 / 主题切换', () => {
  test('侧栏菜单 8 项 + 中文', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })

    const navItems = page.locator('.sidebar .nav-item')
    const count = await navItems.count()
    expect(count).toBeGreaterThanOrEqual(8)

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
    expect(titles.some((t) => t.includes('AI 调用统计'))).toBeTruthy()

    expect(titles.some((t) => t.includes('页面未找到'))).toBeFalsy()
    expect(titles.some((t) => /^\s*Incident\s*$/.test(t))).toBeFalsy()
  })

  test('切到所有 8 个 view → 每页加载', async ({ page }) => {
    const routes = [
      { path: '/dashboard',  hero: 'SITUATIONAL OVERVIEW' },
      { path: '/events',     hero: 'EVENT CENTER' },
      { path: '/incidents',  hero: 'INCIDENT GROUPS' },
      { path: '/rules',      hero: 'ALERT RULES' },
      { path: '/objects',    hero: '' },
      { path: '/channels',   hero: '' },
      { path: '/settings',   hero: '' },
      { path: '/ai-stats',   hero: 'AI ENGINEERING' }
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

  test('主题切换：dark → light', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'domcontentloaded' })

    const html = page.locator('html')
    const initial = await html.getAttribute('data-theme')
    expect(['dark', 'light']).toContain(initial)

    const switcher = page.locator('.theme-switcher, [data-testid="theme-switcher"]').first()
    await expect(switcher).toBeVisible({ timeout: 5_000 })

    const lightBtn = switcher.locator('button', { hasText: /浅色|light|Sun/i }).first()
    if (await lightBtn.count() > 0) {
      await lightBtn.click()
      await expect(html).toHaveAttribute('data-theme', 'light', { timeout: 3_000 })

      await expect(page.locator('body')).not.toHaveClass(/dark/, { timeout: 3_000 })
    }
  })
})
