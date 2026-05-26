<template>
  <div ref="el" class="trend-chart" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore } from '@/stores/theme'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  trend: Array<{ date: string; total: number; pending: number; recovered: number; critical: number }>
  height?: string
}>()

const el = ref<HTMLDivElement>()
const theme = useThemeStore()
let chart: echarts.ECharts | undefined

/* 从 CSS 变量读取，避免硬编码 dark/light 颜色 */
function readToken(name: string, fallback: string) {
  if (!el.value) return fallback
  const v = getComputedStyle(el.value).getPropertyValue(name).trim()
  return v || fallback
}

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)

  const xs = props.trend.map((d) => d.date.slice(5))

  const tooltipBg = readToken('--bg-elev-1', '#0E1018')
  const lineCol = readToken('--line', '#1C2030')
  const lineStrong = readToken('--line-strong', '#2A2F3F')
  const textMuted = readToken('--text-muted', '#94A3B8')
  const textPrimary = readToken('--text-primary', '#F8FAFC')

  const accent = readToken('--accent', '#7DD3FC')
  const warn = readToken('--warn', '#FBBF24')
  const ok = readToken('--ok', '#34D399')
  const critical = readToken('--critical', '#FB7185')

  chart.setOption({
    backgroundColor: 'transparent',
    color: [accent, warn, ok, critical],
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: lineStrong,
      borderWidth: 1,
      textStyle: { color: textPrimary, fontSize: 12 }
    },
    legend: {
      top: 0, right: 0,
      icon: 'roundRect',
      textStyle: { color: textMuted, fontSize: 12 },
      data: ['总量', '待处理', '已恢复', '紧急']
    },
    grid: { left: 36, right: 18, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: xs,
      boundaryGap: false,
      axisLine: { lineStyle: { color: lineCol } },
      axisLabel: { color: textMuted }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: lineCol } },
      axisLabel: { color: textMuted }
    },
    series: [
      seriesLine('总量',   props.trend.map((d) => d.total),     accent,   true),
      seriesLine('待处理', props.trend.map((d) => d.pending),   warn,     false),
      seriesLine('已恢复', props.trend.map((d) => d.recovered), ok,       false),
      seriesLine('紧急',   props.trend.map((d) => d.critical),  critical, false)
    ]
  })
}

function hexToRgba(hex: string, alpha: number) {
  const m = hex.replace('#', '').match(/^([0-9a-f]{6})$/i)
  if (!m) return hex
  const r = parseInt(m[1].slice(0, 2), 16)
  const g = parseInt(m[1].slice(2, 4), 16)
  const b = parseInt(m[1].slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function seriesLine(name: string, data: number[], color: string, area: boolean) {
  return {
    name,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 6,
    showSymbol: false,
    lineStyle: { width: 2 },
    areaStyle: area
      ? {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: hexToRgba(color, 0.34) },
              { offset: 1, color: hexToRgba(color, 0) }
            ]
          }
        }
      : undefined,
    data
  }
}

watch(() => props.trend, render, { deep: true })

/* 主题切换时重渲染，保证颜色与 token 一致 */
watch(() => theme.isDark, () => {
  // 等 :root[data-theme] 切换 + DOM 渲染完成
  requestAnimationFrame(render)
})

onMounted(() => {
  render()
  window.addEventListener('resize', resize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resize)
  chart?.dispose()
})

function resize() {
  chart?.resize()
}
</script>

<style scoped>
.trend-chart {
  width: 100%;
  height: v-bind('props.height || "320px"');
}
</style>
