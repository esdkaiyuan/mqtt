<template>
  <el-dialog
    :model-value="modelValue"
    title="场景试运行（干跑）"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <el-form label-position="top" @submit.prevent>
      <template v-if="!timerSource">
        <el-form-item label="设备" required>
          <el-select v-model="form.deviceId" class="test-control" placeholder="选择触发设备">
            <el-option
              v-for="device in devices"
              :key="device.id"
              :label="device.deviceName || device.deviceKey"
              :value="device.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标识符" required>
          <el-input v-model="form.identifier" placeholder="待判定的属性 / 事件标识符" />
        </el-form-item>
        <el-form-item label="取值">
          <el-input v-model="form.value" placeholder="数值比较符下填数字，可留空" />
        </el-form-item>
      </template>
      <p v-else class="test-hint">定时触发无需提供触发上下文，直接按当前时间评估条件组。</p>
    </el-form>

    <div v-if="result" class="test-result">
      <div class="test-row">
        <span class="test-label">触发匹配</span>
        <el-tag :type="result.triggerMatched ? 'success' : 'info'" size="small">
          {{ result.triggerMatched ? '命中' : '未命中' }}
        </el-tag>
      </div>
      <div class="test-row">
        <span class="test-label">条件满足</span>
        <el-tag :type="result.conditionMatched ? 'success' : 'warning'" size="small">
          {{ result.conditionMatched ? '满足' : '不满足' }}
        </el-tag>
      </div>
      <div class="test-row">
        <span class="test-label">冷却拦截</span>
        <span class="test-value">{{ result.cooldownBlocked ? '是' : '否' }}</span>
      </div>
      <div class="test-row">
        <span class="test-label">预计总耗时</span>
        <span class="test-value">{{ result.estimatedDurationSeconds ?? 0 }} 秒</span>
      </div>

      <div v-if="conditions.length" class="test-block">
        <div class="test-block__title">条件明细</div>
        <el-table :data="conditions" size="small" border>
          <el-table-column prop="identifier" label="标识符" min-width="110" />
          <el-table-column label="比较" width="90">
            <template #default="{ row }">
              {{ OPERATOR_LABELS[row.operator] || row.operator }}
            </template>
          </el-table-column>
          <el-table-column prop="threshold" label="阈值" width="80" />
          <el-table-column prop="actualValue" label="实际值" width="90" />
          <el-table-column label="结论" width="80">
            <template #default="{ row }">
              <el-tag :type="row.satisfied ? 'success' : 'danger'" size="small">
                {{ row.satisfied ? '满足' : '不满足' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="stepSummaries.length" class="test-block">
        <div class="test-block__title">步骤摘要</div>
        <el-table :data="stepSummaries" size="small" border>
          <el-table-column prop="seq" label="#" width="50" />
          <el-table-column label="动作" width="110">
            <template #default="{ row }">
              {{ ACTION_TYPE_LABELS[row.actionType] || row.actionType }}
            </template>
          </el-table-column>
          <el-table-column label="目标" width="110">
            <template #default="{ row }">
              {{ TARGET_TYPE_LABELS[row.targetType] || row.targetType }}
              <span v-if="row.targetDeviceCount"> · {{ row.targetDeviceCount }} 台</span>
            </template>
          </el-table-column>
          <el-table-column label="延时" width="70">
            <template #default="{ row }">{{ row.delaySeconds ?? 0 }}s</template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :loading="testing" @click="handleTest">开始试运行</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useSceneTestMutation } from '@/composables/useScenes'
import { ACTION_TYPE_LABELS, TARGET_TYPE_LABELS, OPERATOR_LABELS } from '@/utils/scene'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  // 已保存场景的 id，仅存在时才可试运行（设计文档 §7.5）
  sceneId: {
    type: [Number, String],
    default: null
  },
  triggerType: {
    type: String,
    default: 'PROPERTY'
  },
  devices: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const { testing, testScene } = useSceneTestMutation()

const timerSource = computed(() => props.triggerType === 'TIMER')

const form = reactive({ deviceId: null, identifier: '', value: '' })
const result = ref(null)

const conditions = computed(() => result.value?.conditions ?? [])
const stepSummaries = computed(() => result.value?.stepSummaries ?? [])

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    form.deviceId = null
    form.identifier = ''
    form.value = ''
    result.value = null
  },
  { immediate: true }
)

async function handleTest() {
  if (!props.sceneId) {
    ElMessage.warning('请先保存场景后再试运行')
    return
  }
  if (!timerSource.value) {
    if (!form.deviceId) {
      ElMessage.warning('请选择触发设备')
      return
    }
    if (!form.identifier.trim()) {
      ElMessage.warning('请填写标识符')
      return
    }
  }
  const payload = timerSource.value
    ? {}
    : {
        deviceId: form.deviceId,
        identifier: form.identifier.trim(),
        value: form.value.trim() === '' ? null : form.value.trim()
      }
  try {
    result.value = await testScene(props.sceneId, payload)
  } catch {
    // 失败原因由 axios 拦截器统一提示
  }
}
</script>

<style scoped>
.test-control {
  width: 100%;
}

.test-hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.test-result {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius);
}

.test-row {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  font-size: var(--font-size-sm);
}

.test-label {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.test-value {
  color: var(--color-text-primary);
  text-align: right;
  word-break: break-all;
}

.test-block {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.test-block__title {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
}
</style>
