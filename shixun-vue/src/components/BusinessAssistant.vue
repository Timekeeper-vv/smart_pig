<script setup lang="ts">
import { onMounted, ref } from 'vue'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const active = ref<'quote' | 'planning' | 'logistics' | 'finance'>('quote')
const loading = ref(false)
const quoteResult = ref<any>(null)
const planResult = ref<any>(null)
const logisticsResult = ref<any>(null)
const financeResult = ref<any>(null)

const quoteForm = ref({
  productName: '亚克力钥匙扣',
  productType: '亚克力钥匙扣',
  quantity: 1000,
  size: '5cm，双面印刷',
  material: '3mm透明亚克力',
  packaging: '独立OPP袋',
  destination: '上海',
  designFee: 300,
  overheadRate: 12,
  targetMarginRate: 45
})

const planForm = ref({
  theme: '城市味道与街巷记忆',
  audience: '年轻游客、企业伴手礼客户、文创爱好者',
  channel: '小红书、抖音、线下快闪、景区店',
  budget: '首批小批量试产，单品29-99元'
})

const logisticsForm = ref({
  orderNo: '',
  productType: '明信片/礼盒',
  quantity: 300,
  destination: '杭州'
})

async function postJson(url: string, body: any) {
  const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  if (!res.ok) throw new Error(await res.text())
  return res.json()
}

