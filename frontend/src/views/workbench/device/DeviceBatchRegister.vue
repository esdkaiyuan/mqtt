<template>
  <div class="device-batch-page page">
    <PageHeader
      title="批量注册"
      desc="按前缀与序号批量创建设备并签发凭据；对标主流 IoT 平台的「批量注册 → 激活 → 烧录凭据」流程"
    >
      <template #actions>
        <el-button @click="openHistory">注册批次记录</el-button>
      </template>
    </PageHeader>

    <div class="batch-layout">
      <!-- 左：注册表单 -->
      <div class="card batch-form">
        <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
          <el-form-item label="产品" prop="productId">
            <el-select v-model="form.productId" placeholder="选择产品" filterable style="width: 100%">
              <el-option
                v-for="p in products"
                :key="p.id"
                :label="`${p.productName} (${p.productKey})`"
                :value="p.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Key 前缀" prop="prefix">
            <el-input v-model="form.prefix" placeholder="如 sensor，将生成 sensor-1、sensor-2 …" />
            <div class="form-hint">仅允许字母、数字、中划线、下划线</div>
          </el-form-item>

          <el-form-item label="注册数量" prop="count">
            <el-input-number v-model="form.count" :min="1" :max="1000" :step="10" style="width: 100%" />
            <div class="form-hint">单批上限 1000；单台失败不阻断整批</div>
          </el-form-item>

          <el-form-item label="起始序号">
            <el-input-number v-model="form.startIndex" :min="0" style="width: 100%" />
          </el-form-item>

          <el-form-item label="设备类型" prop="deviceType">
            <el-input v-model="form.deviceType" placeholder="如 sensor / gateway / actuator" />
          </el-form-item>

          <el-form-item label="设备名称模板">
            <el-input v-model="form.namePattern" placeholder="留空则与 Key 同名；支持 {index}" />
          </el-form-item>

          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>

          <el-form-item label="签发激活码">
            <el-switch v-model="form.issueActivateCode" />
            <span class="form-hint form-hint--inline">
              开启后设备初始为禁用，需消费激活码方可连接
            </span>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">
              批量注册
            </el-button>
            <el-button @click="previewPreview">预览设备名</el-button>
          </el-form-item>
        </el-form>

        <!-- 预览：避免上千台注册后才发现前缀写错 -->
        <div v-if="previewText" class="batch-preview">
          <div class="batch-preview__title">将创建 {{ form.count }} 台，deviceKey形如：</div>
          <code>{{ previewText }}</code>
        </div>
      </div>

      <!-- 右：注册结果 -->
      <div class="card batch-result">
        <template v-if="!result">
          <el-empty description="尚未执行批量注册" />
        </template>

        <template v-else>
          <el-alert
            v-if="result.failedCount > 0"
            type="warning"
            :closable="false"
            show-icon
            :title="`成功 ${result.succeededCount} 台，失败 ${result.failedCount} 台`"
            style="margin-bottom: 16px"
          />
          <el-alert
            v-else
            type="success"
            :closable="false"
            show-icon
            :title="`成功注册 ${result.succeededCount} 台`"
            style="margin-bottom: 16px"
          />

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="批次号">
              <code>{{ result.batchNo }}</code>
            </el-descriptions-item>
          </el-descriptions>

          <el-alert
            type="error"
            :closable="false"
            show-icon
            title="凭据为一次性明文，仅本次返回，关闭后无法再取"
            style="margin: 16px 0"
          />

          <div class="result-actions">
            <el-button type="primary" @click="downloadCredentials">导出凭据 CSV</el-button>
            <el-button @click="result = null">清空</el-button>
          </div>

          <el-table v-if="result.credentials?.length" :data="result.credentials" size="small" max-height="280">
            <el-table-column prop="deviceKey" label="deviceKey" min-width="140" />
            <el-table-column prop="username" label="认证用户名" min-width="180" />
            <el-table-column prop="activateCode" label="激活码" min-width="160">
              <template #default="{ row }">
                <code v-if="row.activateCode">{{ row.activateCode }}</code>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="result.failedItems?.length" style="margin-top: 16px">
            <div class="result-section-title">失败明细</div>
            <el-table :data="result.failedItems" size="small" max-height="200">
              <el-table-column prop="index" label="序号" width="80" />
              <el-table-column prop="deviceKey" label="deviceKey" min-width="140" />
              <el-table-column prop="reason" label="原因" min-width="200" />
            </el-table>
          </div>
        </template>
      </div>
    </div>

    <!-- 批次历史 -->
    <el-dialog v-model="historyVisible" title="注册批次记录" width="900px">
      <el-table :data="history.rows" size="small" v-loading="history.loading">
        <el-table-column prop="batchNo" label="批次号" min-width="200" />
        <el-table-column prop="prefix" label="前缀" width="120" />
        <el-table-column prop="totalCount" label="总数" width="80" />
        <el-table-column prop="succeededCount" label="成功" width="80" />
        <el-table-column prop="failedCount" label="失败" width="80" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewBatch(row.batchNo)">查看凭据</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="history.page"
        :page-size="history.size"
        :total="history.total"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadHistory"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceRegistrationApi, buildCredentialsCsv, downloadTextFile } from '@/api/deviceRegistration'
