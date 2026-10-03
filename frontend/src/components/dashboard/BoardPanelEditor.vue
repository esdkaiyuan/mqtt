<template>
  <el-dialog
    :model-value="visible"
    :title="panel ? '编辑面板' : '新增面板'"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="handleVisible"
  >
    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item label="面板标题">
        <el-input
          v-model="form.title"
          maxlength="64"
          show-word-limit
          placeholder="如：机房温度趋势"
        />
      </el-form-item>

      <el-form-item label="设备（可多选）">
        <el-select
          v-model="form.deviceIds"
          class="editor-full"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          placeholder="请选择设备"
          :loading="deviceLoading"
        >
          <el-option
            v-for="item in deviceOptions"
            :key="item.id"
            :label="item.deviceName || item.deviceKey || `设备 ${item.id}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="属性">
        <el-select
          v-model="form.identifier"
          class="editor-full"
          filterable
          placeholder="请先选择设备"
          :loading="modelLoading"
          :disabled="!propertyOptions.length"
        >
          <el-option
            v-for="item in propertyOptions"
            :key="item.identifier"
            :label="item.label"
            :value="item.identifier"
          />
        </el-select>
        <p v-if="emptyModelHint" class="editor-hint">{{ emptyModelHint }}</p>
      </el-form-item>

      <div class="editor-grid">
        <el-form-item label="图表类型">
          <el-select v-model="form.chartType" class="editor-full">
            <el-option
              v-for="item in CHART_TYPE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="聚合方式">
          <el-select v-model="form.aggregation" class="editor-full">
            <el-option
              v-for="item in AGGREGATION_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="统计粒度">
          <el-select v-model="form.bucket" class="editor-full">
            <el-option
              v-for="item in BUCKET_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="默认跨度（天）">
          <el-input-number
            v-model="form.rangeDays"
            class="editor-full"
            :min="1"
            :max="MAX_RANGE_DAYS"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="handleVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'
import { parseThingModel } from '@/utils/thingModel'
import { BUCKET_OPTIONS } from '@/composables/usePropertyHistory'
import {
  AGGREGATION_OPTIONS,
  CHART_TYPE_OPTIONS,
  createDefaultPanel
} from '@/composables/useDashboards'

/** 面板默认跨度上限，与后端 app.property-history.max-range-days 默认值一致。 */
const MAX_RANGE_DAYS = 31

const props = defineProps({
  visible: { type: Boolean, default: false },
  /** 待编辑面板（DashboardConfig.Panel）；为空表示新增。 */
  panel: { type: Object, default: null },
  /** 可选设备：Device[]（id / deviceName / deviceKey / productId）。 */
  deviceOptions: { type: Array, default: () => [] },
  deviceLoading: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'submit'])

const form = reactive(createDefaultPanel())

/** 每次打开（或切换编辑对象）时以副本重置草稿，取消不会污染外部面板对象。 */
watch(
  () => [props.visible, props.panel],
  () => {
    if (!props.visible) return
    Object.assign(
      form,
      createDefaultPanel(),
      props.panel ? JSON.parse(JSON.stringify(props.panel)) : {}
    )
  },
  { immediate: true }
)

const selectedDevices = computed(() =>
  props.deviceOptions.filter((item) => form.deviceIds.includes(item.id))
)

/** 所选设备的产品集合（去重）；属性下拉取其物模型属性的交集。 */
const productIds = computed(() => [
  ...new Set(
    selectedDevices.value
      .map((item) => item.productId)
      .filter((id) => id !== null && id !== undefined)
  )
])

const modelsQuery = useQuery({
  queryKey: computed(() => ['board-panel-models', productIds.value]),
  queryFn: async () =>
    Promise.all(
      productIds.value.map(async (productId) => {
        const data = unwrapResult(await productApi.getThingModel(productId), null)
        return parseThingModel(data?.thingModel)
      })
    ),
  enabled: computed(() => productIds.value.length > 0)
})

const modelLoading = computed(() => modelsQuery.isFetching.value)

/** 共有属性：只在所有所选产品都定义时才可选，避免跨产品拼出取不到数据的序列。 */
const propertyOptions = computed(() => {
  const models = modelsQuery.data.value
  if (!models?.length) return []
  const [first, ...rest] = models
  return (first.properties || [])
    .filter((item) => Boolean(item.identifier))
    .filter((item) =>
      rest.every((model) =>
        (model.properties || []).some((candidate) => candidate.identifier === item.identifier)
      )
    )
    .map((item) => ({
      identifier: item.identifier,
      label: item.name ? `${item.name}（${item.identifier}）` : item.identifier
    }))
})

/** 设备或物模型变化后，已选属性若不在候选中则清空，避免提交后被后端判为非法（6229）。 */
watch(propertyOptions, (options) => {
  if (form.identifier && !options.some((item) => item.identifier === form.identifier)) {
    form.identifier = ''
  }
})

/** 已选设备但无共有属性时的引导文案（含「所选设备未配置产品」的情形）。 */
const emptyModelHint = computed(() => {
  if (modelLoading.value || !form.deviceIds.length || propertyOptions.value.length) return ''
  return selectedDevices.value.some((item) => !item.productId)
    ? '所选设备尚未绑定产品，无法读取物模型属性'
    : '所选设备在产品物模型中没有共有属性，请先在产品中配置物模型'
})

/** 提交前的前端兜底校验；服务端仍会二次校验（非法配置返回 6229）。 */
function validate() {
  if (!form.deviceIds.length) return '请至少选择一台设备'
  if (!form.identifier) return '请选择属性'
  if (!form.title.trim()) return '请填写面板标题'
  return ''
}

function handleSubmit() {
  const message = validate()
  if (message) {
    ElMessage.warning(message)
    return
  }
  emit('submit', {
    title: form.title.trim(),
    deviceIds: [...form.deviceIds],
    identifier: form.identifier,
    bucket: form.bucket,
    rangeDays: form.rangeDays,
    chartType: form.chartType,
    aggregation: form.aggregation
  })
  emit('update:visible', false)
}

function handleVisible(value) {
  emit('update:visible', value)
}
</script>

<style scoped>
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.editor-full {
  width: 100%;
}

.editor-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--spacing-md);
}

.editor-hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  line-height: 1.5;
  color: var(--color-text-tertiary);
}
</style>
