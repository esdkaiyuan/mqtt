<template>
  <el-dialog
    :model-value="modelValue"
    title="导入设备台账"
    width="900px"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="import-dialog">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="CSV 列：deviceKey, deviceName, productKey, deviceType, topic, description, firmwareVersion"
        description="deviceKey / deviceName / productKey 为必需列；deviceType 缺省 sensor，topic 缺省 device/{deviceKey}/data，firmwareVersion 可空。单次最多 1000 行、2MB。"
      />

      <div class="import-dialog__actions">
        <el-button @click="downloadTemplate">下载模板</el-button>
        <input
          ref="fileInput"
          class="import-dialog__file"
          type="file"
          accept=".csv,text/csv"
          @change="handleFileChange"
        />
        <span v-if="file" class="import-dialog__filename">{{ file.name }}</span>
        <span v-else class="import-dialog__filename text-muted">未选择文件</span>
        <el-button type="primary" :loading="importing" :disabled="!file" @click="submit">
          开始导入
        </el-button>
        <el-button v-if="result" @click="reset">重新导入</el-button>
      </div>

      <template v-if="result">
        <el-alert
          :type="result.failedCount > 0 ? 'warning' : 'success'"
          :closable="false"
          show-icon
          :title="`共 ${result.totalCount} 行：成功 ${result.succeededCount} 台，失败 ${result.failedCount} 台`"
          style="margin-top: 16px"
        />

        <el-alert
          v-if="result.credentials?.length"
          type="error"
          :closable="false"
          show-icon
          title="设备密钥为一次性明文，仅本次返回；请立即导出凭据"
          style="margin: 12px 0"
        />

        <div v-if="result.credentials?.length" class="import-dialog__result-actions">
          <el-button type="primary" @click="downloadCredentials">导出凭据 CSV</el-button>
        </div>

        <el-table
          v-if="result.credentials?.length"
          :data="result.credentials"
          size="small"
          max-height="260"
          style="margin-top: 12px"
        >
          <el-table-column prop="deviceKey" label="deviceKey" min-width="150" />
          <el-table-column prop="username" label="认证用户名" min-width="200" />
          <el-table-column prop="secret" label="设备密钥（一次性）" min-width="220">
            <template #default="{ row }"><code>{{ row.secret }}</code></template>
          </el-table-column>
        </el-table>

        <template v-if="result.failedItems?.length">
          <div class="import-dialog__section-title">失败明细</div>
          <el-table :data="result.failedItems" size="small" max-height="220">
            <el-table-column prop="rowNumber" label="行号" width="80" />
            <el-table-column prop="deviceKey" label="deviceKey" min-width="150" />
            <el-table-column prop="reason" label="原因" min-width="260" />
          </el-table>
        </template>
      </template>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { deviceImportApi } from '@/api/deviceImport'
import { buildCredentialsCsv, downloadTextFile } from '@/api/deviceRegistration'
import { downloadResponse } from '@/utils/download'

defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const fileInput = ref()
const file = ref(null)
const importing = ref(false)
const result = ref(null)

function handleFileChange(event) {
  file.value = event.target.files?.[0] || null
  result.value = null
}

async function downloadTemplate() {
  try {
    const response = await deviceImportApi.downloadTemplate()
    downloadResponse(response, 'device-import-template.csv')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  }
}

async function submit() {
  if (!file.value) return
  importing.value = true
  try {
    result.value = await deviceImportApi.importFile(file.value)
    ElMessage.success(`导入完成：成功 ${result.value.succeededCount} 台`)
    emit('imported')
  } catch {
    // 错误提示由 axios 拦截器统一处理
  } finally {
    importing.value = false
  }
}

function downloadCredentials() {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')
  downloadTextFile(`device-credentials-${stamp}.csv`, buildCredentialsCsv(result.value))
}

function reset() {
  file.value = null
  result.value = null
  if (fileInput.value) fileInput.value.value = ''
}
</script>

<style scoped>
.import-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.import-dialog__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
}

.import-dialog__file {
  max-width: 260px;
  font-size: var(--font-size-sm);
}

.import-dialog__filename {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.import-dialog__result-actions {
  display: flex;
  justify-content: flex-end;
}

.import-dialog__section-title {
  margin: 16px 0 8px;
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}
</style>