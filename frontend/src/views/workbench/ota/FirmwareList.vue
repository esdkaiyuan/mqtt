<template>
  <div class="firmware-list-page page">
    <PageHeader title="固件管理" desc="上传并管理 OTA 固件包，供升级任务按产品下发">
      <template #actions>
        <el-button type="primary" :disabled="!canWrite" @click="openUploadDialog">
          <svg-icon name="add" :size="16" />
          上传固件
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-select
        v-model="filterProductId"
        class="filter-item"
        placeholder="全部产品"
        clearable
      >
        <el-option
          v-for="item in productOptions"
          :key="item.id"
          :label="item.productName"
          :value="item.id"
        />
      </el-select>
      <div class="filter-actions">
        <el-button :disabled="loading" @click="reload">
          <svg-icon name="refresh" :size="16" />
          刷新
        </el-button>
      </div>
    </div>

    <div class="firmware-table-wrap">
      <el-table v-loading="loading" :data="firmwares" border stripe>
        <el-table-column prop="productName" label="所属产品" min-width="150" />
        <el-table-column label="版本号" width="140">
          <template #default="{ row }">
            <span class="firmware-table__mono">{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column label="文件名" min-width="200">
          <template #default="{ row }">
            <span class="firmware-table__mono">{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="MD5" min-width="180">
          <template #default="{ row }">
            <span class="firmware-table__mono">{{ row.md5 || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="说明"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="上传时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadFirmware(row)">下载</el-button>
            <el-button link type="danger" :disabled="!canWrite" @click="removeFirmware(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无固件包，点击「上传固件」添加" />
        </template>
      </el-table>
    </div>

    <el-dialog
      v-model="showUploadDialog"
      title="上传固件"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="所属产品" required>
          <el-select
            v-model="form.productId"
            class="dialog-form__control"
            placeholder="请选择产品"
          >
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input
            v-model="form.version"
            maxlength="64"
            show-word-limit
            placeholder="如：1.0.0（仅字母、数字与 . _ -）"
            @keyup.enter="handleUpload"
          />
        </el-form-item>
        <el-form-item label="固件文件" required>
          <input
            ref="fileInputRef"
            type="file"
            class="dialog-form__file"
            @change="handleFileChange"
          />
          <div class="dialog-form__file-picker">
            <el-button @click="triggerFilePick">选择文件</el-button>
            <span class="dialog-form__file-name">
              {{ selectedFile ? selectedFile.name : '未选择任何文件' }}
            </span>
          </div>
          <p class="dialog-form__hint">
            单个固件不超过 {{ formatFileSize(MAX_FILE_SIZE) }}
          </p>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="选填，如：修复温湿度上报异常"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { otaApi } from '@/api/ota'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'
import { useAuthStore } from '@/stores/auth'

/** 固件包大小上限（32MB），与后端 app.ota.max-file-size 保持一致。 */
const MAX_FILE_SIZE = 32 * 1024 * 1024
/** 版本号格式，与后端 OtaFirmwareServiceImpl 的校验正则保持一致。 */
const VERSION_PATTERN = /^[0-9A-Za-z._-]{1,64}$/

const authStore = useAuthStore()
const queryClient = useQueryClient()

const canWrite = computed(() => authStore.hasRole(['ADMIN', 'OPERATOR']))

const filterProductId = ref(null)
const firmwaresQueryKey = ['ota-firmwares']

const productsQuery = useQuery({
  queryKey: ['ota-product-options'],
  queryFn: async () => unwrapResult(await productApi.getList(), [])
})

const productOptions = computed(() => productsQuery.data.value ?? [])

const firmwaresQuery = useQuery({
  queryKey: computed(() => ['ota-firmwares', filterProductId.value]),
  queryFn: async () =>
    unwrapResult(
      await otaApi.listFirmwares(
        filterProductId.value ? { productId: filterProductId.value } : undefined
      ),
      []
    )
})

const firmwares = computed(() => firmwaresQuery.data.value ?? [])
const loading = computed(() => firmwaresQuery.isFetching.value)

const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)
const fileInputRef = ref(null)

const EMPTY_FORM = { productId: null, version: '', description: '' }
const form = reactive({ ...EMPTY_FORM })

watch(showUploadDialog, (open) => {
  if (!open) return
  Object.assign(form, EMPTY_FORM)
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
})

function openUploadDialog() {
  showUploadDialog.value = true
}

function triggerFilePick() {
  fileInputRef.value?.click()
}

function handleFileChange(event) {
  selectedFile.value = event.target.files?.[0] ?? null
}

function reload() {
  return firmwaresQuery.refetch()
}

function invalidate() {
  return queryClient.invalidateQueries({ queryKey: firmwaresQueryKey })
}

async function handleUpload() {
  if (!form.productId) {
    ElMessage.warning('请选择所属产品')
    return
  }
  const version = form.version.trim()
  if (!VERSION_PATTERN.test(version)) {
    ElMessage.warning('版本号仅支持字母、数字与 . _ -，长度 1~64')
    return
  }
  if (!selectedFile.value) {
    ElMessage.warning('请选择固件文件')
    return
  }
  if (selectedFile.value.size > MAX_FILE_SIZE) {
    ElMessage.warning(`固件文件不能超过 ${formatFileSize(MAX_FILE_SIZE)}`)
    return
  }

  const formData = new FormData()
  formData.append('productId', form.productId)
  formData.append('version', version)
  if (form.description.trim()) formData.append('description', form.description.trim())
  formData.append('file', selectedFile.value)

  uploading.value = true
  try {
    await otaApi.uploadFirmware(formData)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    await invalidate()
  } catch {
    // 命中业务码 6232 / 6233 / 6235 时拦截器已提示后端文案，这里不再重复弹窗
  } finally {
    uploading.value = false
  }
}

/** 触发浏览器附件下载（与 useThingModel 的导出范式一致）。 */
function triggerDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

async function downloadFirmware(row) {
  try {
    const blob = unwrapResult(await otaApi.downloadFirmware(row.id))
    triggerDownload(blob, row.fileName)
  } catch {
    // 具体原因由 axios 拦截器统一提示
  }
}

async function removeFirmware(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除固件「${row.productName} ${row.version}」吗？此操作不可恢复。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await otaApi.removeFirmware(row.id)
    ElMessage.success('固件已删除')
    await invalidate()
  } catch {
    // 命中业务码 6234（被升级任务引用）时拦截器已提示后端文案，这里不再重复弹窗
  }
}

function formatFileSize(size) {
  if (size === null || size === undefined || size === '') return '—'
  const bytes = Number(size)
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-item {
  width: 200px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.firmware-table-wrap {
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.firmware-table__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.dialog-form__control {
  width: 100%;
}

.dialog-form__file {
  display: none;
}

.dialog-form__file-picker {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
}

.dialog-form__file-name {
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dialog-form__hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>