async function runQuote() {
  loading.value = true
  try {
    quoteResult.value = await postJson('/api/creative/assistant/quote', quoteForm.value)
    emit('alert', `报价完成：建议单价 ¥${quoteResult.value.suggestedUnitPrice}`, 'success')
  } catch (e: any) { emit('alert', `报价失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}

async function runPlanning() {
  loading.value = true
  try {
    planResult.value = await postJson('/api/creative/assistant/planning', planForm.value)
    emit('alert', '新品企划已生成', 'success')
  } catch (e: any) { emit('alert', `企划失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}

async function runLogistics() {
  loading.value = true
  try {
    logisticsResult.value = await postJson('/api/creative/assistant/logistics', logisticsForm.value)
    emit('alert', `物流预测完成：预计 ${logisticsResult.value.eta} 到达`, 'success')
  } catch (e: any) { emit('alert', `物流预测失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}

async function loadFinance() {
  loading.value = true
  try {
    const res = await fetch('/api/creative/assistant/finance')
    if (!res.ok) throw new Error(await res.text())
    financeResult.value = await res.json()
  } catch (e: any) { emit('alert', `经营分析失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}

function copy(text: string) {
  navigator.clipboard?.writeText(text || '')
  emit('alert', '已复制', 'success')
}

function money(v: any) { return Number(v || 0).toFixed(2) }
onMounted(() => { runQuote(); loadFinance() })
</script>

<template>
  <div class="page business-page">
    <div class="page-header biz-hero">
      <div>
        <p class="eyebrow">AI BUSINESS OPERATIONS</p>
        <h2 class="page-title">AI业务助手</h2>
        <p class="page-desc">把报价、产品企划、订单物流、经营分析四步补齐，形成文创产品从询盘到回款的闭环。</p>
      </div>
      <button class="btn btn-secondary" @click="loadFinance">刷新经营数据</button>
    </div>

    <div class="tabs">
      <button :class="{active:active==='quote'}" @click="active='quote'">AI报价助手</button>
      <button :class="{active:active==='planning'}" @click="active='planning'">AI新品企划</button>
      <button :class="{active:active==='logistics'}" @click="active='logistics'">订单物流助手</button>
      <button :class="{active:active==='finance'}" @click="active='finance'; loadFinance()">经营分析助手</button>
    </div>

    <section v-if="active==='quote'" class="work-grid">
      <div class="panel-card">
        <h3>客户询盘转报价</h3>
        <label>产品名称</label><input v-model="quoteForm.productName" />
        <label>产品类型</label><select v-model="quoteForm.productType"><option>亚克力钥匙扣</option><option>冰箱贴</option><option>徽章</option><option>贴纸包</option><option>明信片套装</option><option>帆布袋</option><option>礼盒</option></select>
        <div class="form-row"><div><label>数量</label><input v-model.number="quoteForm.quantity" type="number" /></div><div><label>目标毛利%</label><input v-model.number="quoteForm.targetMarginRate" type="number" /></div><div><label>设计费</label><input v-model.number="quoteForm.designFee" type="number" /></div></div>
        <label>尺寸</label><input v-model="quoteForm.size" />
        <label>材质</label><input v-model="quoteForm.material" />
        <label>包装</label><input v-model="quoteForm.packaging" />
        <label>收货地</label><input v-model="quoteForm.destination" />
        <button class="btn btn-primary full" :disabled="loading" @click="runQuote">{{ loading ? '核算中...' : '生成报价和客户回复' }}</button>
      </div>
      <div v-if="quoteResult" class="panel-card result-panel">
        <div class="panel-head"><div><h3>{{ quoteResult.quoteNo }}</h3><p>{{ quoteResult.productName }} · {{ quoteResult.quantity }}件</p></div><button class="mini" @click="copy(quoteResult.customerReply)">复制客户回复</button></div>
        <div class="quote-kpis"><div><span>建议单价</span><b>¥{{ money(quoteResult.suggestedUnitPrice) }}</b></div><div><span>内部底价</span><b>¥{{ money(quoteResult.floorUnitPrice) }}</b></div><div><span>总报价</span><b>¥{{ money(quoteResult.totalQuote) }}</b></div><div><span>总成本</span><b>¥{{ money(quoteResult.totalCost) }}</b></div></div>
        <p class="reply">{{ quoteResult.customerReply }}</p>
        <h4>还需确认</h4><ul><li v-for="q in quoteResult.questions" :key="q">{{ q }}</li></ul>
        <h4>风险提醒</h4><ul><li v-for="r in quoteResult.risks" :key="r">{{ r }}</li></ul>
        <div v-if="quoteResult.aiDraft" class="ai-draft"><h4>硅基流动AI增强建议</h4><pre>{{ quoteResult.aiDraft }}</pre></div>
      </div>
    </section>

    <section v-if="active==='planning'" class="work-grid">
      <div class="panel-card">
        <h3>新品开发企划</h3>
        <label>主题方向</label><textarea v-model="planForm.theme" rows="2" />
        <label>目标人群</label><textarea v-model="planForm.audience" rows="2" />
        <label>渠道</label><input v-model="planForm.channel" />
        <label>预算/价格带</label><input v-model="planForm.budget" />
        <button class="btn btn-primary full" :disabled="loading" @click="runPlanning">生成企划</button>
      </div>
      <div v-if="planResult" class="panel-card result-panel">
        <h3>{{ planResult.planNo }}</h3>
        <p class="reply">{{ planResult.positioning }}</p>
        <div class="concept" v-for="c in planResult.concepts" :key="c.name"><b>{{ c.name }}</b><span>{{ c.insight }}</span><small>推荐SKU：{{ c.recommendedSkus.join('、') }}</small><em>{{ c.reason }}</em></div>
        <h4>上市节奏</h4><div class="road" v-for="r in planResult.launchRoadmap" :key="r.week"><b>{{ r.week }}</b><span>{{ r.task }}</span></div>
        <h4>风险控制</h4><ul><li v-for="r in planResult.riskControl" :key="r">{{ r }}</li></ul>
        <div v-if="planResult.aiDraft" class="ai-draft"><h4>硅基流动AI增强企划</h4><pre>{{ planResult.aiDraft }}</pre></div>
      </div>
    </section>

    <section v-if="active==='logistics'" class="work-grid">
      <div class="panel-card">
        <h3>订单履约/物流预测</h3>
        <label>订单号，可为空</label><input v-model="logisticsForm.orderNo" placeholder="不填则读取最近订单" />
        <label>产品类型</label><input v-model="logisticsForm.productType" />
        <div class="form-row two"><div><label>数量</label><input v-model.number="logisticsForm.quantity" type="number" /></div><div><label>收货地</label><input v-model="logisticsForm.destination" /></div></div>
        <button class="btn btn-primary full" :disabled="loading" @click="runLogistics">预测交付时间</button>
      </div>
      <div v-if="logisticsResult" class="panel-card result-panel">
        <h3>{{ logisticsResult.logisticsNo }}</h3>
        <div class="quote-kpis"><div><span>预估运费</span><b>¥{{ money(logisticsResult.estimatedFreight) }}</b></div><div><span>生产</span><b>{{ logisticsResult.productionDays }}天</b></div><div><span>运输</span><b>{{ logisticsResult.shippingDays }}天</b></div><div><span>预计到达</span><b>{{ logisticsResult.eta }}</b></div></div>
        <p class="reply">{{ logisticsResult.customerReply }}</p>
        <h4>物流方案</h4><div class="road" v-for="c in logisticsResult.carrierOptions" :key="c.name"><b>{{ c.name }}</b><span>¥{{ money(c.cost) }} · {{ c.advantage }}</span></div>
        <h4>履约提醒</h4><ul><li v-for="a in logisticsResult.alerts" :key="a">{{ a }}</li></ul>
        <div v-if="logisticsResult.aiDraft" class="ai-draft"><h4>硅基流动AI履约建议</h4><pre>{{ logisticsResult.aiDraft }}</pre></div>
      </div>
    </section>

    <section v-if="active==='finance'" class="panel-card result-panel">
      <div class="panel-head"><div><h3>经营分析</h3><p>基于当前订单、SKU、报价记录自动生成</p></div><button class="mini" @click="loadFinance">刷新</button></div>
      <div v-if="financeResult" class="quote-kpis"><div><span>成交额</span><b>¥{{ money(financeResult.summary.revenue) }}</b></div><div><span>订单数</span><b>{{ financeResult.summary.orderCount }}</b></div><div><span>客单价</span><b>¥{{ money(financeResult.summary.avgOrder) }}</b></div><div><span>SKU/IP</span><b>{{ financeResult.summary.skuCount }}/{{ financeResult.summary.artworkCount }}</b></div></div>
      <div v-if="financeResult" class="finance-grid"><div><h4>分析结论</h4><ul><li v-for="a in financeResult.analysis" :key="a">{{ a }}</li></ul></div><div><h4>下一步</h4><ul><li v-for="n in financeResult.nextActions" :key="n">{{ n }}</li></ul></div></div>
      <div v-if="financeResult?.aiDraft" class="ai-draft"><h4>硅基流动AI经营简报</h4><pre>{{ financeResult.aiDraft }}</pre></div>
      <table v-if="financeResult?.topSkus?.length"><thead><tr><th>热销SKU</th><th>销量</th><th>金额</th></tr></thead><tbody><tr v-for="s in financeResult.topSkus" :key="s.productName"><td>{{ s.productName }}</td><td>{{ s.qty }}</td><td>¥{{ money(s.amount) }}</td></tr></tbody></table>
    </section>
  </div>
</template>

<style scoped>
.business-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.biz-hero{padding:28px;border-radius:20px;color:#fff;background:linear-gradient(135deg,#111827,#0f766e 45%,#7c2d12);box-shadow:var(--shadow-md)}.biz-hero .page-title{color:#fff;font-size:28px}.biz-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#ccfbf1;font-weight:800}.tabs{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.tabs button{border:1px solid var(--c-border);background:#fff;border-radius:999px;padding:10px 16px;cursor:pointer;font-weight:700;color:#475569}.tabs button.active{background:#0f766e;color:#fff;border-color:#0f766e}.work-grid{display:grid;grid-template-columns:.9fr 1.1fr;gap:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm);overflow:auto}.panel-card h3{margin:0 0 16px}.panel-card label{display:block;font-size:12px;font-weight:700;color:var(--c-text-2);margin:10px 0 6px}input,textarea,select{width:100%;border:1px solid var(--c-border);border-radius:10px;padding:10px;background:#fff;font-family:inherit}input,select{height:40px}.form-row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.form-row.two{grid-template-columns:1fr 1fr}.full{width:100%;margin-top:16px}.panel-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}.panel-head h3{margin:0 0 4px}.panel-head p{margin:0;color:#64748b;font-size:13px}.mini{border:0;background:#f1f5f9;border-radius:999px;padding:7px 12px;cursor:pointer}.quote-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:14px 0}.quote-kpis div{background:#f0fdfa;border:1px solid #99f6e4;border-radius:14px;padding:12px}.quote-kpis span{display:block;color:#0f766e;font-size:12px;margin-bottom:6px}.quote-kpis b{font-size:20px}.reply{background:#fff7ed;border:1px solid #fed7aa;border-radius:14px;padding:14px;line-height:1.8;color:#334155}.concept{display:flex;flex-direction:column;gap:6px;border:1px solid #eef2f7;border-radius:14px;padding:14px;margin-bottom:10px}.concept b{color:#9a3412}.concept span,.concept small,.concept em{color:#64748b;font-style:normal;line-height:1.6}.road{display:flex;gap:12px;border-bottom:1px solid #eef2f7;padding:10px 0}.road b{min-width:70px;color:#0f766e}.road span{color:#475569}.finance-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}table{width:100%;border-collapse:collapse;margin-top:16px}th,td{padding:10px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px}th{background:#f8fafc;color:#64748b}ul{padding-left:20px}li{margin:6px 0;line-height:1.6}.ai-draft{margin-top:14px;border:1px solid #c4b5fd;background:#faf5ff;border-radius:14px;padding:14px}.ai-draft h4{margin:0 0 10px;color:#6d28d9}.ai-draft pre{white-space:pre-wrap;word-break:break-word;line-height:1.7;margin:0;color:#334155;font-family:inherit}@media(max-width:1000px){.work-grid,.finance-grid{grid-template-columns:1fr}.quote-kpis{grid-template-columns:1fr 1fr}.form-row,.form-row.two{grid-template-columns:1fr}}
</style>
