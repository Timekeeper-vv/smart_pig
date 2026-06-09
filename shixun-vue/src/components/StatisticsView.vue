<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import type { MonthlyData, AnimalStatusData, PenUsageData } from '../types'

interface ChartsData {
  monthlySlaughter: MonthlyData[]
  monthlyImmunization: MonthlyData[]
  animalStatus: AnimalStatusData[]
  penUsage: PenUsageData[]
}

const loading = ref<boolean>(true)
const chartsData = ref<ChartsData>({
  monthlySlaughter: [],
  monthlyImmunization: [],
  animalStatus: [],
  penUsage: [],
})

let chartInstances: ECharts[] = []

function disposeCharts(): void {
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
}

// Pad last 6 months so empty months still show on axis
function padMonths(data: MonthlyData[]): { months: string[]; values: number[] } {
  const months = []
  for (let i = 5; i >= 0; i--) {
    const d = new Date()
    d.setDate(1)
    d.setMonth(d.getMonth() - i)
    months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  const map = Object.fromEntries(data.map(r => [r.month, Number(r.count)]))
  return { months, values: months.map(m => map[m] ?? 0) }
}

function initCharts() {
  disposeCharts()

  // Chart 1: Monthly immunization trend (line)
  const immEl = document.getElementById('chart-immunization')
  if (immEl) {
    const { months, values } = padMonths(chartsData.value.monthlyImmunization)
    const c = echarts.init(immEl)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 20, right: 20, bottom: 36, left: 46 },
      xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      series: [{
        name: '免疫次数',
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#0d9488', width: 2 },
        itemStyle: { color: '#0d9488' },
        areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(13,148,136,.25)' }, { offset: 1, color: 'rgba(13,148,136,0)' }] } },
      }],
    })
    chartInstances.push(c)
  }

  // Chart 2: Monthly slaughter analysis (bar)
  const slEl = document.getElementById('chart-slaughter')
  if (slEl) {
    const { months, values } = padMonths(chartsData.value.monthlySlaughter)
    const c = echarts.init(slEl)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 20, right: 20, bottom: 36, left: 46 },
      xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      series: [{
        name: '出栏头数',
        type: 'bar',
        data: values,
        barMaxWidth: 36,
        itemStyle: { color: '#2563eb', borderRadius: [4, 4, 0, 0] },
      }],
    })
    chartInstances.push(c)
  }

  // Chart 3: Animal status pie
  const statusEl = document.getElementById('chart-status')
  if (statusEl) {
    const statusLabels: Record<string, string> = { ACTIVE: '在栏', SOLD: '已出栏' }
    const statusColors: Record<string, string> = { ACTIVE: '#0d9488', SOLD: '#94a3b8' }
    const pieData = chartsData.value.animalStatus.map(r => ({
      name: statusLabels[r.status] || r.status,
      value: Number(r.count),
      itemStyle: { color: statusColors[r.status] || '#6366f1' },
    }))
    const c = echarts.init(statusEl)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0, left: 'center', textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '45%'],
        data: pieData.length ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e2e8f0' } }],
        label: { show: false },
      }],
    })
    chartInstances.push(c)
  }

  // Chart 4: Pen capacity vs usage (horizontal bar)
  const penEl = document.getElementById('chart-pen')
  if (penEl) {
    const pens = chartsData.value.penUsage
    const names = pens.map(p => p.penName)
    const caps = pens.map(p => Number(p.capacity))
    const counts = pens.map(p => Number(p.currentCount))
    const c = echarts.init(penEl)
    c.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0, right: 0, textStyle: { fontSize: 12 } },
      grid: { top: 28, right: 20, bottom: 36, left: 70 },
      xAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11 } },
      series: [
        { name: '设计容量', type: 'bar', data: caps, barMaxWidth: 20, itemStyle: { color: '#e2e8f0', borderRadius: [0, 4, 4, 0] } },
        { name: '当前存栏', type: 'bar', data: counts, barMaxWidth: 20, itemStyle: { color: '#0d9488', borderRadius: [0, 4, 4, 0] } },
      ],
    })
    chartInstances.push(c)
  }
}

function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

onMounted(async () => {
  try {
    const [slRes, immRes, statusRes, penRes] = await Promise.all([
      fetch('/api/stats/monthly-slaughter'),
      fetch('/api/stats/monthly-immunization'),
      fetch('/api/stats/animal-status'),
      fetch('/api/stats/pen-usage'),
    ])
    chartsData.value = {
      monthlySlaughter:     await slRes.json(),
      monthlyImmunization:  await immRes.json(),
      animalStatus:         await statusRes.json(),
      penUsage:             await penRes.json(),
    }
  } catch { /* silent */ } finally {
    loading.value = false
  }
  await nextTick()
  initCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">数据统计分析</h2>
        <p class="page-desc">可视化呈现养殖场运营关键指标</p>
      </div>
    </div>

    <div v-if="loading" class="loading-state">加载中…</div>

    <div v-else class="charts-grid">
      <!-- 免疫覆盖率趋势 -->
      <div class="chart-card">
        <div class="chart-title">免疫覆盖趋势（近6个月）</div>
        <div id="chart-immunization" class="chart-body"></div>
      </div>

      <!-- 出栏量月度分析 -->
      <div class="chart-card">
        <div class="chart-title">出栏量月度分析（近6个月）</div>
        <div id="chart-slaughter" class="chart-body"></div>
      </div>

      <!-- 个体状态分布 -->
      <div class="chart-card">
        <div class="chart-title">个体状态分布</div>
        <div id="chart-status" class="chart-body"></div>
      </div>

      <!-- 圈舍容量利用率 -->
      <div class="chart-card">
        <div class="chart-title">圈舍容量利用率</div>
        <div id="chart-pen" class="chart-body"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.loading-state {
  text-align: center;
  padding: 60px;
  color: var(--c-text-3);
  font-size: 14px;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(460px, 1fr));
  gap: 20px;
}

.chart-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.chart-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  margin-bottom: 16px;
}

.chart-body {
  height: 240px;
  width: 100%;
}
</style>
