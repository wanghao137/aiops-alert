import { test, expect, request } from '@playwright/test'

const BACKEND = 'http://localhost:8090'

test.describe('Events 视图 + AI 摘要', () => {
  test('列表页基础结构', async ({ page }) => {
    await page.goto('/events', { waitUntil: 'domcontentloaded' })

    await expect(page.locator('.hero .eyebrow').first()).toContainText('事件中心')

    await expect(page.locator('.side')).toBeVisible()
    await expect(page.locator('.main')).toBeVisible()

    const tabs = page.locator('.tabs .tab')
    await expect(tabs.first()).toBeVisible()
    expect(await tabs.count()).toBeGreaterThanOrEqual(5)

    await expect(page.locator('.event-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('列表滚动到底部后点击事件，详情仍固定在可视区域', async ({ page }) => {
    const events = Array.from({ length: 28 }, (_, index) => {
      const id = index + 1
      return {
        id,
        eventNo: `ALERT-TEST-${String(id).padStart(4, '0')}`,
        eventTitle: `[严重] 测试对象-${id} · CPU 高使用率触发`,
        objectId: id,
        objectName: `prod-web-${String(id).padStart(2, '0')}`,
        objectType: 'SERVER',
        objectTypeName: '服务器',
        metricCode: 'cpu_usage',
        metricName: 'CPU 使用率',
        currentValue: `CPU 使用率=${80 + id / 10}`,
        thresholdValue: '80',
        alertLevel: id % 3 === 0 ? 'CRITICAL' : 'SERIOUS',
        alertLevelName: id % 3 === 0 ? '紧急' : '严重',
        eventStatus: 'PENDING',
        eventStatusName: '待处理',
        aiSummaryStatus: 'SUCCESS',
        aiSummary: JSON.stringify({
          what: 'CPU 使用率持续超过阈值。',
          impact: '可能影响 Web 服务响应。',
          causes: ['流量升高', '后台任务堆积'],
          actions: ['检查进程负载', '扩容或限流']
        }),
        firstTriggeredAt: `2026-05-28T10:${String(index).padStart(2, '0')}:00`,
        lastTriggeredAt: `2026-05-28T10:${String(index).padStart(2, '0')}:30`,
        notifyLogs: [],
        handleLogs: []
      }
    })

    await page.route('**/api/alert-events**', async (route) => {
      const url = new URL(route.request().url())
      const id = Number(url.pathname.match(/\/api\/alert-events\/(\d+)$/)?.[1])
      const data = Number.isFinite(id) && id > 0
        ? (events.find((item) => item.id === id) ?? events[0])
        : events
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, message: 'ok', data })
      })
    })

    await page.goto('/events', { waitUntil: 'domcontentloaded' })
    const target = page.locator('.event-card').nth(24)
    await target.scrollIntoViewIfNeeded()
    await target.click()

    const detail = page.locator('.detail')
    await expect(detail).toBeVisible({ timeout: 5_000 })
    await expect(detail.locator('.detail-title')).toContainText('测试对象-25')
    const summaryCard = detail.locator('.ai-summary-card')
    await expect(summaryCard).toBeVisible()
    await expect(summaryCard.locator('.head')).toBeVisible()
    await expect(summaryCard.locator('.refresh-btn')).toBeVisible()

    const box = await detail.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.y).toBeGreaterThanOrEqual(72)
    expect(box!.y).toBeLessThan(130)
    expect(box!.height).toBeGreaterThan(260)
    expect(box!.y + Math.min(box!.height, 700)).toBeLessThanOrEqual(900)

    const cardBox = await summaryCard.boundingBox()
    const headBox = await summaryCard.locator('.head').boundingBox()
    const refreshBox = await summaryCard.locator('.refresh-btn').boundingBox()
    const contentBox = await summaryCard.locator('.content').boundingBox()
    expect(cardBox).not.toBeNull()
    expect(headBox).not.toBeNull()
    expect(refreshBox).not.toBeNull()
    expect(contentBox).not.toBeNull()
    expect(headBox!.height).toBeLessThanOrEqual(42)
    expect(refreshBox!.x + refreshBox!.width).toBeLessThanOrEqual(cardBox!.x + cardBox!.width - 12)
    expect(contentBox!.y).toBeGreaterThan(headBox!.y + headBox!.height)
    expect(cardBox!.height).toBeGreaterThan(contentBox!.y + contentBox!.height - cardBox!.y)
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
    const processPanel = detail.locator('.ai-process-panel')
    await expect(processPanel).toBeVisible()
    await expect(processPanel).toContainText('AI 分析过程')
    await expect(processPanel.locator('.process-step')).toHaveCount(4)
    await expect(processPanel.locator('.process-step').first()).toContainText('读取上下文')

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
