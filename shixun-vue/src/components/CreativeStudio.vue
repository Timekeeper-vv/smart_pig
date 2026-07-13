<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const styles = ref<any[]>([])
const assets = ref<any[]>([])
const jobs = ref<any[]>([])
const loading = ref(false)
const promptLoading = ref(false)
const imageLoading = ref(false)
const result = ref<any>(null)
const previewImage = ref<any>(null)
const currentStep = ref(0)

const form = ref({
  title: '山城街巷亚克力钥匙扣',
  styleId: 1,
  productType: '亚克力钥匙扣',
  scene: '景区伴手礼、城市礼物、年轻游客购买',
  concept: '以山城坡道、路灯、老店招牌、小面碗和街巷烟火气为核心元素，做一款温暖、有记忆点的城市文创产品。',
  material: '3mm透明亚克力，双面彩印，金属钥匙扣，独立OPP袋',
  audience: '年轻游客、城市礼物购买者、企业活动客户',
  visualStyle: '温暖治愈、东方城市插画、干净高级、适合小批量打样',
  imageSize: '1024x1024',
  tags: '文创,产品原型图,AI设计工坊',
  negativePrompt: '不要低清晰度，不要错字，不要复杂杂乱背景，不要廉价塑料感，不要侵权IP形象'
})

const generatedPrompt = ref('')
const generatedNegativePrompt = ref('')
const selectedStyle = computed(() => styles.value.find(s => Number(s.id) === Number(form.value.styleId)))
const imageAssets = computed(() => assets.value.filter(a => a.assetType === 'image').slice(0, 8))

