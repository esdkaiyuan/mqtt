<template>
  <div class="device-property-trend">
    <h3 class="card-title">属性趋势</h3>
    <p class="card-desc">按物模型属性查看历史上报的聚合曲线，数据来自属性历史表的按时间桶聚合</p>

    <div class="trend-toolbar">
      <div class="trend-field">
        <span class="trend-field__label">属性</span>
        <el-select
          v-model="identifier"
          class="trend-field__control"
          size="small"
          placeholder="请选择属性"
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
      </div>

      <div class="trend-field">
        <span class="trend-field__label">粒度</span>
        <el-select
          v-model="form.bucket"
          class="trend-field__control trend-field__control--narrow"
          size="small"
          @change="submit"
        >
          <el-option
            v-for="item in BUCKET_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>

      <div class="trend-presets">
        <el-button
          v-for="preset in RANGE_PRESETS"
          :key="preset.hours"
          size="small"
          plain
          @click="applyPreset(preset.hours)"
        >
          {{ preset.label }}
        </el-button>
        <el-button size="small" link @click="reset">重置</el-button>
      </div>
    </div>

    <EmptyState
      v-if="!modelLoading && !propertyOptions.length"
      description="该产品尚未在物模型中定义属性，配置属性并上报后即可查看趋势"
      :image-size="80"
    />
    <PropertyTrendChart
      v-else
      :title="chartTitle"
      :series="series"
      :device-names="deviceNames"
      :aggregation="aggregation"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'
import { parseThingModel } from '@/utils/thingModel'
import { usePropertyHistory, BUCKET_OPTIONS, RANGE_PRESETS } from '@/composables/usePropertyHistory'
import EmptyState from '@/components/common/EmptyState.vue'
import PropertyTrendChart from '@/components/chart/PropertyTrendChart.vue'

/** 可作为 min / max / avg 聚合的物模型数据类型，其余（bool / enum / text ...）只统计样本数。 */
const NUMERIC_TYPES = ['int', 'float', 'double']

const props = defineProps({
  deviceId: { type: [Number, String], default: null },
  deviceName: { type: String, default: '' },
  productId: { type: [Number, String], default: null }
})

// 属性下拉来自该产品物模型；设备详情已带回 productId，无需额外接口
const modelQuery = useQuery({
  queryKey: computed(() => ['device-property-trend-model', props.productId]),
  queryFn: async () => {
    const data = unwrapResult(await productApi.getThingModel(props.productId), null)
    return parseThingModel(data?.thingModel)
  },
  enabled: computed(() => Boolean(props.productId))
})

const modelLoading = computed(() => modelQuery.isFetching.value)

const propertyOptions = computed(() =>
  (modelQuery.data.value?.properties ?? [])
    .filter((item) => Boolean(item.identifier))
    .map((item) => ({
      identifier: item.identifier,
      label: item.name ? `${item.name}（${item.identifier}）` : item.identifier,
      dataType: item.dataType?.type || ''
    }))
)

const identifier = ref('')

// 物模型到位后默认选中第一个属性，避免页面首屏为空
watch(
  propertyOptions,
  (options) => {
    if (!identifier.value && options.length) {
      identifier.value = options[0].identifier
    }
  },
  { immediate: true }
)

const deviceIds = computed(() => (props.deviceId ? [Number(props.deviceId)] : []))
const identifiers = computed(() => (identifier.value ? [identifier.value] : []))

const { form, series, submit, reset, applyPreset } = usePropertyHistory(deviceIds, identifiers)

// 数值型走聚合口径；非数值型后端恒返回 count，聚合字段无意义
const aggregation = computed(() =>
  NUMERIC_TYPES.includes(selectedDataType.value) ? 'avg' : 'count'
)

const selectedDataType = computed(
  () => propertyOptions.value.find((item) => item.identifier === identifier.value)?.dataType || ''
)

const deviceNames = computed(() => {
  if (!props.deviceId) return {}
  const name = props.deviceName || `设备 ${props.deviceId}`
  return { [Number(props.deviceId)]: name }
})

const chartTitle = computed(() => {
  const current = propertyOptions.value.find((item) => item.identifier === identifier.value)
  return current ? `属性趋势 · ${current.label}` : '属性趋势'
})
</script>

<style scoped>
.card-title {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  margin-bottom: var(--spacing-sm);
}

.card-desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
  margin-bottom: var(--spacing-md);
}

.trend-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.trend-field {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.trend-field__label {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.trend-field__control {
  width: 240px;
}

.trend-field__control--narrow {
  width: 120px;
}

.trend-presets {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-left: auto;
}

@media (max-width: 768px) {
  .trend-field__control,
  .trend-field__control--narrow {
    width: 100%;
  }

  .trend-presets {
    margin-left: 0;
  }
}
</style>
