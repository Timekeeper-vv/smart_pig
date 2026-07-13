<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const active = ref<'inquiry'|'quotes'|'fulfillment'|'cost'|'design'>('inquiry')
const loading = ref(false)
const loadingText = ref('')
const dashboard = ref<any>({})
const customers = ref<any[]>([])
const inquiries = ref<any[]>([])
const quotes = ref<any[]>([])
const selectedInquiry = ref<any>(null)
const selectedQuote = ref<any>(null)
const cost = ref<any>({ materials: [], processes: [], productTypes: [] })
const planResult = ref<any>(null)
const reviewResult = ref<any>(null)

const inquiryForm = ref<any>({
  customerName: '山城文旅客户', contactName: '李经理', phone: '13800008888', wechat: 'wx-demo', company: '山城文旅集团',
  title: '山城街巷亚克力钥匙扣定制', productName: '山城街巷钥匙扣', productType: '亚克力钥匙扣', quantity: 1000,
  size: '5cm，双面印刷，异形', material: '3mm透明亚克力+金属扣', packaging: '独立OPP袋', destination: '上海', budget: '单价15元以内', usageScenario: '景区伴手礼/活动赠品',
  rawRequirement: '客户想做1000个山城街巷主题亚克力钥匙扣，5cm左右，双面彩印，独立包装，发上海，需要报价和交期。'
})
const quoteOptions = ref({ designFee: 300, overheadRate: 12, targetMarginRate: 45 })
const planForm = ref({ theme: '山城街巷与城市味道', audience: '年轻游客、景区伴手礼客户、企业活动客户', productTypes: '钥匙扣、冰箱贴、明信片、贴纸包', budget: '29-99元价格带，小批量试产' })
const reviewForm = ref({ concept: '山城街巷系列：以坡道、路灯、老店招牌、地方小吃为视觉元素，延展为钥匙扣、冰箱贴和明信片。', productType: '亚克力钥匙扣/冰箱贴/明信片', audience: '年轻游客、城市礼物购买者' })

const workflowSteps = [
  { no: '1', title: '填询盘', desc: '把客户需求录进去' },
  { no: '2', title: 'AI分析/报价', desc: '自动拆成本、算毛利' },
  { no: '3', title: '发报价单', desc: '复制话术或打印给客户' },
  { no: '4', title: 'BOM/打样', desc: '客户确认后转生产准备' },
  { no: '5', title: '生产单', desc: '打样确认后排产' },
  { no: '6', title: '入库', desc: '生产完成进入库存' },
  { no: '7', title: '出库/物流', desc: '拣货后录真实快递单号' },
]

const currentLink = computed(() => selectedQuote.value?.links?.[0] || selectedInquiry.value?.links?.[0] || null)

const currentStep = computed(() => {
  if (active.value === 'cost' || active.value === 'design') return 0
  const link = currentLink.value
  if (link?.warehouseOutboundId) return 6
  if (link?.warehouseInboundId) return 5
  if (link?.productionOrderId) return 4
  if (link?.bomId || link?.sampleId) return 3
  if (selectedQuote.value?.status === 'sent') return 2
  if (selectedQuote.value?.id || active.value === 'quotes') return 1
  if (selectedInquiry.value?.id) return 0
  return 0
})

const nextAction = computed(() => {
  if (!selectedInquiry.value?.id) return { title: '下一步：先保存询盘', desc: '把客户需求录入后，系统会同步做AI需求分析。', btn: '去填写询盘', tab: 'inquiry' as const }
  if (!selectedQuote.value?.id) return { title: '下一步：根据该询盘生成报价', desc: '确认数量、材质、包装和毛利参数后，一键生成报价单。', btn: '生成报价', tab: 'inquiry' as const }
  if (selectedQuote.value.status !== 'sent') return { title: '下一步：发送/复制报价', desc: '复制客户话术，或者打印报价单给客户确认。', btn: '查看报价单', tab: 'quotes' as const }
  const link = currentLink.value
  if (!link?.bomId) return { title: '下一步：生成BOM和打样单', desc: '客户确认后把报价转成BOM、打样与后续生产准备。', btn: '生成BOM/打样', tab: 'quotes' as const }
  if (!link?.productionOrderId) return { title: '下一步：创建生产单', desc: '打样确认后，把BOM转为生产单并生成采购建议。', btn: '去创建生产单', tab: 'fulfillment' as const }
  if (!link?.warehouseInboundId) return { title: '下一步：生产完成入库', desc: '生产完成后把成品入库，库存自动增加。', btn: '去入库', tab: 'fulfillment' as const }
  if (!link?.warehouseOutboundId) return { title: '下一步：创建出库拣货', desc: '客户订单发货前生成出库单和拣货任务。', btn: '去出库', tab: 'fulfillment' as const }
  return { title: '下一步：物流跟踪', desc: '拣货完成后到物流跟踪录入真实快递单号。', btn: '查看履约进度', tab: 'fulfillment' as const }
})

