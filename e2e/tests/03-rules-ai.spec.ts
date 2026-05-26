import { test, expect } from '@playwright/test'

test.describe('Rules 视图 + AI 终端横幅', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/rules', { waitUntil: 'domcontentloaded' })
  })

  test('Hero + AI 终端横幅 + 规则卡', async ({ page }) => {
    await expect(page.locator('.hero .eyebrow').first()).toContainText('ALERT RULES')

    const banner = page.locator('.ai-banner')
    await expect(banner).toBeVisible()
    await expect(banner.locator('.ai-eyebrow')).toContainText(/AI BUILDER/)
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
})
