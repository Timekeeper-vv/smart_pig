<script setup lang="ts">
import { onMounted, ref } from 'vue'
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const designers = ref<any[]>([])
async function load(){
  try { designers.value = await (await fetch('/api/creative/designers')).json() }
  catch(e:any){ emit('alert', `加载设计师失败：${e.message || e}`, 'error') }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">设计师 / 创作者中心</h2><p class="page-desc">承载作品上传、授权协议、销售数据、收益分成和提现申请等供给端能力。</p></div><button class="btn btn-primary" @click="load">刷新</button></div>
    <div class="designer-grid">
      <article v-for="d in designers" :key="d.id" class="designer-card">
        <div class="avatar">{{ d.brandName?.[0] }}</div>
        <h3>{{ d.brandName }}</h3>
        <p>{{ d.bio }}</p>
        <div class="designer-stats"><span>作品 {{ d.artworkCount }}</span><span>分成 {{ Number(d.revenueShare).toFixed(0) }}%</span><span>{{ d.auditStatus }}</span></div>
      </article>
    </div>
    <section class="roadmap">
      <h3>下一阶段接口契约</h3>
      <ul>
        <li>POST /api/creative/artworks：设计师投稿图片IP</li>
        <li>PUT /api/creative/artworks/{id}/audit：运营审核作品</li>
        <li>POST /api/creative/skus：将图片IP衍生为文创SKU</li>
        <li>POST /api/creative/settlements：设计师收益结算/提现申请</li>
      </ul>
    </section>
  </div>
</template>
<style scoped>
.designer-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px}.designer-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm)}.avatar{width:52px;height:52px;border-radius:16px;background:linear-gradient(135deg,#f97316,#111827);color:#fff;display:flex;align-items:center;justify-content:center;font-size:22px;font-weight:800;margin-bottom:14px}.designer-card h3{margin:0 0 8px}.designer-card p{color:var(--c-text-2);font-size:13px;line-height:1.6;min-height:62px}.designer-stats{display:flex;gap:8px;flex-wrap:wrap}.designer-stats span{background:#fff7ed;color:#9a3412;border-radius:999px;padding:4px 9px;font-size:12px}.roadmap{margin-top:18px;background:#111827;color:#fff;border-radius:18px;padding:20px}.roadmap h3{margin:0 0 12px}.roadmap li{margin:8px 0;color:#d1d5db}
</style>
