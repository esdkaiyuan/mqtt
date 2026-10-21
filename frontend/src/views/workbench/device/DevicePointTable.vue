<template>
  <div class="device-point-page page">
    <PageHeader
      title="设备点位表"
      desc="声明设备的寄存器 / 线圈 / tag 映射与换算规则。平台侧轮询与边缘网关采集共用同一份点表，差别只在执行方"
    >
      <template #actions>
        <el-button @click="openImport">批量导入</el-button>
        <el-button @click="handleExportTemplate">导出模板</el-button>
        <el-button
          v-if="isEdgeTable"
          :loading="dispatching"
          @click="handleDispatch"
        >
          下发到网关
        </el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </PageHeader>

    <!--
      执行方是本页最关键的一项：同一台设备的点位必须统一由一条采集路线执行。
      切换它会让整张点表的归属一起变，因此这里用一个分段控件作用于全表，
      而不是每行一个下拉 —— 每行一个下拉很容易配出「一半 EDGE 一半 PLATFORM」的点表，
      而服务端会整批拒绝。
    -->
    <div class="card executor-bar">
      <div class="executor-label">采集执行方</div>
      <el-radio-group v-model="tableExecutor" @change="applyExecutorToAll">
        <el-radio-button value="">不限（查看全部）</el-radio-button>
        <el-radio-button v-for="opt in EXECUTOR_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </el-radio-button>
      </el-radio-group>
      <div class="executor-hint">
        <template v-if="tableExecutor">
          新增点位将使用 <strong>{{ tableExecutor }}</strong>；保存时该设备下所有点位必须是同一个执行方
        </template>
        <template v-else>
          左侧仅用于筛选查看。新增点位默认 EDGE，若要从边缘采集切到平台轮询，请先切换执行方
        </template>
      </div>
    </div>

    <div class="card">
      <el-table :data="points" v-loading="loading" border empty-text="尚未配置点位">
        <el-table-column label="点位标识" width="180">
          <template #default="{ row }">
            <el-input v-model="row.pointKey" placeholder="映射到物模型 identifier" />
          </template>
        </el-table-column>

        <el-table-column label="点位类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.pointType">
              <el-option v-for="t in POINT_TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="地址" width="140">
          <template #default="{ row }">
            <el-input v-model="row.address" :placeholder="isRegisterType(row.pointType) ? '寄存器号' : 'tag / 节点名'" />
          </template>
        </el-table-column>

        <el-table-column label="从站" width="100">
          <template #default="{ row }">
            <el-input v-model="row.unitId" maxlength="3" placeholder="继承" />
          </template>
        </el-table-column>

        <el-table-column label="数据类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.dataType">
              <el-option v-for="d in DATA_TYPE_OPTIONS" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </template>
        </el-table-column>

        <el-table-column label="换算" width="180">
          <template #default="{ row }">
            <div class="scale-row">
              <el-input-number v-model="row.scale" :controls="false" placeholder="scale" style="width: 82px" />
              <span class="scale-op">×</span>
              <el-input-number v-model="row.offset" :controls="false" placeholder="offset" style="width: 82px" />
            </div>
          </template>
        </el-table-column>

        <el-table-column label="单位" width="90">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="如 ℃" />
          </template>
        </el-table-column>

        <el-table-column label="周期(ms)" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.readIntervalMs" :min="1000" :step="1000" :controls="false" style="width: 100%" />
          </template>
        </el-table-column>

        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" />
          </template>
        </el-table-column>

        <!--
          最近采集状态列：把「没配点」与「配了采不到」在界面上分开。
          没有这一列时，两种故障在现场看到的都是同一个现象 —— 值没更新。
        -->
        <el-table-column label="最近采集" width="200">
          <template #default="{ row }">
            <template v-if="!row.lastCollectedAt">
              <span class="muted">未采集</span>
            </template>
            <template v-else>
              <el-tag :type="statusTagType(row.lastRunStatus)" size="small">
                {{ row.lastRunStatus || '未知' }}
              </el-tag>
              <div v-if="row.lastErrorMessage" class="err-msg" :title="row.lastErrorMessage">
                {{ row.lastErrorMessage }}
              </div>
              <div class="muted small">{{ row.lastCollectedAt }}</div>
            </template>
          </template>
        </el-table-column>

        <el-table-column label="" width="90" align="center">
          <template #default="{ $index }">
            <el-button link type="danger" @click="removeRow($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-button @click="addRow">新增点位</el-button>
        <span class="muted">共 {{ points.length }} 个点位</span>
        <span v-if="removedRows.length" class="muted">
          （{{ removedRows.length }} 个已删除的行将在保存后生效）
        </span>
      </div>
    </div>

    <!-- 批量导入现场点表：JSON 粘贴，兼容常见别名与多种外层结构 -->
    <el-dialog v-model="importVisible" title="批量导入点位表" width="640px">
      <div class="form-hint">
        粘贴 JSON 数组（也兼容 <code>{ "points": [...] }</code> / <code>{ "properties": [...] }</code>）。
        字段名支持 <code>pointKey / point_key / key / name / identifier / tag</code> 等常见别名，
        缺字段的行会被跳过并在下方列出，不影响其余行导入
      </div>
      <el-input v-model="importText" type="textarea" :rows="10" :placeholder="importPlaceholder" />
      <div v-if="importSkipped.length" class="skip-list">
        <div class="skip-title">已跳过 {{ importSkipped.length }} 行</div>
        <div v-for="s in importSkipped.slice(0, 10)" :key="s.index" class="skip-item">第 {{ s.index + 1 }} 行：{{ s.reason }}</div>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="handleImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  devicePointTableApi,
  EXECUTOR_OPTIONS,
  POINT_TYPE_OPTIONS,
  DATA_TYPE_OPTIONS,
  isRegisterType,
  createEmptyPoint,
  parsePointsJson,
  stringifyPointsJson
} from '@/api/devicePointTable'