async function getJson(url:string){ const r=await fetch(url); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function postJson(url:string, body:any={}){ const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function load(){
  loading.value=true
  loadingText.value='正在刷新数据...'
  try{
    const [d,c,i,q,cl]=await Promise.all([getJson('/api/mvp/dashboard'),getJson('/api/mvp/customers'),getJson('/api/mvp/inquiries'),getJson('/api/mvp/quotes'),getJson('/api/mvp/cost-library')])
    dashboard.value=d; customers.value=c; inquiries.value=i; quotes.value=q; cost.value=cl
    if(!selectedInquiry.value && i.length) selectedInquiry.value=await getJson(`/api/mvp/inquiries/${i[0].id}`)
    if(!selectedQuote.value && q.length) selectedQuote.value=await getJson(`/api/mvp/quotes/${q[0].id}`)
  }catch(e:any){ emit('alert',`加载商业MVP失败：${e.message||e}`,'error') } finally{ loading.value=false; loadingText.value='' }
}
async function createInquiry(){ loading.value=true; loadingText.value='正在调用硅基流动AI分析询盘，通常20-60秒，请稍等...'; try{ selectedInquiry.value=await postJson('/api/mvp/inquiries', inquiryForm.value); emit('alert','询盘已保存，AI已分析需求','success'); await load(); active.value='inquiry' }catch(e:any){ emit('alert',`创建询盘失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function openInquiry(id:number){ selectedInquiry.value=await getJson(`/api/mvp/inquiries/${id}`); active.value='inquiry' }
async function generateQuote(){ if(!selectedInquiry.value?.id) return; loading.value=true; loadingText.value='正在调用硅基流动AI生成报价，通常20-60秒，请勿重复点击...'; try{ selectedQuote.value=await postJson(`/api/mvp/inquiries/${selectedInquiry.value.id}/quote`, quoteOptions.value); emit('alert',`报价单已生成：${selectedQuote.value.quoteNo}`,'success'); active.value='quotes'; await load() }catch(e:any){ emit('alert',`生成报价失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function openQuote(id:number){ selectedQuote.value=await getJson(`/api/mvp/quotes/${id}`); active.value='quotes' }
async function createBomSample(){ if(!selectedQuote.value?.id) return; loading.value=true; loadingText.value='正在生成BOM和打样单...'; try{ const r=await postJson(`/api/mvp/quotes/${selectedQuote.value.id}/bom-sample`,{}); emit('alert',`${r.message}：${r.sampleNo}`,'success'); await refreshSelectedQuote(); active.value='fulfillment' }catch(e:any){ emit('alert',`生成BOM/打样失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function refreshSelectedQuote(){ const id=selectedQuote.value?.id; await load(); if(id) selectedQuote.value=await getJson(`/api/mvp/quotes/${id}`) }
async function createProduction(){ if(!selectedQuote.value?.id) return; loading.value=true; loadingText.value='正在从BOM创建生产单和采购建议...'; try{ const r=await postJson(`/api/mvp/quotes/${selectedQuote.value.id}/production`,{}); emit('alert',`${r.message}：${r.productionNo}`,'success'); await refreshSelectedQuote(); active.value='fulfillment' }catch(e:any){ emit('alert',`创建生产单失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function inboundWarehouse(){ if(!selectedQuote.value?.id) return; loading.value=true; loadingText.value='正在办理生产完工入库...'; try{ const r=await postJson(`/api/mvp/quotes/${selectedQuote.value.id}/warehouse-inbound`,{}); emit('alert',`${r.message}：${r.inboundNo}`,'success'); await refreshSelectedQuote(); active.value='fulfillment' }catch(e:any){ emit('alert',`入库失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function outboundWarehouse(){ if(!selectedQuote.value?.id) return; loading.value=true; loadingText.value='正在创建出库单和拣货任务...'; try{ const r=await postJson(`/api/mvp/quotes/${selectedQuote.value.id}/warehouse-outbound`,{}); emit('alert',`${r.message}：${r.pickNo}`,'success'); await refreshSelectedQuote(); active.value='fulfillment' }catch(e:any){ emit('alert',`出库失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function sendQuote(){ if(!selectedQuote.value?.id) return; selectedQuote.value=await postJson(`/api/mvp/quotes/${selectedQuote.value.id}/status`,{status:'sent'}); emit('alert','报价单状态已标记为已发送','success'); await load() }
async function runPlan(){ loading.value=true; loadingText.value='正在调用硅基流动AI生成设计企划，通常20-60秒...'; try{ planResult.value=await postJson('/api/mvp/design-plan',planForm.value); emit('alert','AI设计企划已生成','success') }catch(e:any){ emit('alert',`企划失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
async function runReview(){ loading.value=true; loadingText.value='正在调用硅基流动AI评审方案，通常20-60秒...'; try{ reviewResult.value=await postJson('/api/mvp/design-review',reviewForm.value); emit('alert','AI评审已完成','success') }catch(e:any){ emit('alert',`评审失败：${e.message||e}`,'error') }finally{loading.value=false; loadingText.value=''} }
function money(v:any){ return Number(v||0).toFixed(2) }
function copy(text:string){ navigator.clipboard?.writeText(text||''); emit('alert','已复制报价话术','success') }
function printQuote(){ window.print() }
function goNext(){ active.value = nextAction.value.tab }
onMounted(load)
</script>

<template>
  <div class="page mvp-page">
    <div class="page-header mvp-hero">
      <div>
        <p class="eyebrow">STEP 1 · 接单报价</p>
        <h2 class="page-title">询盘到报价工作台</h2>
        <p class="page-desc">按照「询盘 → 报价 → BOM/打样 → 生产 → 入库 → 出库 → 物流」一条线走完，适合日常接客户定制单。</p>
      </div>
      <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="workflow-card">
      <div v-for="(s, idx) in workflowSteps" :key="s.no" class="workflow-step" :class="{ done: idx < currentStep, active: idx === currentStep }">
        <span class="step-no">{{ s.no }}</span>
        <div><b>{{ s.title }}</b><small>{{ s.desc }}</small></div>
      </div>
    </div>

    <div v-if="loadingText" class="loading-banner">{{ loadingText }}</div>

    <div class="stats-row">
      <div class="stat-card"><div class="stat-label">客户</div><div class="stat-num primary">{{dashboard.customerCount??'-'}}</div></div>
      <div class="stat-card"><div class="stat-label">询盘</div><div class="stat-num success">{{dashboard.inquiryCount??'-'}}</div></div>
      <div class="stat-card"><div class="stat-label">报价单</div><div class="stat-num warning">{{dashboard.quoteCount??'-'}}</div></div>
      <div class="stat-card"><div class="stat-label">BOM/打样</div><div class="stat-num purple">{{dashboard.bomCount??'-'}} / {{dashboard.sampleCount??'-'}}</div></div>
      <div class="stat-card"><div class="stat-label">报价金额</div><div class="stat-num info">¥{{money(dashboard.quoteAmount)}}</div></div>
    </div>

    <div class="quick-help">
      <div><b>{{ nextAction.title }}</b><span>{{ nextAction.desc }}</span></div>
      <button class="mini primary-mini" @click="goNext">{{ nextAction.btn }}</button>
    </div>

    <div class="tabs">
      <button :class="{active:active==='inquiry'}" @click="active='inquiry'">1 接单报价</button>
      <button :class="{active:active==='quotes'}" @click="active='quotes'">2 报价单</button>
      <button :class="{active:active==='fulfillment'}" @click="active='fulfillment'">3 履约进度</button>
      <button :class="{active:active==='cost'}" @click="active='cost'">成本库</button>
      <button :class="{active:active==='design'}" @click="active='design'">设计企划</button>
    </div>

    <section v-if="active==='inquiry'" class="grid-2">
      <div class="panel-card">
        <div class="section-title"><span>01</span><div><h3>填写客户询盘</h3><p>只要把客户说的话、数量、材质、包装填清楚即可。</p></div></div>
        <div class="form-row"><div><label>客户名</label><input v-model="inquiryForm.customerName"></div><div><label>联系人</label><input v-model="inquiryForm.contactName"></div><div><label>电话</label><input v-model="inquiryForm.phone"></div></div>
        <label>询盘标题</label><input v-model="inquiryForm.title">
        <div class="form-row"><div><label>产品名</label><input v-model="inquiryForm.productName"></div><div><label>产品类型</label><select v-model="inquiryForm.productType"><option v-for="t in cost.productTypes" :key="t">{{t}}</option></select></div><div><label>数量</label><input v-model.number="inquiryForm.quantity" type="number"></div></div>
        <label>客户原话/核心需求</label><textarea v-model="inquiryForm.rawRequirement" rows="4"></textarea>
        <div class="form-row"><div><label>尺寸</label><input v-model="inquiryForm.size"></div><div><label>材质</label><input v-model="inquiryForm.material"></div><div><label>包装</label><input v-model="inquiryForm.packaging"></div></div>
        <div class="form-row"><div><label>收货地</label><input v-model="inquiryForm.destination"></div><div><label>预算</label><input v-model="inquiryForm.budget"></div><div><label>使用场景</label><input v-model="inquiryForm.usageScenario"></div></div>
        <button class="btn btn-primary full" :disabled="loading" @click="createInquiry">保存询盘并AI分析</button>
      </div>

      <div class="panel-card">
        <div class="section-title"><span>02</span><div><h3>选择询盘并生成报价</h3><p>点中左侧记录后，设置毛利参数即可报价。</p></div></div>
        <div class="list">
          <button v-for="i in inquiries" :key="i.id" class="list-item" :class="{active:selectedInquiry?.id===i.id}" @click="openInquiry(i.id)">
            <b>{{i.inquiryNo}} · {{i.title}}</b><span>{{i.customerName||'未绑定客户'}} · {{i.productType}} · {{i.quantity}}件 · {{i.status}}</span>
          </button>
        </div>
        <div v-if="selectedInquiry" class="detail">
          <h4>{{selectedInquiry.title}}</h4>
          <p>{{selectedInquiry.rawRequirement}}</p>
          <div class="form-row"><div><label>设计费</label><input v-model.number="quoteOptions.designFee" type="number"></div><div><label>费用率%</label><input v-model.number="quoteOptions.overheadRate" type="number"></div><div><label>目标毛利%</label><input v-model.number="quoteOptions.targetMarginRate" type="number"></div></div>
          <button class="btn btn-primary full" :disabled="loading" @click="generateQuote">根据该询盘生成报价</button>
          <div v-if="selectedInquiry.quotes?.length" class="related-quotes"><h4>关联报价</h4><button v-for="q in selectedInquiry.quotes" :key="q.id" class="quote-chip" @click="openQuote(q.id)">{{q.quoteNo}} ¥{{money(q.totalQuote)}}</button></div>
        </div>
      </div>
    </section>

    <section v-if="active==='quotes'" class="grid-2">
      <div class="panel-card"><div class="section-title"><span>03</span><div><h3>报价记录</h3><p>选择一张报价单查看明细、复制话术、打印。</p></div></div><div class="list"><button v-for="q in quotes" :key="q.id" class="list-item" :class="{active:selectedQuote?.id===q.id}" @click="openQuote(q.id)"><b>{{q.quoteNo}} · {{q.productName}}</b><span>{{q.customerName||'客户'}} · {{q.quantity}}件 · ¥{{money(q.totalQuote)}} · {{q.status}}</span></button></div></div>
      <div v-if="selectedQuote" class="panel-card quote-sheet">
        <div class="sheet-head"><div><h3>报价单 {{selectedQuote.quoteNo}}</h3><p>客户：{{selectedQuote.customerName||'-'}} {{selectedQuote.contactName||''}} {{selectedQuote.phone||''}}</p></div><div class="actions"><button class="mini" @click="copy(selectedQuote.customerReply)">发送/复制报价</button><button class="mini" @click="sendQuote">标记已发送</button><button class="mini" @click="printQuote">打印</button></div></div>
        <table><thead><tr><th>产品</th><th>类型</th><th>数量</th><th>单价</th><th>小计</th></tr></thead><tbody><tr v-for="it in selectedQuote.items" :key="it.name"><td>{{it.name}}</td><td>{{it.type}}</td><td>{{it.quantity}}</td><td>¥{{money(it.unitPrice)}}</td><td>¥{{money(it.subtotal)}}</td></tr></tbody></table>
        <div class="quote-kpis"><div><span>单件成本</span><b>¥{{money(selectedQuote.unitCost)}}</b></div><div><span>建议单价</span><b>¥{{money(selectedQuote.suggestedUnitPrice)}}</b></div><div><span>底价</span><b>¥{{money(selectedQuote.floorUnitPrice)}}</b></div><div><span>总报价</span><b>¥{{money(selectedQuote.totalQuote)}}</b></div></div>
        <p class="reply">{{selectedQuote.customerReply}}</p>
        <div class="ai-draft"><h4>AI报价建议</h4><pre>{{selectedQuote.aiDraft}}</pre></div>
        <h4>条款</h4><ul><li v-for="t in selectedQuote.terms" :key="t">{{t}}</li></ul>
        <button class="btn btn-primary full" :disabled="loading" @click="createBomSample">确认后生成BOM和打样单</button>
      </div>
    </section>


    <section v-if="active==='fulfillment'" class="panel-card fulfillment-card">
      <div class="section-title"><span>04</span><div><h3>从BOM到发货的履约进度</h3><p>这里把询盘报价后面的生产、入库、出库、物流接起来，不再停在BOM/打样。</p></div></div>
      <div v-if="!selectedQuote" class="empty-box">请先选择一张报价单。</div>
      <template v-else>
        <div class="fulfillment-flow">
          <div class="fulfill-step" :class="{done:currentLink?.bomId}"><b>BOM/打样</b><span>{{currentLink?.bomNo || '未生成BOM'}} {{currentLink?.sampleNo ? ' / '+currentLink.sampleNo : ''}}</span><button class="mini primary-mini" :disabled="loading || !!currentLink?.bomId" @click="createBomSample">生成BOM/打样</button></div>
          <div class="fulfill-step" :class="{done:currentLink?.productionOrderId}"><b>生产单</b><span>{{currentLink?.productionNo || '待创建生产单'}}</span><button class="mini primary-mini" :disabled="loading || !currentLink?.bomId || !!currentLink?.productionOrderId" @click="createProduction">创建生产单</button></div>
          <div class="fulfill-step" :class="{done:currentLink?.warehouseInboundId}"><b>生产完工入库</b><span>{{currentLink?.warehouseInboundNo || '待入库'}}</span><button class="mini primary-mini" :disabled="loading || !currentLink?.productionOrderId || !!currentLink?.warehouseInboundId" @click="inboundWarehouse">完工入库</button></div>
          <div class="fulfill-step" :class="{done:currentLink?.warehouseOutboundId}"><b>出库/拣货</b><span>{{currentLink?.warehouseOutboundNo || '待出库'}} {{currentLink?.warehouseOutboundStatus ? ' / '+currentLink.warehouseOutboundStatus : ''}}</span><button class="mini primary-mini" :disabled="loading || !currentLink?.warehouseInboundId || !!currentLink?.warehouseOutboundId" @click="outboundWarehouse">创建出库拣货</button></div>
          <div class="fulfill-step"><b>真实物流</b><span>拣货完成后，到“物流跟踪”录入真实快递单号</span><button class="mini" disabled>去物流跟踪录单号</button></div>
        </div>
        <div class="flow-note"><b>说明：</b>生产单会进入「智能成本核算引擎」，入库/出库会进入「智能仓储」，真实快递轨迹在「物流跟踪」里查。</div>
      </template>
    </section>

    <section v-if="active==='cost'" class="grid-2"><div class="panel-card"><h3>材料成本库</h3><table><thead><tr><th>材料</th><th>类别</th><th>成本</th><th>库存</th><th>供应商</th></tr></thead><tbody><tr v-for="m in cost.materials" :key="m.id"><td>{{m.name}}</td><td>{{m.category}}</td><td>¥{{money(m.standardCost)}}/{{m.unit}}</td><td>{{m.stockQty}}</td><td>{{m.supplier}}</td></tr></tbody></table></div><div class="panel-card"><h3>工艺成本库</h3><table><thead><tr><th>工艺</th><th>类别</th><th>成本</th><th>工时</th><th>损耗</th></tr></thead><tbody><tr v-for="p in cost.processes" :key="p.id"><td>{{p.name}}</td><td>{{p.category}}</td><td>¥{{money(p.standardCost)}}</td><td>{{p.standardHours}}</td><td>{{(Number(p.lossRate||0)*100).toFixed(1)}}%</td></tr></tbody></table></div></section>

    <section v-if="active==='design'" class="grid-2"><div class="panel-card"><div class="section-title"><span>辅</span><div><h3>AI设计企划</h3><p>没有完整需求时，先让AI帮你拆主题和产品矩阵。</p></div></div><label>主题</label><input v-model="planForm.theme"><label>目标人群</label><input v-model="planForm.audience"><label>产品类型</label><input v-model="planForm.productTypes"><label>预算/价格带</label><input v-model="planForm.budget"><button class="btn btn-primary full" :disabled="loading" @click="runPlan">生成设计企划</button><div v-if="planResult" class="ai-draft"><h4>{{planResult.planNo}}</h4><pre>{{planResult.aiDraft}}</pre></div></div><div class="panel-card"><div class="section-title"><span>审</span><div><h3>AI设计评审</h3><p>打样前检查卖点、成本、工艺风险。</p></div></div><label>设计方案/Prompt</label><textarea v-model="reviewForm.concept" rows="6"></textarea><label>目标SKU</label><input v-model="reviewForm.productType"><label>目标人群</label><input v-model="reviewForm.audience"><button class="btn btn-primary full" :disabled="loading" @click="runReview">AI评审是否可打样</button><div v-if="reviewResult" class="ai-draft"><h4>{{reviewResult.reviewNo}}</h4><pre>{{reviewResult.aiDraft}}</pre></div></div></section>
  </div>
</template>

<style scoped>
.mvp-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.mvp-hero{padding:28px;border-radius:20px;color:#fff;background:linear-gradient(135deg,#111827,#7c2d12 45%,#0f766e);box-shadow:var(--shadow-md)}.mvp-hero .page-title{color:#fff;font-size:28px}.mvp-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#fed7aa;font-weight:800}.workflow-card{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:18px 0}.workflow-step{display:flex;gap:12px;align-items:center;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:14px;box-shadow:var(--shadow-sm)}.workflow-step.done{border-color:#99f6e4;background:#f0fdfa}.workflow-step.active{border-color:#fb923c;background:#fff7ed}.step-no{width:34px;height:34px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#e2e8f0;color:#334155;font-weight:900}.workflow-step.done .step-no{background:#0f766e;color:#fff}.workflow-step.active .step-no{background:#f97316;color:#fff}.workflow-step b{display:block;color:#0f172a}.workflow-step small{display:block;color:#64748b;margin-top:3px}.loading-banner{margin:14px 0;padding:12px 16px;border-radius:14px;background:#fff7ed;border:1px solid #fed7aa;color:#9a3412;font-weight:800}.quick-help{display:flex;justify-content:space-between;align-items:center;gap:14px;margin:16px 0;padding:14px 16px;border-radius:16px;background:#ecfeff;border:1px solid #a5f3fc}.quick-help b{display:block;color:#155e75}.quick-help span{display:block;color:#475569;margin-top:4px}.tabs{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.tabs button{border:1px solid var(--c-border);background:#fff;border-radius:999px;padding:10px 16px;cursor:pointer;font-weight:800;color:#475569}.tabs button.active{background:#0f766e;color:#fff;border-color:#0f766e}.grid-2{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm);overflow:auto}.panel-card h3{margin:0 0 4px}.section-title{display:flex;gap:12px;align-items:flex-start;margin-bottom:14px}.section-title>span{background:#0f766e;color:#fff;border-radius:12px;min-width:40px;height:32px;display:flex;align-items:center;justify-content:center;font-weight:900}.section-title p{margin:0;color:#64748b;font-size:13px}.panel-card label{display:block;font-size:12px;font-weight:800;color:#64748b;margin:10px 0 6px}input,textarea,select{width:100%;border:1px solid var(--c-border);border-radius:10px;padding:10px;background:#fff;font-family:inherit}input,select{height:40px}.form-row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.full{width:100%;margin-top:16px}.panel-head,.sheet-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.panel-head p,.sheet-head p{margin:0;color:#64748b}.list{display:flex;flex-direction:column;gap:10px;max-height:360px;overflow:auto}.list-item{text-align:left;border:1px solid #eef2f7;background:#fff;border-radius:12px;padding:12px;cursor:pointer;display:flex;flex-direction:column;gap:5px}.list-item.active{border-color:#0f766e;background:#f0fdfa}.list-item span{font-size:13px;color:#64748b}.detail{margin-top:16px;border-top:1px solid #eef2f7;padding-top:16px}.detail p{color:#475569;line-height:1.7}.related-quotes{margin-top:12px}.quote-chip{margin:0 8px 8px 0;border:1px solid #fed7aa;background:#fff7ed;color:#9a3412;border-radius:999px;padding:7px 10px;cursor:pointer}.mini{border:0;background:#f1f5f9;border-radius:999px;padding:8px 12px;cursor:pointer;font-weight:700}.primary-mini{background:#0f766e;color:#fff}.actions{display:flex;gap:8px;flex-wrap:wrap}.quote-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin:14px 0}.quote-kpis div{background:#fff7ed;border:1px solid #fed7aa;border-radius:14px;padding:12px}.quote-kpis span{display:block;color:#9a3412;font-size:12px;margin-bottom:6px}.quote-kpis b{font-size:20px}.reply{background:#f0fdfa;border:1px solid #99f6e4;border-radius:14px;padding:14px;line-height:1.8;color:#334155}.ai-draft{margin-top:14px;border:1px solid #c4b5fd;background:#faf5ff;border-radius:14px;padding:14px}.ai-draft h4{margin:0 0 10px;color:#6d28d9}.ai-draft pre{white-space:pre-wrap;word-break:break-word;line-height:1.7;margin:0;color:#334155;font-family:inherit}table{width:100%;border-collapse:collapse;margin-top:12px}th,td{padding:10px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px}th{background:#f8fafc;color:#64748b}ul{padding-left:20px}li{margin:6px 0;line-height:1.6}.fulfillment-card{margin-bottom:18px}.fulfillment-flow{display:grid;grid-template-columns:repeat(5,1fr);gap:12px}.fulfill-step{border:1px solid #e2e8f0;border-radius:16px;padding:14px;background:#fff;display:flex;flex-direction:column;gap:8px;min-height:138px}.fulfill-step.done{border-color:#99f6e4;background:#f0fdfa}.fulfill-step b{color:#0f172a}.fulfill-step span{color:#64748b;font-size:13px;line-height:1.5;flex:1}.flow-note{margin-top:14px;background:#fff7ed;border:1px solid #fed7aa;color:#9a3412;border-radius:14px;padding:12px}.empty-box{padding:22px;border:1px dashed #cbd5e1;border-radius:14px;color:#64748b;text-align:center}@media(max-width:1100px){.grid-2,.workflow-card,.fulfillment-flow{grid-template-columns:1fr}.quote-kpis,.form-row{grid-template-columns:1fr 1fr}.quick-help{align-items:flex-start;flex-direction:column}}@media print{.sidebar,.app-header,.tabs,.stats-row,.actions,.btn,.mini,.workflow-card,.quick-help{display:none!important}.app-body{margin-left:0!important}.quote-sheet{box-shadow:none;border:0}.grid-2{display:block}}
</style>
