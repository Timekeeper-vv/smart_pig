<script setup lang="ts">
import { onMounted, ref } from 'vue'

const emit = defineEmits<{ 'switch-page': [page: string], alert: [msg: string, type?: 'success' | 'error'] }>()
const loading = ref(false)
const data = ref<any>({ hotArtworks: [], latestOrders: [] })

async function load() {
  loading.value = true
  try {
    const res = await fetch('/api/creative/dashboard')
    if (!res.ok) throw new Error(await res.text())
    data.value = await res.json()
  } catch (e: any) {
    emit('alert', `加载文创概览失败：${e.message || e}`, 'error')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="page creative-page">
    <div class="page-header hero-admin">
      <div>
        <p class="eyebrow">AND TASTE CREATIVE COMMERCE</p>
        <h2 class="page-title">之间味道 · 文创设计售卖平台</h2>
        <p class="page-desc">围绕图片IP、衍生SKU、创作者收益和订单履约搭建的数字文创经营后台。</p>
      </div>
      <button class="btn btn-primary" @click="emit('switch-page', 'marketplace')">进入图片IP商城</button>
    </div>

    <div class="stats-row">
      <div class="stat-card"><div class="stat-label">图片IP</div><div class="stat-num primary">{{ data.artworkCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">文创SKU</div><div class="stat-num success">{{ data.skuCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">设计师</div><div class="stat-num purple">{{ data.designerCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">订单数</div><div class="stat-num warning">{{ data.orderCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">成交额</div><div class="stat-num info">¥{{ Number(data.revenue || 0).toFixed(2) }}</div></div>
    </div>

    <div class="creative-grid-2">
      <section class="panel-card">
        <div class="panel-head">
          <div><h3>热门图片IP</h3><p>按收藏与浏览热度排序</p></div>
          <button class="btn btn-secondary btn-sm" @click="emit('switch-page', 'marketplace')">查看全部</button>
        </div>
        <div v-if="loading" class="empty">加载中...</div>
        <div v-else class="hot-list">
          <div v-for="item in data.hotArtworks" :key="item.id" class="hot-item">
            <img :src="item.imageUrl" alt="" />
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.categoryName }} · {{ item.designerName }}</span>
              <small>浏览 {{ item.viewCount }} · 收藏 {{ item.favoriteCount }}</small>
            </div>
          </div>
        </div>
      </section>

      <section class="panel-card">
        <div class="panel-head">
          <div><h3>业务模型重构</h3><p>从养殖管理转为图片类文创电商</p></div>
        </div>
        <div class="model-flow">
          <div><b>图片IP</b><span>作品、分类、标签、授权</span></div>
          <i>→</i>
          <div><b>文创SKU</b><span>明信片、装饰画、手机壳等</span></div>
          <i>→</i>
          <div><b>交易订单</b><span>下单、支付、履约、评价</span></div>
          <i>→</i>
          <div><b>设计师收益</b><span>分成、结算、提现扩展</span></div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.creative-page { background: #f8fafc; min-height: calc(100vh - var(--header-h)); }
.hero-admin { padding: 28px; border-radius: 20px; color: #fff; background: radial-gradient(circle at 20% 20%, rgba(255,255,255,.22), transparent 24%), linear-gradient(135deg, #111827, #7c2d12 45%, #111827); box-shadow: var(--shadow-md); }
.hero-admin .page-title { color:#fff; font-size: 28px; }
.hero-admin .page-desc { color: rgba(255,255,255,.78); }
.eyebrow { margin: 0 0 8px; font-size: 12px; letter-spacing: 2px; color: #fed7aa; font-weight: 800; }
.creative-grid-2 { display:grid; grid-template-columns: 1.1fr .9fr; gap: 18px; }
.panel-card { background:#fff; border:1px solid var(--c-border); border-radius: 18px; padding: 20px; box-shadow: var(--shadow-sm); }
.panel-head { display:flex; justify-content:space-between; gap:12px; margin-bottom:16px; }
.panel-head h3 { margin:0 0 4px; font-size:17px; }
.panel-head p { margin:0; color:var(--c-text-2); font-size:13px; }
.hot-list { display:flex; flex-direction:column; gap:12px; }
.hot-item { display:flex; gap:12px; padding:10px; border:1px solid #eef2f7; border-radius:14px; }
.hot-item img { width:88px; height:58px; object-fit:cover; border-radius:10px; }
.hot-item div { display:flex; flex-direction:column; gap:4px; }
.hot-item span, .hot-item small { color:var(--c-text-2); font-size:12px; }
.model-flow { display:flex; flex-direction:column; gap:10px; }
.model-flow div { padding:14px; border-radius:14px; background:#fff7ed; border:1px solid #fed7aa; }
.model-flow b { display:block; color:#9a3412; margin-bottom:4px; }
.model-flow span { color:#64748b; font-size:13px; }
.model-flow i { text-align:center; color:#f97316; font-style:normal; font-weight:800; }
.empty { color:var(--c-text-2); padding:24px; text-align:center; }
@media (max-width: 900px){ .creative-grid-2 { grid-template-columns:1fr; } }
</style>