const route = useRoute()
const deviceId = computed(() => route.params.id)

const loading = ref(false)
const saving = ref(false)
const dispatching = ref(false)
const points = ref([])
/** 已从服务端删除、待保存时生效的点位 ID */
const removedRows = ref([])
/** 筛选用的执行方；'' 表示不限，仅影响展示 */
const tableExecutor = ref('')
/** 新增点位使用的执行方（表格数据本身每行也带 executor） */
const newPointExecutor = ref('EDGE')

const importVisible = ref(false)
const importText = ref('')
const importSkipped = ref([])

/** placeholder 示例走绑定而非字面量：JSON 示例本身含双引号，写进属性必然引号冲突 */
const importPlaceholder = '[{"pointKey":"temp","address":"40001","scale":0.1,"unit":"℃"}]'

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await devicePointTableApi.listPoints(deviceId.value, tableExecutor.value || undefined)
    points.value = (res?.data || []).map((p) => ({ ...createEmptyPoint(), ...p }))
    removedRows.value = []
    // 列表已带全部点位的执行方；若存在既有执行方，新增点位默认沿用它
    const existing = points.value.find((p) => p.executor)?.executor
    if (existing) {
      newPointExecutor.value = existing
      tableExecutor.value = existing
    }
    snapshotLoaded()
  } catch (e) {
    ElMessage.error(e?.message || '点位表加载失败')
  } finally {
    loading.value = false
  }
}

/** 执行方切换后同步全表未保存的新行；已存在的行不动，避免误改历史配置。 */
function applyExecutorToAll(val) {
  if (!val) return
  newPointExecutor.value = val
  points.value.forEach((p) => {
    if (p.id == null) {
      p.executor = val
    }
  })
}

function addRow() {
  points.value.push(createEmptyPoint(newPointExecutor.value))
}

async function removeRow(index) {
  const row = points.value[index]
  if (row.id != null) {
    await ElMessageBox.confirm(
      `删除点位「${row.pointKey}」，其历史采集记录也会一并清理。`,
      '确认删除',
      { type: 'warning' }
    )
    try {
      await devicePointTableApi.deletePoint(deviceId.value, row.id)
      removedRows.value.push(row.id)
    } catch (e) {
      ElMessage.error(e?.message || '删除失败')
      return
    }
  }
  points.value.splice(index, 1)
}

async function handleSave() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    await devicePointTableApi.savePoints(
      deviceId.value,
      points.value.map((p) => ({
        ...p,
        unitId: p.unitId === '' || p.unitId === null || p.unitId === undefined
          ? null : Number(p.unitId)
      }))
    )
    ElMessage.success('点位表已保存')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 点表执行方为 EDGE 时才允许下发（PLATFORM 由平台自行采集，下发无意义） */
const isEdgeTable = computed(
  () => points.value.length > 0 && points.value.every((p) => p.executor === 'EDGE')
)

/**
 * 最近一次从服务端取回（或保存成功）的表格快照，用于判断「是否有未保存改动」。
 * <p>
 * 只做「有没有改动」这一个判断，不做逐字段 diff —— 下发前的提醒只需知道改动存在与否，
 * 做细了反而会在用户批量微调时反复弹窗。
 */
const lastLoadedSnapshot = ref('[]')

function snapshotLoaded() {
  lastLoadedSnapshot.value = JSON.stringify(points.value.map((p) => ({ ...p })))
}

/**
 * 下发点表到边缘网关。
 *
 * 有未保存的改动时先要求保存：下发的是服务端已存的点表，
 * 直接下发会让用户以为表格里的新配置已经送到网关，实际送的是旧版本 —— 这类误判很难现场发现。
 */
