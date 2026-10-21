<template>
  <div class="hmi-create-page page">
    <PageHeader title="新建上位机" desc="选择设备后按物模型自动生成控件，预览确认后保存">
      <template #actions>
        <el-button @click="goBack">返回列表</el-button>
      </template>
    </PageHeader>

    <div class="create-steps card">
      <div :class="['create-step', { active: step >= 1, done: step > 1 }]">
        <span class="create-step__index">1</span>
        <span>选择设备</span>
      </div>
      <span class="create-step__line" />
      <div :class="['create-step', { active: step >= 2, done: step > 2 }]">
        <span class="create-step__index">2</span>
        <span>生成与调整</span>
      </div>
      <span class="create-step__line" />
      <div :class="['create-step', { active: step >= 3 }]">
        <span class="create-step__index">3</span>
        <span>保存运行</span>
      </div>
    </div>

    <div class="create-section card">
      <h3 class="card-title">1. 选择设备</h3>
      <p class="card-desc">一个上位机绑定一台设备；生成结果只包含该设备物模型定义的属性与服务。</p>
      <div class="device-picker">
        <el-select
          v-model="selectedDeviceId"
          filterable
          placeholder="选择要生成上位机的设备"
          :loading="devicesQuery.isLoading.value"
          class="device-select"
        >
          <el-option
            v-for="device in devices"
            :key="device.id"
            :value="device.id"
            :label="`${device.deviceName}（${device.deviceKey}）`"
          />
        </el-select>
        <el-button :disabled="!selectedDeviceId" :loading="generating" @click="generateDraft">
          重新生成布局
        </el-button>
      </div>
      <p v-if="devices.length === 0 && !devicesQuery.isLoading.value" class="empty-hint">
        当前账号还没有设备，请先到「设备管理」创建设备。
      </p>
    </div>

    <div v-if="generating" class="create-section card">正在按物模型生成...</div>

    <template v-else-if="draft">
      <div class="create-section card">
        <div class="preview-head">
          <div>
            <h3 class="card-title">2. 预览与命名</h3>
            <p class="card-desc">
              已为 {{ draft.deviceName }} 生成 {{ draft.config.widgets.length }} 个控件。
              保存后可在运行页继续编辑。
            </p>
          </div>
          <div class="name-row">
            <span class="field-label">名称</span>
            <el-input v-model="name" maxlength="64" class="name-input" />
          </div>
        </div>

        <el-alert
          v-for="(notice, index) in draft.notices"
          :key="index"
          type="warning"
          :closable="false"
          show-icon
          :title="notice"
          class="notice"
        />

        <HmiGrid
          :widgets="draft.config.widgets"
          :values="{}"
          :events="[]"
          :device-id="draft.deviceId"
          :device-name="draft.deviceName"
          preview
        />
      </div>

      <div class="create-footer">
        <el-button @click="generateDraft">换一种布局</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          保存并打开
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import HmiGrid from '@/components/hmi/HmiGrid.vue'
import { deviceApi } from '@/api/device'
import { hmiApi } from '@/api/hmi'
import { unwrapResult } from '@/utils/result'

const route = useRoute()
const router = useRouter()

const devicesQuery = useQuery({
  queryKey: ['hmi-device-options'],
  queryFn: async () => unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
})

const devices = computed(() => devicesQuery.data.value?.records || [])

const initialDeviceId = Number(route.query.deviceId)
const selectedDeviceId = ref(Number.isFinite(initialDeviceId) && initialDeviceId > 0 ? initialDeviceId : '')
const draft = ref(null)
const name = ref('')
const generating = ref(false)
const saving = ref(false)

const step = computed(() => {
  if (!selectedDeviceId.value) return 1
  return draft.value ? 3 : 2
})

async function generateDraft() {
  if (!selectedDeviceId.value) return
  generating.value = true
  draft.value = null
  try {
    const result = unwrapResult(await hmiApi.draft(selectedDeviceId.value))
    draft.value = result
    name.value = result.suggestedName || `${result.deviceName}上位机`
  } finally {
    generating.value = false
  }
}

watch(selectedDeviceId, (value) => {
  draft.value = null
  if (value) generateDraft()
}, { immediate: true })

async function save() {
  if (!draft.value) return
  if (!name.value.trim()) {
    ElMessage.warning('请填写上位机名称')
    return
  }
  saving.value = true
  try {
    const created = unwrapResult(
      await hmiApi.create({
        name: name.value.trim(),
        deviceId: draft.value.deviceId,
        config: JSON.stringify(draft.value.config)
      })
    )
    ElMessage.success('上位机已创建')
    router.push(`/workbench/hmi/${created.id}`)
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/workbench/hmi')
}
</script>

<style scoped>
.create-steps {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md) var(--spacing-lg);
}

.create-step {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.create-step.active {
  color: var(--color-text-primary);
}

.create-step.done {
  color: var(--color-primary);
}

.create-step__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  font-size: var(--font-size-xs);
}

.create-step.active .create-step__index {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.create-step.done .create-step__index {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.create-step__line {
  flex: 1;
  height: 1px;
  background: var(--border-color-light);
}

.create-section {
  margin-bottom: var(--grid-gutter);
}

.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-xs);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
  line-height: 1.6;
}

.device-picker {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  flex-wrap: wrap;
}

.device-select {
  width: 360px;
  max-width: 100%;
}

.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-lg);
  flex-wrap: wrap;
}

.name-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.field-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.name-input {
  width: 260px;
}

.notice {
  margin-bottom: var(--spacing-sm);
}

.create-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  padding-bottom: var(--spacing-xl);
}

.empty-hint {
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}
</style>
