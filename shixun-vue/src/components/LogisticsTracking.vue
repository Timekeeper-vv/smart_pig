<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const loading = ref(false)
const syncingId = ref<number|null>(null)
const dashboard = ref<any>({})
const providerStatus = ref<any>({})
const carriers = ref<any[]>([])
const orders = ref<any[]>([])
const shipments = ref<any[]>([])
const selected = ref<any>(null)
const form = ref<any>({
  orderId: '', orderNo: '', receiverName: '张先生', receiverPhone: '13800001111', receiverAddress: '上海市浦东新区文创园区88号', carrierCode: 'shunfeng', trackingNo: ''
})

const selectedOrder = computed(() => orders.value.find(o => Number(o.id) === Number(form.value.orderId)))
const exceptionList = computed(() => shipments.value.filter(s => s.status === 'exception' || s.alertLevel === 'warning' || s.alertLevel === 'exception'))

async function getJson(url:string){ const r=await fetch(url); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function postJson(url:string, body:any={}){ const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function load(){ loading.value=true; try{ const [d,ps,c,o,s]=await Promise.all([getJson('/api/logistics/dashboard'),getJson('/api/logistics/provider-status'),getJson('/api/logistics/carriers'),getJson('/api/logistics/orders'),getJson('/api/logistics/shipments')]); dashboard.value=d; providerStatus.value=ps; carriers.value=c; orders.value=o; shipments.value=s; if(!selected.value && s.length) selected.value=await getJson(`/api/logistics/shipments/${s[0].id}`) }catch(e:any){ emit('alert',`加载物流失败：${e.message||e}`,'error') }finally{ loading.value=false } }
async function createShipment(){ loading.value=true; try{ const body={...form.value, orderId: form.value.orderId ? Number(form.value.orderId) : null}; selected.value=await postJson('/api/logistics/shipments',body); emit('alert','真实发货单已创建，请点击同步物流查询快递100真实轨迹','success'); form.value.trackingNo=''; await load() }catch(e:any){ emit('alert',`创建发货单失败：${e.message||e}`,'error') }finally{loading.value=false} }
async function openShipment(id:number){ selected.value=await getJson(`/api/logistics/shipments/${id}`) }
async function syncShipment(id:number){ syncingId.value=id; try{ selected.value=await postJson(`/api/logistics/shipments/${id}/sync`); emit('alert','物流轨迹已同步','success'); await load(); selected.value=await getJson(`/api/logistics/shipments/${id}`) }catch(e:any){ emit('alert',`同步失败：${e.message||e}`,'error') }finally{ syncingId.value=null } }
async function subscribeShipment(id:number){ syncingId.value=id; try{ selected.value=await postJson(`/api/logistics/shipments/${id}/subscribe`); emit('alert','已提交快递100真实订阅','success'); await load(); selected.value=await getJson(`/api/logistics/shipments/${id}`) }catch(e:any){ emit('alert',`订阅失败：${e.message||e}`,'error') }finally{ syncingId.value=null } }
async function markException(id:number){ syncingId.value=id; try{ selected.value=await postJson(`/api/logistics/shipments/${id}/exception`,{reason:'超过预期时间未更新，客服需联系承运商核实'}); emit('alert','已标记为物流异常','success'); await load(); selected.value=await getJson(`/api/logistics/shipments/${id}`) }catch(e:any){ emit('alert',`标记失败：${e.message||e}`,'error') }finally{ syncingId.value=null } }
function fillFromOrder(){ const o=selectedOrder.value; if(!o) return; form.value.orderNo=o.orderNo; form.value.receiverName=o.buyerName || '客户'; form.value.receiverPhone=o.buyerPhone || '13800001111' }
function statusText(s:string){ return ({shipped:'已发货',in_transit:'运输中',delivering:'派送中',signed:'已签收',exception:'异常'} as any)[s] || s }
function statusClass(s:string){ return s || 'shipped' }
function fmt(v:any){ return v ? String(v).replace('T',' ').slice(0,19) : '-' }
onMounted(load)
</script>
<template>
  <div class="page logistics-page">
    <div class="page-header logistics-hero"><div><p class="eyebrow">LOGISTICS TRACKING</p><h2 class="page-title">物流跟踪</h2><p class="page-desc">真实物流模式：录入真实快递公司和真实单号，调用快递100真实API查询轨迹；可配置公网回调后启用订阅推送。</p></div><button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button></div>
    <div class="stats-row"><div class="stat-card"><div class="stat-label">发货单</div><div class="stat-num primary">{{dashboard.shipmentCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">运输中</div><div class="stat-num success">{{dashboard.inTransitCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">已签收</div><div class="stat-num warning">{{dashboard.signedCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">异常提醒</div><div class="stat-num purple">{{dashboard.exceptionCount??'-'}}</div></div></div>
    <div class="guide-card"><b>真实流程</b><span>先在顺丰/中通等后台或线下拿到真实快递单号 → 录入系统 → 点击同步物流调用快递100 → 配置公网回调后可订阅自动更新</span></div>
    <div class="api-card" :class="{ok:providerStatus.queryConfigured}"><b>快递100真实API</b><span>{{providerStatus.message}}<template v-if="providerStatus.subscribeConfigured">；订阅回调已配置</template><template v-else>；订阅推送未配置公网callback-url</template></span></div>
    <section class="grid-2">
      <div class="panel-card">
        <div class="section-title"><span>发</span><div><h3>录入真实发货单</h3><p>这里不生成假单号，必须填写承运商真实面单号。</p></div></div>
        <label>关联订单</label><select v-model="form.orderId" @change="fillFromOrder"><option value="">不关联订单/手工发货</option><option v-for="o in orders" :key="o.id" :value="o.id">{{o.orderNo}} · {{o.buyerName||'客户'}} · {{o.orderStatus}}</option></select>
        <div class="form-row"><div><label>订单号</label><input v-model="form.orderNo" placeholder="可选"></div><div><label>收货人</label><input v-model="form.receiverName"></div></div>
        <label>手机号</label><input v-model="form.receiverPhone">
        <label>收货地址</label><input v-model="form.receiverAddress">
        <div class="form-row"><div><label>快递公司</label><select v-model="form.carrierCode"><option v-for="c in carriers" :key="c.code" :value="c.code">{{c.name}}</option></select></div><div><label>快递单号</label><input v-model="form.trackingNo"></div></div>
        <button class="btn btn-primary full" :disabled="loading || !form.trackingNo" @click="createShipment">创建真实发货单</button>
        <div v-if="exceptionList.length" class="exception-box"><b>异常件提醒</b><button v-for="e in exceptionList" :key="e.id" @click="openShipment(e.id)">{{e.orderNo||e.trackingNo}} · {{e.latestTrace}}</button></div>
      </div>
      <div class="panel-card">
        <div class="section-title"><span>查</span><div><h3>发货单列表</h3><p>点击任意发货单查看时间线。</p></div></div>
        <div class="list"><button v-for="s in shipments" :key="s.id" class="list-item" :class="{active:selected?.id===s.id}" @click="openShipment(s.id)"><div><b>{{s.shipmentNo}} · {{s.carrierName}}</b><em :class="statusClass(s.status)">{{statusText(s.status)}}</em></div><span>{{s.orderNo||'无订单'}} · {{s.trackingNo}}</span><small>{{s.latestTrace}}</small></button></div>
      </div>
    </section>
    <section v-if="selected" class="panel-card detail-card">
      <div class="detail-head"><div><h3>{{selected.carrierName}} · {{selected.trackingNo}}</h3><p>{{selected.orderNo||'无关联订单'}} · {{selected.receiverName}} · {{selected.receiverPhone}} · {{selected.receiverAddress}}</p></div><div class="actions"><button class="mini" :disabled="syncingId===selected.id" @click="syncShipment(selected.id)">{{syncingId===selected.id?'查询中...':'快递100真实查询'}}</button><button class="mini" :disabled="syncingId===selected.id" @click="subscribeShipment(selected.id)">订阅推送</button><button class="mini danger" @click="markException(selected.id)">标记异常</button></div></div>
      <div class="status-tip"><b>{{statusText(selected.status)}}</b><span>{{selected.suggestion}}</span></div>
      <div class="timeline"><div v-for="t in selected.traces" :key="t.id" class="trace"><i :class="statusClass(t.status)"></i><div><b>{{t.content}}</b><span>{{fmt(t.traceTime)}} · {{t.location}} · {{statusText(t.status)}}</span></div></div></div>
    </section>
  </div>
</template>
<style scoped>
.logistics-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.logistics-hero{padding:28px;border-radius:20px;color:#fff;background:linear-gradient(135deg,#111827,#0369a1 45%,#0f766e);box-shadow:var(--shadow-md)}.logistics-hero .page-title{color:#fff;font-size:28px}.logistics-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#bae6fd;font-weight:800}.guide-card{margin:16px 0 10px;padding:14px 16px;background:#ecfeff;border:1px solid #a5f3fc;border-radius:16px}.guide-card b{color:#155e75;margin-right:10px}.guide-card span{color:#475569}.api-card{margin:0 0 16px;padding:12px 16px;background:#fff7ed;border:1px solid #fed7aa;border-radius:16px}.api-card.ok{background:#f0fdf4;border-color:#bbf7d0}.api-card b{color:#9a3412;margin-right:10px}.api-card.ok b{color:#166534}.api-card span{color:#475569}.grid-2{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm)}.section-title{display:flex;gap:12px;align-items:flex-start;margin-bottom:14px}.section-title>span{background:#0369a1;color:#fff;border-radius:12px;min-width:38px;height:32px;display:flex;align-items:center;justify-content:center;font-weight:900}.section-title h3{margin:0 0 4px}.section-title p{margin:0;color:#64748b;font-size:13px}.panel-card label{display:block;font-size:12px;font-weight:800;color:#64748b;margin:10px 0 6px}input,select{width:100%;height:40px;border:1px solid var(--c-border);border-radius:10px;padding:0 10px;background:#fff}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:10px}.full{width:100%;margin-top:16px}.list{display:flex;flex-direction:column;gap:10px;max-height:420px;overflow:auto}.list-item{text-align:left;border:1px solid #eef2f7;background:#fff;border-radius:12px;padding:12px;cursor:pointer;display:flex;flex-direction:column;gap:6px}.list-item.active{border-color:#0369a1;background:#f0f9ff}.list-item div{display:flex;justify-content:space-between;gap:10px}.list-item span,.list-item small{font-size:13px;color:#64748b}.list-item em,.detail-head em{font-style:normal;border-radius:999px;padding:3px 8px;font-size:12px;font-weight:900}.shipped{background:#dbeafe;color:#1d4ed8}.in_transit{background:#e0f2fe;color:#0369a1}.delivering{background:#fff7ed;color:#c2410c}.signed{background:#dcfce7;color:#166534}.exception{background:#fee2e2;color:#991b1b}.exception-box{margin-top:16px;border:1px solid #fecaca;background:#fef2f2;border-radius:14px;padding:12px;display:flex;flex-direction:column;gap:8px}.exception-box b{color:#991b1b}.exception-box button{text-align:left;border:0;background:#fff;border-radius:10px;padding:9px;color:#991b1b;cursor:pointer}.detail-card{margin-top:18px}.detail-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;border-bottom:1px solid #eef2f7;padding-bottom:14px}.detail-head h3{margin:0 0 6px}.detail-head p{margin:0;color:#64748b}.actions{display:flex;gap:8px}.mini{border:0;background:#f1f5f9;border-radius:999px;padding:9px 12px;cursor:pointer;font-weight:800}.mini.danger{background:#fee2e2;color:#991b1b}.status-tip{margin:14px 0;padding:14px;border-radius:14px;background:#f0fdfa;border:1px solid #99f6e4}.status-tip b{display:block;color:#0f766e;margin-bottom:4px}.status-tip span{color:#475569}.timeline{display:flex;flex-direction:column;gap:0}.trace{display:flex;gap:12px;position:relative;padding:0 0 18px}.trace:not(:last-child)::after{content:'';position:absolute;left:8px;top:20px;bottom:0;width:2px;background:#e2e8f0}.trace i{width:18px;height:18px;border-radius:50%;margin-top:2px;z-index:1;flex-shrink:0}.trace b{display:block;color:#0f172a}.trace span{display:block;color:#64748b;font-size:13px;margin-top:5px}@media(max-width:1100px){.grid-2,.form-row{grid-template-columns:1fr}.detail-head{flex-direction:column}}
</style>
