<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const active = ref<'projects'|'saas'|'templates'|'usage'>('projects')
const loading = ref(false)
const loadingText = ref('')
const dashboard = ref<any>({})
const tenants = ref<any[]>([])
const plans = ref<any[]>([])
const usage = ref<any[]>([])
const templates = ref<any[]>([])
const projects = ref<any[]>([])
const selectedProject = ref<any>(null)
const projectForm = ref({ tenantId: 1, name: '山城街巷文创开发项目', theme: '山城街巷与城市味道', targetAudience: '年轻游客、景区伴手礼客户、企业活动客户', productTypes: '钥匙扣,冰箱贴,明信片,贴纸包', budget: '29-99元' })
const tenantForm = ref({ name: '新文创商家', industry: '文创定制', contactName: '负责人', phone: '13800000000', planId: 1 })

const projectFlow = [
  { no: '1', title: '建项目', desc: '明确主题、人群、价格带' },
  { no: '2', title: 'AI企划', desc: '生成定位、卖点、产品方向' },
  { no: '3', title: 'SKU矩阵', desc: '拆成可卖的单品组合' },
  { no: '4', title: 'AI评审', desc: '检查成本、工艺、风险' },
  { no: '5', title: 'BOM/打样', desc: '转打样和物料清单' },
  { no: '6', title: '生产单', desc: '确认后进入生产履约' },
]

const projectStep = computed(() => {
  const p = selectedProject.value
  if (!p?.id) return 0
  if (p.skus?.some((s:any) => s.productionId || s.status === 'production')) return 5
  if (p.skus?.some((s:any) => s.bomId || s.sampleId || s.status === 'sample')) return 4
  if (p.aiReview) return 3
  if (p.skus?.length) return 2
  if (p.aiPlan) return 1
  return 0
})

const projectHint = computed(() => {
  if (!selectedProject.value?.id) return '先在左侧创建一个文创项目。'
  if (!selectedProject.value.aiPlan) return '当前该做：点击「AI项目企划」，让AI把主题、人群、卖点和产品方向整理出来。'
  if (!selectedProject.value.skus?.length) return '当前该做：点击「生成SKU矩阵」，把一个主题拆成多个可报价/可生产的单品。'
  if (!selectedProject.value.aiReview) return '当前该做：点击「AI评审」，检查SKU的成本、工艺、打样风险。'
  if (!selectedProject.value.skus?.some((s:any)=>s.bomId || s.sampleId)) return '当前该做：选择优先SKU，点击「生成BOM/打样」。'
  return '当前该做：打样确认后，给成熟SKU创建生产单。'
})

