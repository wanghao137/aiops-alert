import { test, expect, request } from '@playwright/test'

const BACKEND = 'http://localhost:8090'

test.describe('Events 视图 + AI 摘要', () => {
  test('列表页基础结构', async ({ page }) => {
    await page.goto('/events', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.hero .eyebrow').first()).toContainText('EVENT CENTER')

    await expect(page.locator('.side')).toBeVisible()
    await expect(page.locator('.main')).toBeVisible()

    const tabs = page.locator('.tabs .tab')
    await expect(tabs.first()).toBeVisible()
    expect(await tabs.count()).toBeGreaterThanOrEqual(5)

    await expect(page.locator('.event-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('触发新告警 → 详情抽屉展示 AI 摘要状态', async ({ page }) => {
    const api = await request.newContext({ baseURL: BACKEND })
    const rulesResp = await api.get('/api/alert-rules')
    const rules = (await rulesResp.json()).data
    const cpuRule = rules.find((r: any) => r.ruleCode === 'RULE-SRV-CPU') ?? rules[0]
    const ruleDetailResp = await api.get(`/api/alert-rules/${cpuRule.id}`)
    const obj = (await ruleDetailResp.json()).data.objects[0]

    const triggerResp = await api.post('/api/alert-events/test', {
      data: {
        ruleId: cpuRule.id,
        objectId: obj.id,
        currentValue: 'cpu_usage=99% (e2e)',
        eventReason: 'playwright e2e trigger'
      }
    })
    const newEvent = (await triggerResp.json()).data
    expect(newEvent.id).toBeGreaterThan(0)
    await api.dispose()

    await page.goto('/events', { waitUntil: 'domcontentloaded' })

    const targetCard = page.locator('.event-card', { hasText: String(newEvent.eventNo) })
    await expect(targetCard).toBeVisible({ timeout: 10_000 })
    await targetCard.click()

    const detail = page.locator('.detail')
    await expect(detail).toBeVisible({ timeout: 5_000 })

    const summaryCard = detail.locator('.ai-summary-card')
    await expect(summaryCard).toBeVisible()
    const cls = (await summaryCard.getAttribute('class')) ?? ''
    expect(cls).toMatch(/pending|loading|success/)

    if (/pending|loading/.test(cls)) {
      const thinking = summaryCard.locator('.thinking-line')
      await expect(thinking).toBeVisible({ timeout: 3_000 })
      await expect(thinking.locator('.prompt-mark')).toContainText('▸')
      await expect(thinking.locator('.thinking-text')).toBeVisible()
      await expect(thinking.locator('.caret')).toBeVisible()

      const initialText = (await thinking.locator('.thinking-text').textContent())?.trim()
      await page.waitForTimeout(1900)
      const laterCls = (await summaryCard.getAttribute('class')) ?? ''
      if (/success/.test(laterCls)) {
        await expect(summaryCard.locator('.ready-pill')).toContainText('已生成')
        await expect(summaryCard.locator('.block.what')).toBeVisible()
        await expect(summaryCard.locator('.block.actions')).toBeVisible()
      } else {
        await expect(thinking.locator('.thinking-text')).toBeVisible()
        const laterText = (await thinking.locator('.thinking-text').textContent())?.trim()
        expect(initialText).not.toEqual(laterText)
      }
    } else {
      await expect(summaryCard.locator('.ready-pill')).toContainText('已生成')
      await expect(summaryCard.locator('.block.what')).toBeVisible()
      await expect(summaryCard.locator('.block.actions')).toBeVisible()
    }
  })
})