async function handleDispatch() {
  if (hasUnsavedChanges()) {
    try {
      await ElMessageBox.confirm(
        '当前表格有未保存的改动，下发的将是服务端已保存的点表。是否先保存？',
        '点表下发',
        { confirmButtonText: '去保存', cancelButtonText: '直接下发已保存版本', type: 'warning' }
      )
      await handleSave()
    } catch {
      // 用户选择「直接下发已保存版本」，按其意图继续
    }
    if (hasUnsavedChanges()) {
      ElMessage.warning('请先保存点表再下发')
      return
    }
  }

  dispatching.value = true
  try {
    const res = await devicePointTableApi.dispatch(deviceId.value)
    const data = res?.data || {}
    // QUEUED 表示设备离线、已入队待补发，配置尚未送达 —— 提示必须区分这两种情况
    if (data.status === 'QUEUED') {
      ElMessage.warning(`网关当前离线，点表已排队（版本 ${data.version}），上线后自动生效`)
    } else {
      ElMessage.success(`点表已下发（版本 ${data.version}，${data.pointCount} 个点位）`)
    }
  } catch (e) {
    ElMessage.error(e?.message || '点表下发失败')
  } finally {
    dispatching.value = false
  }
}

function hasUnsavedChanges() {
  return (
    JSON.stringify(points.value.map((p) => ({ ...p }))) !== JSON.stringify(lastLoadedSnapshot.value)
  )
}

/**
 * 前端只做「明显的错」，不做业务规则的全量复制。
 * 服务端才是唯一判定方，这里拦住的是能让用户少提交一次、错误信息也更直白的那些。
 */
function validate() {
  if (points.value.length === 0) {
    return '没有可保存的点位'
  }
  const seen = new Set()
  for (const p of points.value) {
    const key = (p.pointKey || '').trim()
    if (!key) return '存在未填写点位标识的行'
    if (seen.has(key)) return `点位标识「${key}」重复`
    seen.add(key)
    if (isRegisterType(p.pointType) && !(p.address || '').trim()) {
      return `点位「${key}」是寄存器类型，必须填写地址`
    }
    if (isRegisterType(p.pointType) && !/^\d+$/.test((p.address || '').trim())) {
      return `点位「${key}」的地址必须是寄存器号（数字）`
    }
    const unitId = p.unitId
    if (unitId !== null && unitId !== undefined && String(unitId).trim() !== '') {
      const n = Number(unitId)
      if (!Number.isInteger(n) || n < 0 || n > 247) {
        return `点位「${key}」的从站地址需在 0~247，留空表示继承驱动配置`
      }
    }
    if (p.scale === 0) {
      return `点位「${key}」的 scale 不能为 0（会把所有值压成 0）`
    }
  }
  const executors = new Set(points.value.map((p) => p.executor).filter(Boolean))
  if (executors.size > 1) {
    return `同一设备的点位执行方必须一致，当前混用了 ${[...executors].join(' / ')}`
  }
  return null
}

function openImport() {
  importText.value = ''
  importSkipped.value = []
  importVisible.value = true
}

function handleImport() {
  try {
    const { points: imported, skipped } = parsePointsJson(importText.value)
    if (imported.length === 0) {
      ElMessage.warning('没有解析到可用点位')
      return
    }
    // 导入点位沿用当前页面的执行方，避免用户导入 50 行后才在保存时被服务端整批拒绝
    const executor = tableExecutor.value || newPointExecutor.value
    imported.forEach((p) => {
      p.executor = executor
    })
    points.value = points.value.concat(imported)
    importSkipped.value = skipped
    importVisible.value = false
    ElMessage.success(
      skipped.length
        ? `已导入 ${imported.length} 个点位，跳过 ${skipped.length} 行（可在弹窗中查看原因）`
        : `已导入 ${imported.length} 个点位`
    )
    if (skipped.length) {
      openImport()
    }
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  }
}

function handleExportTemplate() {
  // 导出的是「当前配置」，用户可在 Excel/文本编辑器里改完再粘贴回来
  const text = stringifyPointsJson(points.value)
  const blob = new Blob([text], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `point-table-device-${deviceId.value}.json`
  a.click()
  URL.revokeObjectURL(url)
}

function statusTagType(status) {
  if (status === 'OK') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'info'
}
</script>

<style scoped>
.executor-bar {
  margin-bottom: 16px;
}
.executor-label {
  font-weight: 600;
  margin-bottom: 8px;
}
.executor-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #909399);
}
.scale-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.scale-op {
  color: var(--color-text-secondary, #909399);
}
.table-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.muted {
  color: var(--color-text-secondary, #909399);
  font-size: 12px;
}
.small {
  font-size: 12px;
}
.err-msg {
  color: var(--el-color-danger, #f56c6c);
  font-size: 12px;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skip-list {
  margin-top: 12px;
  padding: 8px 12px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 4px;
  font-size: 12px;
  max-height: 140px;
  overflow-y: auto;
}
.skip-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.skip-item {
  color: var(--color-text-secondary, #909399);
}
.form-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #909399);
  margin-bottom: 8px;
  line-height: 1.6;
}
</style>