<template>
  <div class="board-list-page page">
    <PageHeader title="数据看板" desc="把常用设备属性趋势组合成看板，保存后可随时回看">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          新建看板
        </el-button>
      </template>
    </PageHeader>

    <div class="board-list">
      <div v-if="loading && !dashboards.length" class="loading-overlay">加载中...</div>

      <EmptyState
        v-else-if="loadError && !dashboards.length"
        description="看板列表加载失败，请稍后重试"
      >
        <el-button size="small" @click="refetch()">重试</el-button>
      </EmptyState>

      <EmptyState
        v-else-if="dashboards.length === 0"
        description="暂无看板，点击「新建看板」开始"
      />

      <div v-else class="boards-grid">
        <div
          v-for="board in dashboards"
          :key="board.id"
          class="board-card card"
          @click="openBoard(board)"
        >
          <div class="board-card__header">
            <div class="board-card__title-wrap">
              <h3 class="board-card__title">{{ board.name }}</h3>
              <span class="board-card__count">{{ board.panelCount }} 个面板</span>
            </div>
          </div>

          <div class="board-card__footer">
            <span class="board-card__time">更新于 {{ formatDateTime(board.updatedAt) }}</span>
            <div class="board-card__actions" @click.stop>
              <el-button link type="primary" @click="openRenameDialog(board)">
                <svg-icon name="edit" :size="14" />
                重命名
              </el-button>
              <el-button link type="danger" @click="removeBoard(board)">
                <svg-icon name="delete" :size="14" />
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="showDialog"
      :title="editingId ? '重命名看板' : '新建看板'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="看板名称">
          <el-input
            v-model="form.name"
            maxlength="64"
            show-word-limit
            placeholder="如：一号机房监控"
            @keyup.enter="handleSave"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ editingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SvgIcon from '@/components/Icon.vue'
import { dashboardApi } from '@/api/dashboard'
import { unwrapResult } from '@/utils/result'
import { useDashboards } from '@/composables/useDashboards'

const router = useRouter()

const { dashboards, loading, loadError, refetch, saving, create, update, remove } = useDashboards()

const showDialog = ref(false)
/** 非空表示重命名，空表示新建。 */
const editingId = ref(null)
const form = reactive({ name: '' })

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN')
}

function openCreateDialog() {
  editingId.value = null
  form.name = ''
  showDialog.value = true
}

function openRenameDialog(board) {
  editingId.value = board.id
  form.name = board.name
  showDialog.value = true
}

function openBoard(board) {
  router.push(`/workbench/boards/${board.id}`)
}

async function handleSave() {
  const name = form.name.trim()
  if (!name) {
    ElMessage.warning('请填写看板名称')
    return
  }

  try {
    if (editingId.value) {
      // 重命名只改名称，配置沿用服务端现值（后端按整份覆盖语义保存）
      const detail = await update(editingId.value, { name, config: await currentConfig(editingId.value) })
      if (detail === null) return
      ElMessage.success('看板已重命名')
    } else {
      await create({ name, config: { panels: [] } })
      ElMessage.success('看板已创建')
    }
    showDialog.value = false
  } catch {
    // 具体原因（如 6229 / 6230）由 axios 拦截器统一提示
  }
}

/**
 * 重命名时需要带着原配置整份提交（PUT 为覆盖语义），这里按需拉一次详情；
 * 拉取失败会抛给调用方，由上层保持弹窗不关闭。
 */
async function currentConfig(id) {
  const data = unwrapResult(await dashboardApi.detail(id), null)
  return data?.config ?? { panels: [] }
}

async function removeBoard(board) {
  try {
    await ElMessageBox.confirm(
      `删除后「${board.name}」的看板配置将不可恢复，确定继续吗？`,
      '确认删除看板',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await remove(board.id)
    ElMessage.success('看板已删除')
  } catch {
    // 具体原因由 axios 拦截器统一提示
  }
}
</script>

<style scoped>
.boards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--grid-gutter);
}

.board-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: var(--spacing-lg);
  cursor: pointer;
  transition: box-shadow var(--transition-base);
}

.board-card:hover {
  box-shadow: var(--shadow-card-hover);
}

.board-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.board-card__title-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-width: 0;
}

.board-card__title {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.board-card__count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.board-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--color-border-light);
}

.board-card__time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.board-card__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}
</style>
