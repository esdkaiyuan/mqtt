<template>
  <aside class="orch-inspector">
    <template v-if="node">
      <div class="orch-inspector__head">
        <div class="orch-inspector__heading">
          <p class="orch-inspector__title">{{ node.data?.title || node.type }}</p>
          <p class="orch-inspector__type">{{ typeLabel }}</p>
        </div>
        <el-button
          v-if="node.data?.removable"
          link
          type="danger"
          @click="emit('remove', { id: node.id })"
        >
          <svg-icon name="delete" :size="14" />
          删除
        </el-button>
      </div>

      <div class="orch-inspector__body">
        <slot :name="node.type" :node="node" :update="handleUpdate">
          <p class="orch-inspector__hint">该节点无需编辑。</p>
        </slot>
      </div>
    </template>

    <p v-else class="orch-inspector__hint">选中节点后在此编辑属性。</p>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import SvgIcon from '@/components/Icon.vue'
import { NODE_TYPE } from '@/utils/orchestration'

const props = defineProps({
  node: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update', 'remove'])

const TYPE_LABELS = {
  [NODE_TYPE.TRIGGER]: '触发节点',
  [NODE_TYPE.CONDITION_GROUP]: '条件组节点',
  [NODE_TYPE.CONDITION]: '条件节点',
  [NODE_TYPE.STEP]: '步骤节点',
  [NODE_TYPE.SCOPE]: '作用域节点',
  [NODE_TYPE.ACTION]: '动作节点'
}

const typeLabel = computed(() => TYPE_LABELS[props.node?.type] || '节点')

function handleUpdate(patch) {
  if (!props.node) return
  emit('update', { id: props.node.id, patch })
}
</script>

<style scoped>
.orch-inspector {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
  overflow-y: auto;
}

.orch-inspector__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.orch-inspector__heading {
  min-width: 0;
}

.orch-inspector__title {
  margin: 0;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.orch-inspector__type {
  margin: var(--spacing-2xs) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.orch-inspector__body {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.orch-inspector__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>