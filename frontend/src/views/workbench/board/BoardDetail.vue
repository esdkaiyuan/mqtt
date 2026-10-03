<template>
  <div class="board-detail-page page">
    <PageHeader :title="title" :desc="desc">
      <template #actions>
        <el-button @click="goBack">返回</el-button>
        <template v-if="editing">
          <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </template>
        <el-button v-else type="primary" :disabled="!detail" @click="startEdit">
          <svg-icon name="edit" :size="16" />
          编辑看板
        </el-button>
      </template>
    </PageHeader>

    <div class="board-detail">
      <div v-if="loading && !detail" class="loading-overlay">加载中...</div>

      <EmptyState
        v-else-if="loadError && !detail"
        description="看板加载失败，请稍后重试"
      >
        <el-button size="small" @click="refetch()">重试</el-button>
      </EmptyState>

      <template v-else-if="detail">
        <div v-if="editing" class="board-detail__toolbar">
          <el-button size="small" type="primary" plain @click="openAddPanel">
            <svg-icon name="add" :size="14" />
            添加面板
          </el-button>
          <span class="board-detail__hint">编辑中的改动仅在点击「保存」后写入看板</span>
        </div>

        <div v-if="displayPanels.length" class="board-detail__grid">
          <div
            v-for="(panel, index) in displayPanels"
            :key="panelKey(panel, index)"
            class="panel-card card"
          >
            <div v-if="editing" class="panel-card__toolbar">
              <el-button link type="primary" @click="openEditPanel(panel, index)">
                <svg-icon name="edit" :size="14" />
                编辑
              </el-button>
              <el-button link type="danger" @click="removePanel(index)">
                <svg-icon name="delete" :size="14" />
                删除
              </el-button>
            </div>
            <BoardPanel :panel="panel" :device-names="deviceNames" />
          </div>
        </div>

        <EmptyState
          v-else
          :description="editing ? '还没有面板，点击上方「添加面板」开始' : '该看板还没有面板'"
        />
      </template>
    </div>

    <BoardPanelEditor
      v-model:visible="editorVisible"
      :panel="editorPanel"
      :device-options="deviceOptions"
      :device-loading="deviceLoading"
      @submit="handlePanelSubmit"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SvgIcon from '@/components/Icon.vue'
import BoardPanel from '@/components/dashboard/BoardPanel.vue'
import BoardPanelEditor from '@/components/dashboard/BoardPanelEditor.vue'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'
import { useDashboardDetail } from '@/composables/useDashboards'

const route = useRoute()
const router = useRouter()

const id = computed(() => route.params.id)
const { detail, config, loading, loadError, refetch, saving, save } = useDashboardDetail(id)

/** 编辑态：本地维护面板副本，保存时才整份 PUT 覆盖。 */
const editing = ref(false)
const draftPanels = ref([])

const editorVisible = ref(false)
/** 非空表示编辑第几个面板，null 表示新增。 */
const editingIndex = ref(null)
const editorPanel = ref(null)

/** 设备列表：既供面板编辑器选择，也用于把 deviceId 映射成图例名称。 */
const devicesQuery = useQuery({
  queryKey: ['board-device-options'],
  queryFn: async () => unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
})

const deviceOptions = computed(() => devicesQuery.data.value?.records || [])
const deviceLoading = computed(() => devicesQuery.isFetching.value)

const deviceNames = computed(() =>
  Object.fromEntries(
    deviceOptions.value.map((item) => [
      item.id,
      item.deviceName || item.deviceKey || `设备 ${item.id}`
    ])
  )
)

const title = computed(() => detail.value?.name || '看板详情')

const displayPanels = computed(() => (editing.value ? draftPanels.value : config.value.panels))

const desc = computed(() =>
  displayPanels.value.length ? `共 ${displayPanels.value.length} 个面板` : '看板内还没有面板'
)

/** 面板配置变化时更换 key，强制重建 BoardPanel（其时间窗仅在创建时取一次）。 */
function panelKey(panel, index) {
  return `${index}-${JSON.stringify(panel)}`
}

function startEdit() {
  draftPanels.value = JSON.parse(JSON.stringify(config.value.panels))
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  draftPanels.value = []
}

function openAddPanel() {
  editingIndex.value = null
  editorPanel.value = null
  editorVisible.value = true
}

function openEditPanel(panel, index) {
  editingIndex.value = index
  editorPanel.value = panel
  editorVisible.value = true
}

function handlePanelSubmit(panel) {
  if (editingIndex.value === null) {
    draftPanels.value.push(panel)
  } else {
    draftPanels.value[editingIndex.value] = panel
  }
  editingIndex.value = null
  editorPanel.value = null
}

async function removePanel(index) {
  const panel = draftPanels.value[index]
  try {
    await ElMessageBox.confirm(
      `确定移除面板「${panel?.title || '未命名面板'}」吗？保存后生效。`,
      '确认移除面板',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  draftPanels.value.splice(index, 1)
}

async function handleSave() {
  const saved = await save(detail.value.name, { panels: draftPanels.value })
  if (saved === null) return
  editing.value = false
  draftPanels.value = []
  ElMessage.success('看板已保存')
}

function goBack() {
  router.push('/workbench/boards')
}
</script>

<style scoped>
.board-detail__toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
}

.board-detail__hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.board-detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(480px, 1fr));
  gap: var(--grid-gutter);
}

.panel-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg);
  min-width: 0;
}

.panel-card__toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-xs);
}

@media (max-width: 640px) {
  .board-detail__grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
