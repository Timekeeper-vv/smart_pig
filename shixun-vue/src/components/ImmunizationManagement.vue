<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Modal from './Modal.vue'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import type { ImmunizationRecord, DrugVaccine, User, Animal, AlertType } from '../types'

const props = defineProps<{ currentUser: User | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface ImmunizationForm {
  earTag: string; vaccineId: number | null; eventTime: string
  dosage: string; operator: string; notes: string
}

const records = ref<ImmunizationRecord[]>([])
const allRecords = ref<ImmunizationRecord[]>([])
const vaccines = ref<DrugVaccine[]>([])
const animals = ref<Animal[]>([])

const search = ref<string>('')
const vaccineFilter = ref<string>('')
const dateFrom = ref<string>('')
const dateTo = ref<string>('')

const showModal = ref<boolean>(false)
const form = ref<ImmunizationForm>({ earTag: '', vaccineId: null, eventTime: '', dosage: '', operator: '', notes: '' })

const showBatchModal = ref<boolean>(false)
const batchVaccineId = ref<number | null>(null)
const batchDate = ref<string>(today())
const batchDosage = ref<string>('')
const batchNotes = ref<string>('')
const batchSearch = ref<string>('')
const selectedEarTags = ref<string[]>([])
const batchSaving = ref<boolean>(false)
const batchModalOpening = ref<boolean>(false)

watch(() => form.value.vaccineId, (id) => {
  if (id) {
    const spec = vaccines.value.find(v => v.id === id)?.specification || ''
    if (spec) form.value.dosage = spec
  }
})

watch(batchVaccineId, (id) => {
  if (id) {
    const spec = vaccines.value.find(v => v.id === id)?.specification || ''
    if (spec) batchDosage.value = spec
  }
})

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
function onSearch() { page.value = 1; load() }

const immunizedTagsForBatchVaccine = computed(() => {
  if (!batchVaccineId.value) return new Set<string>()
  const vaccineName = vaccines.value.find(v => v.id === batchVaccineId.value)?.genericName
  return new Set(allRecords.value.filter(r => r.vaccineName === vaccineName).map(r => r.earTag))
})

const filteredBatchAnimals = computed(() => {
  let list = animals.value.filter(a => a.status === 'ACTIVE')
  if (batchSearch.value) list = list.filter(a => a.earTag.includes(batchSearch.value) || (a.currentPenName || '').includes(batchSearch.value))
  return list
})

const unimmunizedCount = computed(() => {
  const active = animals.value.filter(a => a.status === 'ACTIVE')
  if (!batchVaccineId.value) return active.length
  return active.filter(a => !immunizedTagsForBatchVaccine.value.has(a.earTag)).length
})

async function load() {
  const p = new URLSearchParams({ page: String(page.value), size: String(pageSize.value) })
  if (search.value) p.set('search', search.value)
  if (vaccineFilter.value) p.set('vaccine', vaccineFilter.value)
  if (dateFrom.value) p.set('dateFrom', dateFrom.value)
  if (dateTo.value) p.set('dateTo', dateTo.value)
  const [rRes, vRes, aRes] = await Promise.all([
    fetch(`/api/events/immunization?${p}`),
    fetch('/api/drugs-vaccines?category=VACCINE'),
    fetch('/api/animals'),
  ])
  const data = await rRes.json()
  records.value = data.content
  total.value = data.total
  vaccines.value = await vRes.json()
  animals.value = await aRes.json()
}

function openAdd() {
  form.value = {
    earTag: '', vaccineId: null, eventTime: today(), dosage: '',
    operator: props.currentUser?.username || '', notes: ''
  }
  showModal.value = true
}

watch(() => props.currentUser, (u) => {
  if (showModal.value) form.value.operator = u?.username || ''
})

async function save() {
  if (!form.value.earTag || !form.value.vaccineId) {
    emit('alert', '请填写耳标号和疫苗', 'error'); return
  }
  const res = await fetch('/api/events/immunization', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '免疫记录已录入') }
  else { const err = await res.json(); emit('alert', err.error || '录入失败', 'error') }
}

async function openBatchModal() {
  batchModalOpening.value = true
  try {
    const res = await fetch('/api/events/immunization')
    allRecords.value = await res.json()
  } catch {
    allRecords.value = []
  } finally {
    batchModalOpening.value = false
  }
  batchVaccineId.value = null
  batchDate.value = today()
  batchDosage.value = ''
  batchNotes.value = ''
  batchSearch.value = ''
  selectedEarTags.value = []
  showBatchModal.value = true
}

