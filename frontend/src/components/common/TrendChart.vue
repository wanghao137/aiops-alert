<template>
  <div ref="el" class="trend-chart" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  trend: Array<{ date: string; total: number; pending: number; recovered: number; critical: number }>
  height?: string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  const xs = props.trend.map((d) => d.date.slice(5))
  chart.setOption({
    backgroundColor: 'transparent',
    color: ['#3B82F6', '#F59E0B', '#10B981', '#EF4444'],
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#0F172A',
      borderColor: '#1F2937',
      textStyle: { color: '#F8FAFC' }
    },
    legend: {
      top: 0, right: 0,
      icon: 'roundRect',
      textStyle: { color: '#94A3B8', fontSize: 12 },
      data: ['总量', '待处理', '已恢复', '紧急']
    },
    grid: { left: 36, right: 18, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: xs,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#1F2937' } },
      axisLabel: { color: '#94A3B8' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#1F2937' } },
      axisLabel: { color: '#94A3B8' }
    },
    series: [
      seriesLine('总量', props.trend.map((d) => d.total), '#3B82F6', true),
      seriesLine('待处理', props.trend.map((d) => d.pending), '#F59E0B', false),
      seriesLine('已恢复', props.trend.map((d) => d.recovered), '#10B981', false),
      seriesLine('紧急', props.trend.map((d) => d.critical), '#EF4444', false)
    ]
  })
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
              { offset: 0, color: color + '55' },
              { offset: 1, color: color + '00' }
            ]
          }
        }
      : undefined,
    data
  }
}

watch(() => props.trend, render, { deep: true })

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
