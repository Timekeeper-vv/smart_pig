<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Modal from './Modal.vue'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import type { MedicationRecord, DrugVaccine, User, AlertType } from '../types'

const props = defineProps<{ currentUser: User | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface MedicationForm {
  earTag: string; drugId: number | null; reason: string
  eventTime: string; dosage: string; operator: string
}

const records = ref<MedicationRecord[]>([])
const drugs = ref<DrugVaccine[]>([])
const search = ref<string>('')
const drugFilter = ref<string>('')
const dateFrom = ref<string>('')
const dateTo = ref<string>('')
const showModal = ref<boolean>(false)
const form = ref<MedicationForm>({ earTag: '', drugId: null, reason: '', eventTime: '', dosage: '', operator: '' })

// 选药品后自动填充剂量（来自规格）
watch(() => form.value.drugId, (id) => {
  if (id) {
    const spec = drugs.value.find(d => d.id === id)?.specification || ''
    if (spec) form.value.dosage = spec
  }
})

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
function onSearch() { page.value = 1; load() }

async function load() {
  const p = new URLSearchParams({ page: String(page.value), size: String(pageSize.value) })
  if (search.value) p.set('search', search.value)
  if (drugFilter.value) p.set('drug', drugFilter.value)
  if (dateFrom.value) p.set('dateFrom', dateFrom.value)
  if (dateTo.value) p.set('dateTo', dateTo.value)
  const [rRes, dRes] = await Promise.all([fetch(`/api/events/medication?${p}`), fetch('/api/drugs-vaccines?category=DRUG')])
  const data = await rRes.json()
  records.value = data.content
  total.value = data.total
  drugs.value = await dRes.json()
}

function openAdd() {
  form.value = {
    earTag: '', drugId: null, reason: '', eventTime: today(),
    dosage: '', operator: props.currentUser?.username || ''
  }
  showModal.value = true
}

watch(() => props.currentUser, (u) => {
  if (showModal.value) form.value.operator = u?.username || ''
})

async function save() {
  if (!form.value.earTag || !form.value.drugId) {
    emit('alert', '请填写耳标号和药品', 'error'); return
  }
  const res = await fetch('/api/events/medication', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '用药记录已录入') }
  else { const err = await res.json(); emit('alert', err.error || '录入失败', 'error') }
}

async function deleteRecord(id) {
  if (!confirm('确定删除该记录？')) return
  await fetch(`/api/events/medication/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

function clearFilters() {
  search.value = ''; drugFilter.value = ''; dateFrom.value = ''; dateTo.value = ''; page.value = 1; load()
}

function today() { return new Date().toISOString().split('T')[0] }

function exportExcel() {
  const rows = records.value.map(r => ({
    耳标号: r.earTag, 药品名称: r.drugName, 用药原因: r.reason || '',
    用药日期: r.eventTime, 剂量: r.dosage || '', 执行人: r.operator || '',
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '用药记录')
  XLSX.writeFile(wb, `用药记录_${today()}.xlsx`)
}

function exportPDF() {
  const doc = new jsPDF({ orientation: 'landscape' })
  doc.setFont('helvetica')
  doc.text('用药记录台账', 14, 14)
  autoTable(doc, {
    startY: 22,
    head: [['耳标号', '药品名称', '用药原因', '用药日期', '剂量', '执行人']],
    body: records.value.map(r => [r.earTag, r.drugName, r.reason || '', r.eventTime, r.dosage || '', r.operator || '']),
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [13, 148, 136] },
  })
  doc.save(`用药记录_${today()}.pdf`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用药记录管理</h2>
        <p class="page-desc">记录用药事件，支持原因描述便于后续疫病分析</p>
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
        <button class="btn btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          录入用药
        </button>
      </div>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">用药记录总数</div>
        <div class="stat-num warning">{{ total }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">当前筛选条数</div>
        <div class="stat-num">{{ total }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar filter-toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号、药品或原因..." @input="onSearch" />
        </div>
        <select v-model="drugFilter" class="select-filter" @change="onSearch">
          <option value="">全部药品</option>
          <option v-for="d in drugs" :key="d.id" :value="d.genericName">{{ d.genericName }}</option>
        </select>
        <div class="date-range">
          <input v-model="dateFrom" type="date" class="date-input" title="开始日期" @change="onSearch" />
          <span class="date-sep">至</span>
          <input v-model="dateTo" type="date" class="date-input" title="结束日期" @change="onSearch" />
        </div>
        <button v-if="search || drugFilter || dateFrom || dateTo" class="btn-clear-filter" @click="clearFilters" title="清空筛选">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          清空筛选
        </button>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>药品名称</th>
              <th>用药原因</th>
              <th>用药日期</th>
              <th>剂量</th>
              <th>执行人</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in records" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span class="badge badge-warning">{{ r.drugName }}</span></td>
              <td class="cell-truncate">{{ r.reason || '—' }}</td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.dosage || '—' }}</td>
              <td>
                <span class="operator-tag">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>
                  {{ r.operator || '—' }}
                </span>
              </td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="records.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无用药记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination" v-if="totalPages > 1">
        <button class="pg-btn" :disabled="page === 1" @click="page--">‹</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页 &nbsp;共 {{ total }} 条</span>
        <button class="pg-btn" :disabled="page === totalPages" @click="page++">›</button>
      </div>
    </div>

    <Modal :show="showModal" title="录入用药记录" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="如 ET202606100010" />
        </div>
        <div class="form-group">
          <label>药品 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.drugId">
            <option :value="null">— 从标准库选择 —</option>
            <option v-for="d in drugs" :key="d.id" :value="d.id">{{ d.genericName }}{{ d.specification ? '（' + d.specification + '）' : '' }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>用药日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>剂量 <small style="color:var(--c-text-3)">（按规格自动填入，可修改）</small></label>
          <input v-model="form.dosage" placeholder="如 5mL" />
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
          <label>用药原因 <span style="color:var(--c-error)">*</span></label>
          <textarea v-model="form.reason" rows="2" placeholder="描述发病症状或预防目的"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
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
  white-space: nowrap; color: var(--c-text-2);
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
</style>
