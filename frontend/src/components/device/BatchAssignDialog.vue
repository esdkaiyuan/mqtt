<template>
  <el-dialog v-model="visible" :title="title" width="440px" :close-on-click-modal="false">
    <el-form label-position="top" class="dialog-form" @submit.prevent>
      <el-form-item :label="label" required>
        <el-select
          v-model="targetId"
          :placeholder="placeholder"
          filterable
          style="width: 100%"
        >
          <el-option
            v-for="option in options"
            :key="option.id"
            :label="option.name"
            :value="option.id"
          >
            <span v-if="option.color" class="assign-dot" :style="{ background: option.color }" />
            {{ option.name }}
          </el-option>
        </el-select>
        <p class="form-hint">将对 {{ count }} 台设备{{ actionText }}该{{ unit }}。</p>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">{{ actionText }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  /** group=分组关联；tag=标签关联 */
  mode: { type: String, default: 'group' },
  /** ADD=加入 / 打标；REMOVE=移出 / 去标 */
  action: { type: String, default: 'ADD' },
  count: { type: Number, default: 0 },
  options: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const targetId = ref(null)

const isGroup = computed(() => props.mode === 'group')
const isAdd = computed(() => props.action === 'ADD')
const unit = computed(() => (isGroup.value ? '分组' : '标签'))
const label = computed(() => (isGroup.value ? '目标分组' : '目标标签'))
const placeholder = computed(() => (isGroup.value ? '请选择分组' : '请选择标签'))

const actionText = computed(() => {
  if (isGroup.value) return isAdd.value ? '加入' : '移出'
  return isAdd.value ? '打标签' : '去标签'
})

const title = computed(() => `批量${actionText.value}`)

watch(visible, (open) => {
  if (open) targetId.value = null
})

function handleSubmit() {
  if (targetId.value === null || targetId.value === undefined) {
    ElMessage.warning(`请选择${label.value}`)
    return
  }
  emit('submit', targetId.value)
}
</script>

<style scoped>
.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.assign-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 6px;
  border-radius: 50%;
  vertical-align: middle;
}

.form-hint {
  margin-top: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>