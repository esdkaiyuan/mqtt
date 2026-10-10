<template>
  <div class="orch-canvas">
    <NodePalette v-if="palette.length" :items="palette" @add="handlePaletteAdd" />

    <div class="orch-canvas__stage" @dragover.prevent @drop="handleDrop">
      <VueFlow
        :nodes="nodes"
        :edges="edges"
        :nodes-connectable="false"
        :nodes-draggable="true"
        :min-zoom="0.2"
        :max-zoom="2"
        fit-view-on-init
        @node-click="handleNodeClick"
        @pane-click="handlePaneClick"
        @node-drag-stop="handleNodeDragStop"
      >
        <Background :gap="16" />
        <Controls />
        <MiniMap />

        <template #node-trigger="nodeProps">
          <TriggerNode v-bind="nodeProps" />
        </template>
        <template #node-conditionGroup="nodeProps">
          <ConditionGroupNode v-bind="nodeProps" />
        </template>
        <template #node-condition="nodeProps">
          <ConditionNode v-bind="nodeProps" />
        </template>
        <template #node-step="nodeProps">
          <StepNode v-bind="nodeProps" />
        </template>
        <template #node-scope="nodeProps">
          <ScopeNode v-bind="nodeProps" />
        </template>
        <template #node-action="nodeProps">
          <ActionNode v-bind="nodeProps" />
        </template>
      </VueFlow>
    </div>

    <NodeInspector :node="activeNode" @update="handleInspectorUpdate" @remove="handleInspectorRemove">
      <template v-if="$slots.trigger" #trigger="slotProps">
        <slot name="trigger" v-bind="slotProps" />
      </template>
      <template v-if="$slots.conditionGroup" #conditionGroup="slotProps">
        <slot name="conditionGroup" v-bind="slotProps" />
      </template>
      <template v-if="$slots.condition" #condition="slotProps">
        <slot name="condition" v-bind="slotProps" />
      </template>
      <template v-if="$slots.step" #step="slotProps">
        <slot name="step" v-bind="slotProps" />
      </template>
      <template v-if="$slots.scope" #scope="slotProps">
        <slot name="scope" v-bind="slotProps" />
      </template>
      <template v-if="$slots.action" #action="slotProps">
        <slot name="action" v-bind="slotProps" />
      </template>
    </NodeInspector>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import NodePalette from '@/components/orchestration/NodePalette.vue'
import NodeInspector from '@/components/orchestration/NodeInspector.vue'
import TriggerNode from '@/components/orchestration/nodes/TriggerNode.vue'
import ConditionGroupNode from '@/components/orchestration/nodes/ConditionGroupNode.vue'
import ConditionNode from '@/components/orchestration/nodes/ConditionNode.vue'
import StepNode from '@/components/orchestration/nodes/StepNode.vue'
import ScopeNode from '@/components/orchestration/nodes/ScopeNode.vue'
import ActionNode from '@/components/orchestration/nodes/ActionNode.vue'
import { NODE_DND_MIME } from '@/utils/orchestration'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

/**
 * 编排画布容器（T-29 设计文档 §7）。
 *
 * 只负责「渲染 nodes / edges + 面板拖入 + 节点选中 + 属性面板」，不含任何领域分支：
 * 节点 / 连线由调用方（场景 / 规则视图）投影 `form` 得到，事件原样抛出由调用方写回 `form`。
 */
const props = defineProps({
  nodes: {
    type: Array,
    required: true
  },
  edges: {
    type: Array,
    default: () => []
  },
  palette: {
    type: Array,
    default: () => []
  },
  // 受控选中节点（可选）；未传时容器自持选中状态
  inspector: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['node-add', 'node-remove', 'node-reorder', 'node-update', 'node-select'])

const internalSelectedId = ref(null)

// 在本组件作用域内调用会把 store 提供给 <VueFlow> 子组件，二者共用同一实例（T-29 §10.3 居中定位所需）。
const { findNode, fitView, addSelectedNodes, removeSelectedElements } = useVueFlow()

const selectedId = computed(() => props.inspector?.id ?? internalSelectedId.value)
const activeNode = computed(
  () => props.nodes.find((node) => node.id === selectedId.value) ?? null
)

/**
 * 选中并居中指定节点（校验错误定位，T-29 设计文档 §10.3）。
 * 返回是否命中节点；未命中时调用方无需提示。
 */
function focusNode(id) {
  const node = findNode(id)
  if (!node) return false
  internalSelectedId.value = id
  removeSelectedElements()
  addSelectedNodes([node])
  fitView({ nodes: [id], duration: 300, maxZoom: 1.2, padding: 0.3 })
  emit('node-select', { id })
  return true
}

defineExpose({ focusNode })

function handleNodeClick({ node }) {
  internalSelectedId.value = node.id
  emit('node-select', { id: node.id })
}

function handlePaneClick() {
  internalSelectedId.value = null
}

/** 仅可重排节点（步骤）在拖拽结束时上报新坐标，由调用方换算为新的顺序。 */
function handleNodeDragStop({ node }) {
  if (!node?.data?.reorderable) return
  emit('node-reorder', { id: node.id, position: node.position })
}

function handlePaletteAdd({ type }) {
  emit('node-add', { type })
}

function handleDrop(event) {
  const type = event.dataTransfer?.getData(NODE_DND_MIME)
  if (type) emit('node-add', { type })
}

function handleInspectorUpdate(payload) {
  emit('node-update', payload)
}

function handleInspectorRemove(payload) {
  emit('node-remove', payload)
}
</script>

<style scoped>
.orch-canvas {
  display: flex;
  gap: var(--spacing-sm);
  height: 100%;
  min-height: 420px;
}

.orch-canvas__stage {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-bg);
  overflow: hidden;
}

.orch-canvas__stage :deep(.vue-flow) {
  height: 100%;
}
</style>