async function loadBase() {
  loading.value = true
  try {
    const [s, a, j] = await Promise.all([
      fetch('/api/creative/ai/styles'),
      fetch('/api/creative/ai/assets'),
      fetch('/api/creative/ai/jobs')
    ])
    styles.value = await s.json()
    assets.value = await a.json()
    jobs.value = await j.json()
  } catch (e: any) { emit('alert', `加载AI设计工坊失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}

function buildBasicIdea() {
  return [
    `主题/IP：${form.value.concept}`,
    `目标人群：${form.value.audience}`,
    `材质工艺：${form.value.material}`,
    `视觉风格：${form.value.visualStyle}`
  ].join('\n')
}

async function generatePrompt() {
  promptLoading.value = true
  result.value = null
  try {
    const res = await fetch('/api/creative/ai/prompt/ai', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({
        title: form.value.title,
        styleId: form.value.styleId,
        productType: form.value.productType,
        scene: form.value.scene,
        prompt: buildBasicIdea(),
        negativePrompt: form.value.negativePrompt,
        imageSize: form.value.imageSize,
        tags: form.value.tags
      })
    })
    if (!res.ok) throw new Error(await res.text())
    const data = await res.json()
    generatedPrompt.value = data.prompt || data.rawPrompt || ''
    generatedNegativePrompt.value = data.negativePrompt || form.value.negativePrompt
    currentStep.value = 1
    emit('alert', '产品原型图提示词已生成，可以继续修改', 'success')
  } catch (e: any) { emit('alert', `生成提示词失败：${e.message || e}`, 'error') }
  finally { promptLoading.value = false }
}

async function generateImage() {
  if (!generatedPrompt.value.trim()) { emit('alert', '请先生成或填写提示词', 'error'); return }
  imageLoading.value = true
  result.value = null
  currentStep.value = 2
  try {
    const res = await fetch('/api/creative/ai/text-to-image', {
      method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify({
        title: form.value.title,
        styleId: form.value.styleId,
        productType: form.value.productType,
        scene: form.value.scene,
        prompt: generatedPrompt.value,
        negativePrompt: generatedNegativePrompt.value,
        imageSize: form.value.imageSize,
        tags: form.value.tags
      })
    })
    if (!res.ok) throw new Error(await res.text())
    result.value = await res.json()
    currentStep.value = 3
    emit('alert', '产品原型图生成成功，已保存到资产库', 'success')
    await loadBase()
  } catch (e: any) { emit('alert', `生成产品图失败：${e.message || e}`, 'error'); currentStep.value = 1 }
  finally { imageLoading.value = false }
}

function useSample(type: string) {
  if (type === 'magnet') {
    form.value.title = '山城街巷冰箱贴'
    form.value.productType = '冰箱贴'
    form.value.material = '软磁+滴胶工艺，异形轮廓，背面磁吸'
    form.value.concept = '把山城坡道、轻轨穿楼、老街灯牌做成层次丰富的城市记忆冰箱贴。'
  } else if (type === 'sticker') {
    form.value.title = '街巷小味贴纸包'
    form.value.productType = '贴纸包'
    form.value.material = '防水PVC贴纸，模切白边，6-8个元素一组'
    form.value.concept = '一组治愈系食物和街角小物贴纸，包含小面、茶碗、路灯、店招、猫和坡道。'
  } else {
    form.value.title = '山城街巷亚克力钥匙扣'
    form.value.productType = '亚克力钥匙扣'
    form.value.material = '3mm透明亚克力，双面彩印，金属钥匙扣，独立OPP袋'
    form.value.concept = '以山城坡道、路灯、老店招牌、小面碗和街巷烟火气为核心元素，做一款温暖、有记忆点的城市文创产品。'
  }
  generatedPrompt.value = ''
  result.value = null
  currentStep.value = 0
}

function downloadUrl(url?: string) { return url || '#' }
function openPreview(asset: any) { if (asset?.previewUrl || asset?.fileUrl || asset?.imageUrl) previewImage.value = asset }
onMounted(loadBase)
</script>

<template>
  <div class="page studio-page">
    <div class="page-header studio-hero">
      <div>
        <p class="eyebrow">AI DESIGN STUDIO</p>
        <h2 class="page-title">AI设计工坊</h2>
        <p class="page-desc">用户只填基础信息，AI先生成“产品原型图提示词”；你可以修改确认，再调用大模型生成产品图。</p>
      </div>
      <button class="btn btn-secondary" @click="loadBase">刷新资产</button>
    </div>

    <div class="steps-card">
      <div class="step" :class="{active:currentStep===0,done:currentStep>0}"><span>1</span><b>填基础信息</b><small>产品、主题、材质、风格</small></div>
      <div class="step" :class="{active:currentStep===1,done:currentStep>1}"><span>2</span><b>AI生成提示词</b><small>生成后可人工修改</small></div>
      <div class="step" :class="{active:currentStep===2,done:currentStep>2}"><span>3</span><b>确认生成产品图</b><small>保存到资产库</small></div>
    </div>

    <div v-if="promptLoading || imageLoading" class="loading-banner">
      {{ promptLoading ? '正在调用硅基流动AI生成产品原型图提示词，通常20-60秒...' : '正在调用硅基流动AI生成产品图，通常20-60秒，请勿重复点击...' }}
    </div>

    <div class="studio-layout">
      <section class="studio-panel">
        <div class="section-title"><span>填</span><div><h3>基础信息</h3><p>这里尽量用业务语言填写，不需要懂提示词。</p></div></div>
        <div class="sample-row">
          <button class="mini" @click="useSample('keychain')">钥匙扣示例</button>
          <button class="mini" @click="useSample('magnet')">冰箱贴示例</button>
          <button class="mini" @click="useSample('sticker')">贴纸包示例</button>
        </div>
        <label>产品名称</label><input v-model="form.title" />
        <div class="form-row"><div><label>产品类型</label><input v-model="form.productType" /></div><div><label>图片尺寸</label><select v-model="form.imageSize"><option value="1024x1024">方图 1024×1024</option><option value="1024x768">横图 1024×768</option><option value="768x1024">竖图 768×1024</option></select></div></div>
        <label>品牌视觉风格</label>
        <select v-model="form.styleId">
          <option v-for="s in styles" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <div v-if="selectedStyle" class="style-note"><b>{{ selectedStyle.name }}</b><span>{{ selectedStyle.description }}</span></div>
        <label>主题/IP想法</label><textarea v-model="form.concept" rows="4" />
        <label>目标人群</label><input v-model="form.audience" />
        <label>使用场景</label><input v-model="form.scene" />
        <label>材质/工艺</label><input v-model="form.material" />
        <label>想要的视觉风格</label><input v-model="form.visualStyle" />
        <label>反向要求</label><input v-model="form.negativePrompt" />
        <button class="btn btn-primary full" :disabled="promptLoading" @click="generatePrompt">{{ promptLoading ? 'AI生成中...' : '下一步：AI生成产品图提示词' }}</button>
      </section>

      <section class="studio-panel">
        <div class="section-title"><span>改</span><div><h3>可编辑提示词</h3><p>AI生成后你可以直接改，确认满意再生图。</p></div></div>
        <label>正向提示词</label>
        <textarea v-model="generatedPrompt" rows="14" placeholder="点击左侧按钮后，这里会出现用于生成产品原型图的提示词。你也可以手动填写。" />
        <label>反向提示词</label>
        <textarea v-model="generatedNegativePrompt" rows="4" placeholder="不想出现的内容，比如：低清晰、错字、变形、杂乱背景。" />
        <button class="btn btn-primary full" :disabled="imageLoading || !generatedPrompt.trim()" @click="generateImage">{{ imageLoading ? '产品图生成中...' : '确认提示词，生成产品图' }}</button>

        <div v-if="result" class="result-card">
          <div class="result-head"><div><b>生成成功</b><span>{{ result.jobNo }} · 资产ID {{ result.assetId }}</span></div><a class="btn btn-secondary btn-sm" :href="downloadUrl(result.imageUrl)" :download="form.title + '.png'">下载图片</a></div>
          <img :src="result.imageUrl" @click="openPreview({ ...result, previewUrl: result.imageUrl, title: form.title })" />
        </div>
      </section>
    </div>

    <section class="studio-panel asset-panel">
      <div class="panel-head"><div><h3>最近生成的产品图</h3><p>生成成功后会自动沉淀到资产库，后面可以继续用于打样、上架和评审。</p></div></div>
      <div class="asset-grid">
        <article v-for="asset in imageAssets" :key="asset.id" class="asset-card">
          <img v-if="asset.previewUrl" :src="asset.previewUrl" @click="openPreview(asset)" />
          <div v-else class="asset-placeholder">图片资产</div>
          <div class="asset-body"><b>{{ asset.title }}</b><span>{{ asset.assetNo }} · {{ asset.status }}</span><small>{{ asset.tags }}</small><a class="btn btn-secondary btn-sm" :href="downloadUrl(asset.fileUrl || asset.previewUrl)" :download="asset.title + '.png'">下载</a></div>
        </article>
        <div v-if="!imageAssets.length" class="empty">暂无产品图，先生成一张。</div>
      </div>
      <h4>最近任务</h4>
      <div class="job-list"><div v-for="job in jobs.slice(0,8)" :key="job.id" class="job-item"><b>{{ job.jobNo }}</b><span>{{ job.jobType }} / {{ job.provider }}</span><em :class="job.status">{{ job.status }}</em></div></div>
    </section>

    <div v-if="previewImage" class="image-modal" @click.self="previewImage=null">
      <div class="image-modal-card">
        <button class="modal-close" @click="previewImage=null">×</button>
        <img :src="previewImage.previewUrl || previewImage.fileUrl || previewImage.imageUrl" />
        <div class="modal-foot"><b>{{ previewImage.title || 'AI生成图片' }}</b><a class="btn btn-primary btn-sm" :href="downloadUrl(previewImage.fileUrl || previewImage.previewUrl || previewImage.imageUrl)" :download="(previewImage.title || 'AI作品') + '.png'">下载原图</a></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.studio-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.studio-hero{padding:26px;border-radius:20px;background:linear-gradient(135deg,#111827,#7c3aed 45%,#f97316);color:#fff}.studio-hero .page-title{color:#fff;font-size:28px}.studio-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#fed7aa;font-weight:800}.steps-card{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin:18px 0}.step{background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:14px;display:flex;align-items:center;gap:12px;box-shadow:var(--shadow-sm)}.step span{width:34px;height:34px;border-radius:50%;background:#e2e8f0;color:#334155;display:flex;align-items:center;justify-content:center;font-weight:900;flex-shrink:0}.step b{display:block}.step small{display:block;color:#64748b;margin-top:3px}.step.active{border-color:#f97316;background:#fff7ed}.step.active span{background:#f97316;color:#fff}.step.done{border-color:#a78bfa;background:#faf5ff}.step.done span{background:#7c3aed;color:#fff}.loading-banner{margin:14px 0;padding:12px 16px;border-radius:14px;background:#fff7ed;border:1px solid #fed7aa;color:#9a3412;font-weight:800}.studio-layout{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-bottom:18px}.studio-panel{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm)}.section-title{display:flex;gap:12px;align-items:flex-start;margin-bottom:14px}.section-title>span{background:#7c3aed;color:#fff;border-radius:12px;min-width:38px;height:32px;display:flex;align-items:center;justify-content:center;font-weight:900}.section-title h3{margin:0 0 4px}.section-title p{margin:0;color:#64748b;font-size:13px}.sample-row{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}.mini{border:0;background:#f1f5f9;border-radius:999px;padding:8px 12px;cursor:pointer;font-weight:800}.studio-panel label{display:block;font-size:12px;font-weight:800;color:#64748b;margin:10px 0 6px}input,select,textarea{width:100%;border:1px solid var(--c-border);border-radius:10px;padding:10px 12px;font-family:var(--font);font-size:14px;background:#fff}textarea{resize:vertical;line-height:1.6}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:10px}.style-note{padding:12px;border-radius:12px;background:#f5f3ff;border:1px solid #ddd6fe;margin-top:10px;display:flex;flex-direction:column;gap:4px}.style-note span{color:#6b7280;font-size:12px}.full{width:100%;margin-top:16px}.result-card{margin-top:18px;border:2px solid #22c55e;background:#f0fdf4;border-radius:18px;padding:14px}.result-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:12px}.result-head b{display:block}.result-head span{font-size:12px;color:#64748b}.result-card img{width:100%;max-height:560px;object-fit:contain;border-radius:14px;background:#fff;cursor:zoom-in;box-shadow:0 12px 30px rgba(15,23,42,.12)}.panel-head h3{margin:0 0 4px}.panel-head p{margin:0 0 16px;color:var(--c-text-2);font-size:13px}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:14px}.asset-card{border:1px solid #e5e7eb;border-radius:16px;overflow:hidden;background:#fff}.asset-card img,.asset-placeholder{width:100%;height:190px;object-fit:cover;background:#111827;color:#fff;display:flex;align-items:center;justify-content:center;cursor:zoom-in}.asset-body{padding:12px;display:flex;flex-direction:column;gap:6px}.asset-body span,.asset-body small{font-size:12px;color:#64748b}.asset-body a{text-decoration:none;text-align:center;align-self:flex-start}.empty{grid-column:1/-1;text-align:center;color:#64748b;padding:36px;border:1px dashed #cbd5e1;border-radius:14px}.job-list{display:flex;flex-direction:column;gap:8px}.job-item{display:flex;align-items:center;gap:10px;border-top:1px solid #eef2f7;padding:10px 0}.job-item span{flex:1;color:#64748b;font-size:13px}.job-item em{font-style:normal;border-radius:999px;padding:3px 8px;font-size:12px;background:#e5e7eb}.job-item em.succeeded{background:#dcfce7;color:#166534}.job-item em.failed{background:#fee2e2;color:#991b1b}.job-item em.running{background:#dbeafe;color:#1d4ed8}.image-modal{position:fixed;inset:0;background:rgba(15,23,42,.74);z-index:1000;display:flex;align-items:center;justify-content:center;padding:28px}.image-modal-card{position:relative;background:#fff;border-radius:20px;padding:16px;max-width:min(1100px,94vw);max-height:94vh;display:flex;flex-direction:column;gap:12px;box-shadow:0 25px 80px rgba(0,0,0,.35)}.image-modal-card img{max-width:100%;max-height:78vh;object-fit:contain;border-radius:14px;background:#f8fafc}.modal-close{position:absolute;right:14px;top:10px;border:0;background:rgba(15,23,42,.72);color:#fff;border-radius:999px;width:34px;height:34px;font-size:22px;cursor:pointer}.modal-foot{display:flex;justify-content:space-between;align-items:center;gap:12px}@media(max-width:1100px){.studio-layout,.steps-card,.form-row{grid-template-columns:1fr}.result-head{align-items:flex-start;flex-direction:column}}
</style>