async function getJson(url:string){ const r=await fetch(url); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function postJson(url:string, body:any={}){ const r=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)}); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function load(){ loading.value=true; loadingText.value='正在刷新数据...'; try{ const [d,t,p,u,tp,pr]=await Promise.all([getJson('/api/scale/dashboard'),getJson('/api/scale/tenants'),getJson('/api/scale/plans'),getJson('/api/scale/usage'),getJson('/api/scale/templates'),getJson('/api/scale/projects')]); dashboard.value=d; tenants.value=t; plans.value=p; usage.value=u; templates.value=tp; projects.value=pr; if(!selectedProject.value&&pr.length) selectedProject.value=await getJson(`/api/scale/projects/${pr[0].id}`)}catch(e:any){emit('alert',`加载项目/SaaS失败：${e.message||e}`,'error')}finally{loading.value=false; loadingText.value=''} }
async function createProject(){ loading.value=true; loadingText.value='正在创建项目...'; try{ selectedProject.value=await postJson('/api/scale/projects',projectForm.value); emit('alert','项目已创建','success'); await load() }catch(e:any){emit('alert',`创建项目失败：${e.message||e}`,'error')}finally{loading.value=false; loadingText.value=''} }
async function openProject(id:number){ selectedProject.value=await getJson(`/api/scale/projects/${id}`); active.value='projects' }
async function runStep(step:string){ if(!selectedProject.value?.id) return; loading.value=true; loadingText.value= step==='skus' ? '正在生成SKU矩阵，请稍等...' : '正在调用硅基流动AI，通常20-60秒，请勿重复点击...'; try{ if(step==='plan') selectedProject.value=await postJson(`/api/scale/projects/${selectedProject.value.id}/ai-plan`); if(step==='skus') selectedProject.value=await postJson(`/api/scale/projects/${selectedProject.value.id}/generate-skus`); if(step==='review') selectedProject.value=await postJson(`/api/scale/projects/${selectedProject.value.id}/ai-review`); emit('alert','流程步骤已完成','success'); await load() }catch(e:any){emit('alert',`执行失败：${e.message||e}`,'error')}finally{loading.value=false;loadingText.value=''} }
async function skuBomSample(sku:any){ loading.value=true; loadingText.value='正在生成BOM/打样单...'; try{ const r=await postJson(`/api/scale/project-skus/${sku.id}/bom-sample`); emit('alert',`已生成BOM/打样：${r.sampleNo}`,'success'); selectedProject.value=await getJson(`/api/scale/projects/${selectedProject.value.id}`) }catch(e:any){emit('alert',`生成失败：${e.message||e}`,'error')}finally{loading.value=false; loadingText.value=''} }
async function skuProduction(sku:any){ loading.value=true; loadingText.value='正在创建生产单...'; try{ const r=await postJson(`/api/scale/project-skus/${sku.id}/production`); emit('alert',`已创建生产单：${r.productionNo}`,'success'); selectedProject.value=await getJson(`/api/scale/projects/${selectedProject.value.id}`) }catch(e:any){emit('alert',`创建生产失败：${e.message||e}`,'error')}finally{loading.value=false; loadingText.value=''} }
async function createTenant(){ loading.value=true; loadingText.value='正在开通商家试用...'; try{ await postJson('/api/scale/tenants',tenantForm.value); emit('alert','租户试用已开通','success'); await load() }catch(e:any){emit('alert',`开通失败：${e.message||e}`,'error')}finally{loading.value=false; loadingText.value=''} }
async function useTemplate(t:any){ await postJson(`/api/scale/templates/${t.id}/use`,{tenantId:1}); emit('alert','模板已应用并计入用量','success'); await load() }
function money(v:any){return Number(v||0).toFixed(2)}
function skuStatus(s:any){ if(s.productionId || s.status==='production') return '已生产'; if(s.bomId || s.sampleId || s.status==='sample') return '已打样'; if(s.status==='reviewed') return '已评审'; return '待推进' }
function skuStatusClass(s:any){ if(s.productionId || s.status==='production') return 'production'; if(s.bomId || s.sampleId || s.status==='sample') return 'sample'; if(s.status==='reviewed') return 'reviewed'; return 'planned' }
onMounted(load)
</script>
<template>
  <div class="page scale-page">
    <div class="page-header scale-hero">
      <div>
        <p class="eyebrow">STEP 2 + STEP 3 · 设计生产与商业化</p>
        <h2 class="page-title">生产管理</h2>
        <p class="page-desc">把一个文创主题做成“可企划、可拆SKU、可评审、可打样、可生产”的项目；同时保留商家套餐、模板市场和用量统计。</p>
      </div>
      <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="stats-row"><div class="stat-card"><div class="stat-label">商家/租户</div><div class="stat-num primary">{{dashboard.tenantCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">订阅</div><div class="stat-num success">{{dashboard.activeSubscriptionCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">项目/SKU</div><div class="stat-num warning">{{dashboard.projectCount??'-'}} / {{dashboard.projectSkuCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">模板</div><div class="stat-num purple">{{dashboard.templateCount??'-'}}</div></div><div class="stat-card"><div class="stat-label">MRR</div><div class="stat-num info">¥{{money(dashboard.mrr)}}</div></div></div>
    <div v-if="loadingText" class="loading-banner">{{loadingText}}</div>

    <div class="mode-tabs">
      <button :class="{active:active==='projects'}" @click="active='projects'"><b>生产项目</b><span>建项目 → SKU → 打样生产</span></button>
      <button :class="{active:active==='saas'||active==='templates'||active==='usage'}" @click="active='saas'"><b>SaaS运营后台</b><span>商家套餐 → 模板 → 用量</span></button>
    </div>
    <div v-if="active==='saas'||active==='templates'||active==='usage'" class="sub-tabs"><button :class="{active:active==='saas'}" @click="active='saas'">商家/套餐</button><button :class="{active:active==='templates'}" @click="active='templates'">模板市场</button><button :class="{active:active==='usage'}" @click="active='usage'">用量统计</button></div>

    <template v-if="active==='projects'">
      <div class="workflow-card">
        <div v-for="(s, idx) in projectFlow" :key="s.no" class="workflow-step" :class="{ done: idx < projectStep, active: idx === projectStep }"><span class="step-no">{{s.no}}</span><div><b>{{s.title}}</b><small>{{s.desc}}</small></div></div>
      </div>
      <div class="hint-card"><b>操作提示</b><span>{{ projectHint }}</span></div>
      <section class="grid-2">
        <div class="panel-card">
          <div class="section-title"><span>建</span><div><h3>创建文创项目</h3><p>适合“一个主题要开发一组产品”的场景。</p></div></div>
          <label>所属商家</label><select v-model.number="projectForm.tenantId"><option v-for="t in tenants" :key="t.id" :value="t.id">{{t.name}}</option></select>
          <label>项目名</label><input v-model="projectForm.name"><label>主题</label><input v-model="projectForm.theme"><label>目标人群</label><input v-model="projectForm.targetAudience"><label>产品矩阵</label><input v-model="projectForm.productTypes"><label>预算/价格带</label><input v-model="projectForm.budget">
          <button class="btn btn-primary full" :disabled="loading" @click="createProject">创建项目</button>
          <h3 class="list-title">项目列表</h3>
          <div class="list"><button v-for="p in projects" :key="p.id" class="list-item" :class="{active:selectedProject?.id===p.id}" @click="openProject(p.id)"><b>{{p.projectNo}} · {{p.name}}</b><span>{{p.tenantName}} · {{p.status}} · {{p.productTypes}}</span></button></div>
        </div>

        <div v-if="selectedProject" class="panel-card">
          <div class="panel-head"><div><h3>{{selectedProject.name}}</h3><p>{{selectedProject.projectNo}} · {{selectedProject.status}}</p></div></div>
          <div class="action-flow"><button :disabled="loading" @click="runStep('plan')">1 生成AI企划</button><button :disabled="loading" @click="runStep('skus')">2 生成SKU矩阵</button><button :disabled="loading" @click="runStep('review')">3 AI评审</button></div>
          <div v-if="selectedProject.aiPlan" class="ai-draft"><h4>AI项目企划</h4><pre>{{selectedProject.aiPlan}}</pre></div>
          <div v-if="selectedProject.aiReview" class="ai-draft"><h4>AI评审</h4><pre>{{selectedProject.aiReview}}</pre></div>
          <div class="sku-head"><h4>项目SKU</h4><span>优先选择评分高、工艺风险低的SKU打样</span></div>
          <div v-if="!selectedProject.skus?.length" class="empty-box">还没有SKU，先点击“生成SKU矩阵”。</div>
          <div class="sku-card" v-for="s in selectedProject.skus" :key="s.id">
            <div class="sku-info"><div><b>{{s.skuName}}</b><em class="status" :class="skuStatusClass(s)">{{skuStatus(s)}}</em></div><span>{{s.productType}} · 目标价 ¥{{money(s.targetPrice)}} · {{s.plannedQty}}件</span><small>{{s.sellingPoint}}</small></div>
            <div class="sku-actions"><button class="mini" :disabled="loading" @click="skuBomSample(s)">生成BOM/打样</button><button class="mini" :disabled="loading || !s.bomId" @click="skuProduction(s)">创建生产单</button></div>
          </div>
        </div>
      </section>
    </template>

    <section v-if="active==='saas'" class="grid-2"><div class="panel-card"><div class="section-title"><span>S</span><div><h3>开通商家试用</h3><p>以后可以给外部文创商家使用，形成SaaS收费。</p></div></div><label>商家名称</label><input v-model="tenantForm.name"><label>行业</label><input v-model="tenantForm.industry"><label>联系人</label><input v-model="tenantForm.contactName"><label>电话</label><input v-model="tenantForm.phone"><label>套餐</label><select v-model.number="tenantForm.planId"><option v-for="p in plans" :value="p.id" :key="p.id">{{p.name}} ¥{{money(p.monthlyPrice)}}/月</option></select><button class="btn btn-primary full" :disabled="loading" @click="createTenant">开通14天试用</button></div><div class="panel-card"><h3>套餐与商家</h3><div class="plan" v-for="p in plans" :key="p.id"><b>{{p.name}} ¥{{money(p.monthlyPrice)}}/月</b><span>用户{{p.userLimit}} · 询盘{{p.inquiryLimit}} · AI{{p.aiQuota}} · 项目{{p.projectLimit}}</span></div><div class="tenant" v-for="t in tenants" :key="t.id"><b>{{t.name}}</b><span>{{t.planName}} · {{t.subscriptionStatus}} · {{t.industry}}</span></div></div></section>
    <section v-if="active==='templates'" class="template-grid"><div class="tpl" v-for="t in templates" :key="t.id"><b>{{t.title}}</b><span>{{t.category}} · {{t.productType}} · 使用{{t.usageCount}}</span><p>{{t.description}}</p><button class="mini" @click="useTemplate(t)">应用模板</button></div></section>
    <section v-if="active==='usage'" class="panel-card"><h3>用量统计</h3><table><thead><tr><th>商家</th><th>月份</th><th>指标</th><th>用量</th><th>更新时间</th></tr></thead><tbody><tr v-for="u in usage" :key="u.id"><td>{{u.tenantName}}</td><td>{{u.usageMonth}}</td><td>{{u.metric}}</td><td>{{u.usedCount}}</td><td>{{u.updatedAt}}</td></tr></tbody></table></section>
  </div>
</template>
<style scoped>
.scale-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.scale-hero{padding:28px;border-radius:20px;color:#fff;background:linear-gradient(135deg,#111827,#4c1d95 45%,#0f766e);box-shadow:var(--shadow-md)}.scale-hero .page-title{color:#fff;font-size:28px}.scale-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#ddd6fe;font-weight:800}.loading-banner{margin:14px 0;padding:12px 16px;border-radius:14px;background:#fff7ed;border:1px solid #fed7aa;color:#9a3412;font-weight:800}.mode-tabs{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin:18px 0}.mode-tabs button{text-align:left;background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:16px 18px;cursor:pointer;box-shadow:var(--shadow-sm)}.mode-tabs button.active{border-color:#4c1d95;background:#faf5ff}.mode-tabs b{display:block;color:#111827;font-size:16px}.mode-tabs span{display:block;color:#64748b;margin-top:6px}.sub-tabs{display:flex;gap:10px;margin:0 0 18px}.sub-tabs button{border:1px solid var(--c-border);background:#fff;border-radius:999px;padding:9px 14px;cursor:pointer;font-weight:800;color:#475569}.sub-tabs button.active{background:#4c1d95;color:#fff;border-color:#4c1d95}.workflow-card{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin:18px 0}.workflow-step{display:flex;gap:10px;align-items:center;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:12px;box-shadow:var(--shadow-sm)}.workflow-step.done{border-color:#c4b5fd;background:#faf5ff}.workflow-step.active{border-color:#14b8a6;background:#f0fdfa}.step-no{width:30px;height:30px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#e2e8f0;color:#334155;font-weight:900;flex-shrink:0}.workflow-step.done .step-no{background:#4c1d95;color:#fff}.workflow-step.active .step-no{background:#0f766e;color:#fff}.workflow-step b{display:block;font-size:13px;color:#0f172a}.workflow-step small{display:block;font-size:11px;color:#64748b;margin-top:2px}.hint-card{margin-bottom:18px;padding:14px 16px;background:#ecfeff;border:1px solid #a5f3fc;border-radius:16px}.hint-card b{color:#155e75;margin-right:10px}.hint-card span{color:#475569}.grid-2{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm);overflow:auto}.panel-card h3{margin:0 0 4px}.section-title{display:flex;gap:12px;align-items:flex-start;margin-bottom:14px}.section-title>span{background:#4c1d95;color:#fff;border-radius:12px;min-width:38px;height:32px;display:flex;align-items:center;justify-content:center;font-weight:900}.section-title p{margin:0;color:#64748b;font-size:13px}.panel-card label{display:block;font-size:12px;font-weight:800;color:#64748b;margin:10px 0 6px}input,select{width:100%;height:40px;border:1px solid var(--c-border);border-radius:10px;padding:0 10px;background:#fff}.full{width:100%;margin-top:16px}.list-title{margin-top:20px!important}.list{display:flex;flex-direction:column;gap:10px;max-height:320px;overflow:auto}.list-item{text-align:left;border:1px solid #eef2f7;background:#fff;border-radius:12px;padding:12px;cursor:pointer;display:flex;flex-direction:column;gap:5px}.list-item.active{border-color:#4c1d95;background:#faf5ff}.list-item span,.tenant span,.plan span,.tpl span,.sku-card span,.sku-card small{font-size:13px;color:#64748b}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start}.panel-head p{margin:0;color:#64748b}.action-flow{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:14px 0}.action-flow button,.mini{border:0;background:#f1f5f9;border-radius:999px;padding:9px 12px;cursor:pointer;font-weight:800}.action-flow button:hover,.mini:hover{background:#e2e8f0}.ai-draft{margin-top:14px;border:1px solid #c4b5fd;background:#faf5ff;border-radius:14px;padding:14px;max-height:360px;overflow:auto}.ai-draft h4{margin:0 0 10px;color:#6d28d9}.ai-draft pre{white-space:pre-wrap;word-break:break-word;line-height:1.7;margin:0;color:#334155;font-family:inherit}.sku-head{display:flex;justify-content:space-between;align-items:center;margin:18px 0 10px}.sku-head h4{margin:0}.sku-head span{color:#64748b;font-size:13px}.empty-box{padding:18px;border:1px dashed #cbd5e1;border-radius:14px;color:#64748b;text-align:center}.sku-card,.tenant,.plan{padding:12px;border:1px solid #eef2f7;border-radius:12px;margin-bottom:10px;display:flex;justify-content:space-between;gap:10px}.sku-info{display:flex;flex-direction:column;gap:5px;min-width:0}.sku-info>div{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.sku-actions{display:flex;gap:8px;align-items:center;flex-shrink:0}.status{font-style:normal;border-radius:999px;padding:3px 8px;font-size:12px;font-weight:800}.status.planned{background:#f1f5f9;color:#475569}.status.reviewed{background:#eff6ff;color:#1d4ed8}.status.sample{background:#fff7ed;color:#c2410c}.status.production{background:#f0fdf4;color:#15803d}.tenant,.plan{flex-direction:column}.template-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.tpl{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:18px;box-shadow:var(--shadow-sm);display:flex;flex-direction:column;gap:8px}.tpl p{color:#475569;line-height:1.6}table{width:100%;border-collapse:collapse}th,td{padding:10px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px}th{background:#f8fafc;color:#64748b}@media(max-width:1200px){.workflow-card{grid-template-columns:repeat(3,1fr)}}@media(max-width:1100px){.grid-2,.template-grid,.mode-tabs{grid-template-columns:1fr}.action-flow{grid-template-columns:1fr}.sku-card{flex-direction:column}.workflow-card{grid-template-columns:1fr}}
</style>