function isSelected(earTag: string) { return selectedEarTags.value.includes(earTag) }

function toggleEarTag(earTag: string) {
  const idx = selectedEarTags.value.indexOf(earTag)
  if (idx > -1) selectedEarTags.value.splice(idx, 1)
  else selectedEarTags.value.push(earTag)
}

function selectAllBatch() { selectedEarTags.value = filteredBatchAnimals.value.map(a => a.earTag) }
function clearAllBatch() { selectedEarTags.value = [] }

async function batchSave() {
  if (!batchVaccineId.value) { emit('alert', '请先选择疫苗', 'error'); return }
  if (selectedEarTags.value.length === 0) { emit('alert', '请至少选择一只动物', 'error'); return }
  batchSaving.value = true
  const operator = props.currentUser?.username || ''
  let successCount = 0
  for (const earTag of selectedEarTags.value) {
    const res = await fetch('/api/events/immunization', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ earTag, vaccineId: batchVaccineId.value, eventTime: batchDate.value, dosage: batchDosage.value, operator, notes: batchNotes.value }),
    })
    if (res.ok) successCount++
  }
  batchSaving.value = false
  showBatchModal.value = false
  load()
  emit('alert', `批量免疫完成，共录入 ${successCount} / ${selectedEarTags.value.length} 条记录`)
}

