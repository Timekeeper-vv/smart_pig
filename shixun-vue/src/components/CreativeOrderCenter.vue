<script setup lang="ts">
import { onMounted, ref } from 'vue'
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const orders = ref<any[]>([])
async function load(){
  try { orders.value = await (await fetch('/api/creative/orders')).json() }
  catch(e:any){ emit('alert', `加载订单失败：${e.message || e}`, 'error') }
}
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="page-header"><div><h2 class="page-title">订单中心</h2><p class="page-desc">支持后续接入微信/支付宝支付、生产履约、发货与退款流程。</p></div><button class="btn btn-primary" @click="load">刷新订单</button></div>
    <div class="order-list">
      <article v-for="o in orders" :key="o.id" class="order-card">
        <div class="order-head"><div><b>{{ o.orderNo }}</b><span>买家：{{ o.buyerName || o.userId }}</span></div><strong>¥{{ Number(o.payAmount).toFixed(2) }}</strong><em>{{ o.orderStatus }}</em></div>
        <div class="order-items"><div v-for="it in o.items" :key="it.id" class="order-item"><img :src="it.coverUrl"><span>{{ it.productName }} / {{ it.artworkTitle }}</span><b>×{{ it.quantity }}</b></div></div>
      </article>
      <div v-if="!orders.length" class="empty">暂无订单，可在“图片IP商城”点击模拟购买生成订单。</div>
    </div>
  </div>
</template>
<style scoped>
.order-list{display:flex; flex-direction:column; gap:14px}.order-card{background:#fff;border:1px solid var(--c-border);border-radius:16px;padding:16px;box-shadow:var(--shadow-sm)}.order-head{display:flex;align-items:center;justify-content:space-between;gap:12px;border-bottom:1px solid #eef2f7;padding-bottom:12px}.order-head div{display:flex;flex-direction:column;gap:4px}.order-head span{font-size:12px;color:var(--c-text-2)}.order-head strong{color:#dc2626;font-size:20px}.order-head em{font-style:normal;background:#dbeafe;color:#1d4ed8;padding:4px 10px;border-radius:999px;font-size:12px}.order-items{padding-top:12px;display:flex;flex-direction:column;gap:8px}.order-item{display:flex;align-items:center;gap:10px;color:var(--c-text-2)}.order-item img{width:58px;height:42px;object-fit:cover;border-radius:8px}.order-item span{flex:1}.empty{text-align:center;color:var(--c-text-2);padding:48px;background:#fff;border-radius:16px;border:1px dashed var(--c-border)}
</style>
