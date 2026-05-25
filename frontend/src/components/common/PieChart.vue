<template>
  <div ref="el" class="pie-chart" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  data: Array<{ name: string; value: number; color?: string }>
  height?: string
}>()

const el = ref<HTMLDivElement>()
let chart: echarts.ECharts | undefined

function render() {
  if (!el.value) return
  if (!chart) chart = echarts.init(el.value)
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#94A3B8', fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '76%'],
        center: ['50%', '46%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#0B1120', borderWidth: 3 },
        label: { show: false },
        emphasis: {
          label: {
            show: true,
            color: '#F8FAFC',
            fontSize: 14,
            fontWeight: 600,
            formatter: '{b}\n{c}'
          }
        },
        data: props.data.map((d) => ({
          name: d.name,
          value: d.value,
          itemStyle: d.color ? { color: d.color } : undefined
        }))
      }
    ]
  })
}

watch(() => props.data, render, { deep: true })

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
.pie-chart {
  width: 100%;
  height: v-bind('props.height || "240px"');
}
</style>