async function deleteRecord(id: number) {
  if (!confirm('确定删除该记录？')) return
  await fetch(`/api/events/immunization/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

function clearFilters() {
  search.value = ''; vaccineFilter.value = ''; dateFrom.value = ''; dateTo.value = ''; page.value = 1; load()
}

function today() { return new Date().toISOString().split('T')[0] }

function exportExcel() {
  const rows = records.value.map(r => ({
    耳标号: r.earTag, 疫苗名称: r.vaccineName, 免疫日期: r.eventTime,
    剂量: r.dosage || '', 执行人: r.operator || '', 备注: r.notes || '',
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '免疫记录')
  XLSX.writeFile(wb, `免疫记录_${today()}.xlsx`)
}

function exportPDF() {
  const doc = new jsPDF({ orientation: 'landscape' })
  doc.setFont('helvetica')
  doc.text('免疫记录台账', 14, 14)
  autoTable(doc, {
    startY: 22,
    head: [['耳标号', '疫苗名称', '免疫日期', '剂量', '执行人', '备注']],
    body: records.value.map(r => [r.earTag, r.vaccineName, r.eventTime, r.dosage || '', r.operator || '', r.notes || '']),
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [13, 148, 136] },
  })
  doc.save(`免疫记录_${today()}.pdf`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">免疫记录管理</h2>
        <p class="page-desc">录入个体免疫事件，支持批量操作与未免疫查询</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="exportExcel">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          导出 Excel
        </button>
        <button class="btn btn-secondary" @click="exportPDF">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          导出 PDF
        </button>
        <button class="btn btn-secondary" @click="openBatchModal" :disabled="batchModalOpening">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="6" height="6" rx="1"/><rect x="3" y="13" width="6" height="6" rx="1"/><line x1="13" y1="8" x2="21" y2="8"/><line x1="13" y1="16" x2="21" y2="16"/></svg>
          {{ batchModalOpening ? '加载中…' : '批量录入' }}
        </button>
        <button class="btn btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          录入免疫
        </button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">免疫记录总数</div>
        <div class="stat-num info">{{ total }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在栏动物总数</div>
        <div class="stat-num success">{{ animals.filter(a => a.status === 'ACTIVE').length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar filter-toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或疫苗名称..." @input="onSearch" />
        </div>
        <select v-model="vaccineFilter" class="select-filter" @change="onSearch">
          <option value="">全部疫苗</option>
          <option v-for="v in vaccines" :key="v.id" :value="v.genericName">{{ v.genericName }}</option>
        </select>
        <div class="date-range">
          <input v-model="dateFrom" type="date" class="date-input" title="开始日期（周期起）" @change="onSearch" />
          <span class="date-sep">至</span>
          <input v-model="dateTo" type="date" class="date-input" title="结束日期（周期止）" @change="onSearch" />
        </div>
        <button v-if="search || vaccineFilter || dateFrom || dateTo" class="btn-clear-filter" @click="clearFilters">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          清空筛选
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>疫苗名称</th>
              <th>免疫日期</th>
              <th>剂量</th>
              <th>执行人</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span class="badge badge-info">{{ r.vaccineName }}</span></td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.dosage || '—' }}</td>
              <td>
                <span class="operator-tag">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>
                  {{ r.operator || '—' }}
                </span>
              </td>
              <td class="cell-truncate">{{ r.notes || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="records.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无免疫记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span class="pg-total">共 {{ total }} 条</span>
        <button class="pg-btn" :disabled="page === 1" @click="page--; load()">‹</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="pg-btn" :disabled="page === totalPages" @click="page++; load()">›</button>
        <select v-model.number="pageSize" class="pg-size" @change="page = 1; load()">
          <option :value="5">5条/页</option>
          <option :value="10">10条/页</option>
          <option :value="20">20条/页</option>
          <option :value="50">50条/页</option>
        </select>
      </div>
    </div>

    <!-- 单条录入弹窗 -->
    <Modal :show="showModal" title="录入免疫记录" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="如 ET202606100010" />
        </div>
        <div class="form-group">
          <label>疫苗 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.vaccineId">
            <option :value="null">— 从标准库选择 —</option>
            <option v-for="v in vaccines" :key="v.id" :value="v.id">{{ v.genericName }}{{ v.specification ? '（' + v.specification + '）' : '' }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>免疫日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>剂量 <small style="color:var(--c-text-3)">（按规格自动填入，可修改）</small></label>
          <input v-model="form.dosage" placeholder="如 1头份" />
        </div>
        <div class="form-group">
          <label>执行人</label>
          <div class="operator-display">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>
            <span>{{ form.operator || '未登录' }}</span>
            <span class="op-badge">当前账户</span>
          </div>
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>备注</label>
          <textarea v-model="form.notes" rows="2"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
    </Modal>

    <!-- 批量录入弹窗 -->
    <Modal :show="showBatchModal" title="批量免疫录入" width="620px" @close="showBatchModal = false">
      <!-- 免疫参数 -->
      <div class="batch-form-grid">
        <div class="form-group" style="grid-column: 1 / -1">
          <label>疫苗 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="batchVaccineId">
            <option :value="null">
              {{ vaccines.length ? `— 请选择疫苗（共 ${vaccines.length} 种）—` : '— 暂无疫苗，请先在标准库添加 —' }}
            </option>
            <option v-for="v in vaccines" :key="v.id" :value="v.id">
              {{ v.genericName }}{{ v.specification ? '（' + v.specification + '）' : '' }}
            </option>
          </select>
        </div>
        <div class="form-group">
          <label>免疫日期</label>
          <input v-model="batchDate" type="date" />
        </div>
        <div class="form-group">
          <label>剂量 <small style="color:var(--c-text-3)">（自动填入，可修改）</small></label>
          <input v-model="batchDosage" placeholder="如 1头份" />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>备注</label>
          <input v-model="batchNotes" placeholder="可选" />
        </div>
      </div>

      <!-- 动物选择 -->
      <div class="batch-section-title">
        在栏动物选择
        <span v-if="batchVaccineId" class="batch-unimm-tip">· 未免疫本疫苗 {{ unimmunizedCount }} 只</span>
      </div>
      <div class="batch-toolbar">
        <div class="search-wrap" style="flex:1">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="batchSearch" class="search-input" placeholder="搜索耳标号或圈舍..." />
        </div>
        <button class="btn-xs" @click="selectAllBatch">全选</button>
        <button class="btn-xs" @click="clearAllBatch">清空</button>
        <span class="batch-sel-count">已选 {{ selectedEarTags.length }}/{{ filteredBatchAnimals.length }} 只</span>
      </div>
      <div class="animal-list">
        <div class="animal-list-head">
          <span></span>
          <span>耳标号</span>
          <span>圈舍</span>
          <span>品种</span>
          <span>状态</span>
        </div>
        <div v-if="filteredBatchAnimals.length === 0" class="empty-state" style="padding:20px">
          <p>暂无在栏动物</p>
        </div>
        <label
          v-for="a in filteredBatchAnimals"
          :key="a.id"
          class="animal-item"
          :class="{ selected: isSelected(a.earTag) }"
        >
          <input type="checkbox" :checked="isSelected(a.earTag)" @change="toggleEarTag(a.earTag)" />
          <code class="animal-tag">{{ a.earTag }}</code>
          <span class="animal-pen">{{ a.currentPenName || '—' }}</span>
          <span class="animal-breed">{{ a.breed || '—' }}</span>
          <span v-if="immunizedTagsForBatchVaccine.has(a.earTag)" class="imm-badge">已免疫</span>
          <span v-else></span>
        </label>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showBatchModal = false">取消</button>
        <button class="btn btn-primary" :disabled="batchSaving || selectedEarTags.length === 0" @click="batchSave">
          {{ batchSaving ? '提交中…' : `确认录入（${selectedEarTags.length}/${filteredBatchAnimals.length} 只）` }}
        </button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.header-actions { display: flex; align-items: center; gap: 8px; }
.filter-toolbar { flex-wrap: wrap; gap: 8px; }
.date-range { display: flex; align-items: center; gap: 6px; }
.date-input {
  height: 32px; padding: 0 8px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text);
  background: var(--c-surface);
}
.date-sep { font-size: 12px; color: var(--c-text-3); }
.btn-clear-filter {
  display: inline-flex; align-items: center; gap: 4px;
  height: 32px; padding: 0 10px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text-2);
  background: var(--c-surface); cursor: pointer;
}
.btn-clear-filter:hover { border-color: var(--c-error); color: var(--c-error); }
.cell-truncate {
  max-width: 150px; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; color: var(--c-text-2); font-size: 13px;
}
.operator-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--c-text-2);
}
.operator-display {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: var(--c-bg-2);
  border: 1px solid var(--c-border); border-radius: var(--r);
  font-size: 13px; color: var(--c-text); font-weight: 500;
}
.op-badge {
  margin-left: auto; font-size: 11px; padding: 1px 6px;
  background: var(--c-primary-light); color: var(--c-primary-dark);
  border-radius: 4px;
}
.pagination {
  display: flex; align-items: center; justify-content: flex-end;
  gap: 12px; padding: 12px 16px; border-top: 1px solid var(--c-border);
  font-size: 13px; color: var(--c-text-2);
}
.pg-btn {
  width: 28px; height: 28px; border: 1px solid var(--c-border);
  border-radius: var(--r); background: var(--c-surface); cursor: pointer;
  font-size: 16px; display: flex; align-items: center; justify-content: center; color: var(--c-text);
}
.pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.pg-btn:not(:disabled):hover { border-color: var(--c-primary); color: var(--c-primary); }
.pg-total { color: var(--c-text-3); margin-right: auto; }
.pg-size {
  height: 28px; padding: 0 6px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text);
  background: var(--c-surface); cursor: pointer;
}

/* Batch modal */
.batch-form-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px;
  padding-bottom: 16px; border-bottom: 1px solid var(--c-border); margin-bottom: 14px;
}
.batch-section-title {
  font-size: 11px; font-weight: 600; text-transform: uppercase;
  letter-spacing: .5px; color: var(--c-text-3);
  margin-bottom: 10px; display: flex; align-items: center; gap: 6px;
}
.batch-unimm-tip { color: var(--c-primary); font-weight: 500; text-transform: none; letter-spacing: 0; }
.batch-toolbar {
  display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
}
.batch-sel-count { font-size: 12px; color: var(--c-primary); font-weight: 600; white-space: nowrap; }
.btn-xs {
  height: 28px; padding: 0 12px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text-2);
  background: var(--c-surface); cursor: pointer; white-space: nowrap; flex-shrink: 0;
}
.btn-xs:hover { border-color: var(--c-primary); color: var(--c-primary); }
.animal-list {
  border: 1px solid var(--c-border); border-radius: var(--r);
  max-height: 260px; overflow-y: auto; margin-bottom: 16px;
}
.animal-list-head {
  display: grid; grid-template-columns: 20px 140px 1fr 80px 52px;
  gap: 10px; padding: 7px 12px;
  font-size: 11px; color: var(--c-text-3); font-weight: 600;
  border-bottom: 2px solid var(--c-border);
  background: var(--c-bg-2); position: sticky; top: 0; z-index: 1;
}
.animal-item {
  display: grid; grid-template-columns: 20px 140px 1fr 80px 52px;
  gap: 10px; align-items: center;
  padding: 9px 12px; cursor: pointer;
  border-bottom: 1px solid var(--c-border); transition: background .1s;
}
.animal-item:last-child { border-bottom: none; }
.animal-item:hover { background: var(--c-bg-2); }
.animal-item.selected { background: var(--c-primary-light); }
.animal-item input[type="checkbox"] { width: 14px; height: 14px; cursor: pointer; margin: 0; }
.animal-tag { font-size: 12px; color: var(--c-text); font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.animal-pen { font-size: 12px; color: var(--c-text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.animal-breed { font-size: 11px; color: var(--c-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.imm-badge { font-size: 10px; padding: 2px 6px; background: #dcfce7; color: #16a34a; border-radius: 4px; text-align: center; white-space: nowrap; }
</style>