import { productApi } from '@/api/product'

const formRef = ref()
const submitting = ref(false)
const products = ref([])
const result = ref(null)
const historyVisible = ref(false)
const previewText = ref('')

const form = reactive({
  productId: null,
  prefix: '',
  deviceType: 'sensor',
  count: 10,
  startIndex: 1,
  namePattern: '',
  description: '',
  issueActivateCode: true
})

const history = reactive({ rows: [], loading: false, page: 1, size: 20, total: 0 })

const rules = {
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  prefix: [
    { required: true, message: '请填写 Key 前缀', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_-]{1,48}$/,
      message: '仅允许字母、数字、中划线、下划线，长度 1~48',
      trigger: 'blur'
    }
  ],
  deviceType: [{ required: true, message: '请填写设备类型', trigger: 'blur' }]
}

// 预览只展示首尾各一个，中间省略，避免上千行撑爆页面
const preview = computed(() => {
  const start = form.startIndex ?? 1
  const count = form.count ?? 0
  if (!form.prefix || count < 1) return ''
  if (count <= 2) {
    return Array.from({ length: count }, (_, i) => `${form.prefix}-${start + i}`).join('、')
  }
  return `${form.prefix}-${start} … ${form.prefix}-${start + count - 1}`
})

function previewPreview() {
  previewText.value = preview.value
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    result.value = await deviceRegistrationApi.batchRegister({ ...form })
    if (result.value.succeededCount > 0) {
      ElMessage.success(`成功注册 ${result.value.succeededCount} 台，请及时导出凭据`)
    } else {
      ElMessage.warning('没有设备注册成功，请检查失败明细')
    }
  } finally {
    submitting.value = false
  }
}

function downloadCredentials() {
  if (!result.value?.credentials?.length) return
  const csv = buildCredentialsCsv(result.value)
  downloadTextFile(`device-credentials-${result.value.batchNo}.csv`, csv)
}

async function loadHistory() {
  history.loading = true
  try {
    const page = await deviceRegistrationApi.pageBatches({
      pageNum: history.page,
      pageSize: history.size
    })
    history.rows = page.records || []
    history.total = page.total || 0
  } finally {
    history.loading = false
  }
}

function openHistory() {
  historyVisible.value = true
  history.page = 1
  loadHistory()
}

async function viewBatch(batchNo) {
  const batch = await deviceRegistrationApi.getBatch(batchNo)
  let credentials = []
  if (batch.credentials) {
    try {
      credentials = JSON.parse(batch.credentials)
    } catch {
      credentials = []
    }
  }
  result.value = {
    batchNo: batch.batchNo,
    totalCount: batch.totalCount,
    succeededCount: batch.succeededCount,
    failedCount: batch.failedCount,
    credentials,
    failedItems: batch.failedItems ? JSON.parse(batch.failedItems) : []
  }
  historyVisible.value = false
  await ElMessageBox.alert('凭据为一次性内容，请确认环境安全后再导出', '查看历史凭据', {
    confirmButtonText: '我已知悉'
  })
}

onMounted(async () => {
  try {
    const page = await productApi.getList({ pageNum: 1, pageSize: 200 })
    products.value = page.records || []
  } catch {
    products.value = []
  }
})
</script>

<style scoped>
.batch-layout {
  display: grid;
  grid-template-columns: minmax(0, 420px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  line-height: 1.5;
}

.form-hint--inline {
  margin-left: 12px;
}

.batch-preview {
  margin-top: 8px;
  padding: 12px;
  background: var(--color-background-secondary);
  border-radius: var(--border-radius-md);
  font-size: 12px;
}

.batch-preview__title {
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}

.result-actions {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.result-section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-secondary);
  margin-bottom: 8px;
}

.text-muted {
  color: var(--color-text-tertiary);
}

@media (max-width: 1024px) {
  .batch-layout {
    grid-template-columns: 1fr;
  }
}
</style>
