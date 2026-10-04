<template>
  <div class="scene-center page">
    <PageHeader title="场景联动" desc="零代码配置多设备联动：设备属性 / 事件 / 定时触发，按条件组合执行动作步骤流">
      <template #actions>
        <el-button type="primary" @click="openCreate">
          <svg-icon name="add" :size="16" />
          新建场景
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="scene-center__tabs">
      <el-tab-pane label="场景定义" name="definitions">
        <div v-if="activeTab === 'definitions'" class="scene-definitions">
          <div class="filter-bar">
            <el-select
              :model-value="filters.triggerType"
              placeholder="全部触发源"
              clearable
              class="filter-item"
              @update:model-value="(value) => setFilter('triggerType', value)"
            >
              <el-option
                v-for="option in TRIGGER_SOURCE_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-select
              :model-value="filters.enabled"
              placeholder="全部状态"
              clearable
              class="filter-item"
              @update:model-value="(value) => setFilter('enabled', value)"
            >
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
            <el-input
              :model-value="filters.keyword"
              placeholder="搜索场景名称"
              clearable
              class="filter-item filter-item--wide"
              @update:model-value="(value) => setFilter('keyword', value)"
            />
            <div class="filter-actions">
              <el-button @click="resetFilters">重置</el-button>
              <el-button :loading="loading" @click="refresh">
                <svg-icon name="refresh" :size="14" />
                刷新
              </el-button>
            </div>
          </div>

          <div v-if="loading && scenes.length === 0" class="loading-overlay">加载中...</div>

          <EmptyState
            v-else-if="scenes.length === 0"
            description="暂无场景，点击「新建场景」开始配置多设备联动"
          />

          <template v-else>
            <div class="scene-table card">
              <el-table :data="scenes" style="width: 100%">
                <el-table-column prop="name" label="场景名称" min-width="140" show-overflow-tooltip />
                <el-table-column label="触发源" width="100">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      effect="plain"
                      :type="TRIGGER_SOURCE_TAG_TYPES[row.triggerType] || 'info'"
                    >
                      {{ TRIGGER_SOURCE_LABELS[row.triggerType] || row.triggerType }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="触发条件" min-width="180">
                  <template #default="{ row }">
                    <span class="scene-mono">{{ formatTriggerSummary(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="条件数" width="80" align="center">
                  <template #default="{ row }">{{ (row.conditions || []).length }}</template>
                </el-table-column>
                <el-table-column label="冷却" width="80">
                  <template #default="{ row }">
                    <span>{{ row.cooldownSeconds > 0 ? `${row.cooldownSeconds}s` : '不限' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="最近触发" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.lastTriggeredAt) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-switch
                      :model-value="row.enabled === 1"
                      :loading="togglingId === row.id"
                      @change="(value) => handleToggle(row, value)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="210" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                    <el-button link type="primary" @click="handleRun(row)">执行</el-button>
                    <el-button link type="primary" @click="openTest(row)">试运行</el-button>
                    <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <EmptyState description="暂无场景" :image-size="80" />
                </template>
              </el-table>
            </div>

            <div class="scene-pagination">
              <el-pagination
                layout="total, sizes, prev, pager, next"
                :current-page="currentPage"
                :page-size="pageSize"
                :page-sizes="[10, 20, 50]"
                :total="total"
                background
                @current-change="goToPage"
                @size-change="changePageSize"
              />
            </div>
          </template>
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行记录" name="executions">
        <div v-if="activeTab === 'executions'" class="scene-executions">
          <div class="filter-bar">
            <el-select
              :model-value="executionFilters.sceneId"
              placeholder="全部场景"
              clearable
              filterable
              class="filter-item filter-item--wide"
              @update:model-value="(value) => setExecutionFilter('sceneId', value)"
            >
              <el-option
                v-for="scene in sceneOptions"
                :key="scene.id"
                :label="scene.name"
                :value="scene.id"
              />
            </el-select>
            <el-select
              :model-value="executionFilters.deviceId"
              placeholder="全部设备"
              clearable
              filterable
              class="filter-item"
              @update:model-value="(value) => setExecutionFilter('deviceId', value)"
            >
              <el-option
                v-for="device in deviceOptions"
                :key="device.id"
                :label="device.deviceName || device.deviceKey"
                :value="device.id"
              />
            </el-select>
            <el-select
              :model-value="executionFilters.status"
              placeholder="全部状态"
              clearable
              class="filter-item"
              @update:model-value="(value) => setExecutionFilter('status', value)"
            >
              <el-option
                v-for="option in EXECUTION_STATUS_OPTIONS"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <div class="filter-actions">
              <el-button @click="resetExecutionFilters">重置</el-button>
              <el-button :loading="executionLoading" @click="refreshExecutions">
                <svg-icon name="refresh" :size="14" />
                刷新
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="hasActive"
            class="pending-hint"
            type="info"
            :closable="false"
            show-icon
            title="存在待执行 / 执行中记录，列表将每 3 秒自动刷新直至完成"
          />

          <div v-if="executionLoading && executions.length === 0" class="loading-overlay">加载中...</div>

          <EmptyState
            v-else-if="executions.length === 0"
            description="暂无执行记录"
          />

          <template v-else>
            <div class="execution-table card">
              <el-table :data="executions" style="width: 100%">
                <el-table-column prop="sceneName" label="场景" min-width="130" show-overflow-tooltip />
                <el-table-column label="触发源" width="100">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      effect="plain"
                      :type="TRIGGER_SOURCE_TAG_TYPES[row.triggerType] || 'info'"
                    >
                      {{ TRIGGER_SOURCE_LABELS[row.triggerType] || row.triggerType }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="设备" min-width="120">
                  <template #default="{ row }">
                    <span>{{ row.triggerDeviceName || row.triggerDeviceKey || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="触发标识符" min-width="120">
                  <template #default="{ row }">
                    <span class="scene-mono">{{ row.triggerIdentifier || '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="触发值" min-width="100">
                  <template #default="{ row }">
                    <span class="scene-mono">{{ row.triggerValue ?? '—' }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="方式" width="80">
                  <template #default="{ row }">
                    {{ EXECUTION_TRIGGER_SOURCE_LABELS[row.triggerSource] || row.triggerSource || '—' }}
                  </template>
                </el-table-column>
                <el-table-column label="进度" width="80" align="center">
                  <template #default="{ row }">{{ row.finishedSteps ?? 0 }}/{{ row.totalSteps ?? 0 }}</template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="EXECUTION_STATUS_TAG_TYPES[row.status] || 'info'">
                      {{ EXECUTION_STATUS_LABELS[row.status] || row.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="创建时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="130" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click="openDetail(row)">详情</el-button>
                    <el-button
                      v-if="row.status === 'FAILED'"
                      link
                      type="primary"
                      :loading="retryingId === row.id"
                      @click="handleRetry(row)"
                    >
                      重试
                    </el-button>
                  </template>
                </el-table-column>
                <template #empty>
                  <EmptyState description="暂无执行记录" :image-size="80" />
                </template>
              </el-table>
            </div>

            <div class="scene-pagination">
              <el-pagination
                layout="total, sizes, prev, pager, next"
                :current-page="executionPage"
                :page-size="executionPageSize"
                :page-sizes="[10, 20, 50]"
                :total="executionTotal"
                background
                @current-change="goToExecutionPage"
                @size-change="changeExecutionPageSize"
              />
            </div>
          </template>
        </div>
      </el-tab-pane>
    </el-tabs>

    <SceneFormDialog v-model="formVisible" :scene="editingScene" @saved="handleSaved" />
    <SceneTestDialog
      v-model="testVisible"
      :scene-id="testSceneId"
      :trigger-type="testTriggerType"
      :devices="deviceOptions"
    />
    <SceneExecutionDrawer
      v-model="drawerVisible"
      :execution-id="detailExecutionId"
      @retried="refreshExecutions"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SceneFormDialog from '@/components/scene/SceneFormDialog.vue'
import SceneTestDialog from '@/components/scene/SceneTestDialog.vue'
import SceneExecutionDrawer from '@/components/scene/SceneExecutionDrawer.vue'
import { deviceApi } from '@/api/device'
import { sceneApi } from '@/api/scene'
import { unwrapResult } from '@/utils/result'
import { useSceneListQuery, useSceneMutations, useSceneRunMutation } from '@/composables/useScenes'
import { useSceneExecutionsQuery, useSceneExecutionRetryMutation } from '@/composables/useSceneExecutions'
import {
  TRIGGER_SOURCE_LABELS,
  TRIGGER_SOURCE_TAG_TYPES,
  TRIGGER_SOURCE_OPTIONS,
  EXECUTION_STATUS_LABELS,
  EXECUTION_STATUS_TAG_TYPES,
  EXECUTION_STATUS_OPTIONS,
  formatTriggerSummary,
  formatDateTime
} from '@/utils/scene'

// 页签切换懒加载，避免执行记录页在未激活时发起无效查询（与规则中心一致）
const activeTab = ref('definitions')

/** 触发方式（AUTO/MANUAL）与「触发源」（PROPERTY/EVENT/TIMER）口径不同，单独映射。 */
const EXECUTION_TRIGGER_SOURCE_LABELS = { AUTO: '自动', MANUAL: '手动' }

// ---- 场景定义 ----
const {
  filters,
  currentPage,
  pageSize,
  scenes,
  total,
  loading,
  setFilter,
  resetFilters,
  goToPage,
  changePageSize,
  refresh
} = useSceneListQuery()

const { setEnabled, deleteScene } = useSceneMutations()
const { runScene } = useSceneRunMutation()

const formVisible = ref(false)
const editingScene = ref(null)
const togglingId = ref(null)
const runningId = ref(null)

const testVisible = ref(false)
const testSceneId = ref(null)
const testTriggerType = ref('PROPERTY')

function openCreate() {
  editingScene.value = null
  formVisible.value = true
}

function openEdit(scene) {
  editingScene.value = scene
  formVisible.value = true
}

function openTest(scene) {
  testSceneId.value = scene.id
  testTriggerType.value = scene.triggerType
  testVisible.value = true
}

function handleSaved() {
  refresh()
}

/** 启停开关：仅切换 enabled，成功后失效场景缓存；失败时开关回弹原值。 */
async function handleToggle(scene, value) {
  togglingId.value = scene.id
  try {
    await setEnabled(scene.id, value)
    ElMessage.success(value ? '场景已启用' : '场景已停用')
  } catch {
    // 失败原因由拦截器提示
  } finally {
    togglingId.value = null
  }
}

/** 手动执行一次（真实触发）。 */
async function handleRun(scene) {
  try {
    await ElMessageBox.confirm(
      `确认立即手动执行场景「${scene.name}」？将真实下发动作步骤。`,
      '手动执行',
      { confirmButtonText: '执行', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  runningId.value = scene.id
  try {
    await runScene(scene.id)
    ElMessage.success('已提交执行，请到「执行记录」查看进度')
  } catch {
    // 无启用步骤时返回 6241
  } finally {
    runningId.value = null
  }
}

async function handleDelete(scene) {
  try {
    await ElMessageBox.confirm(`确定要删除场景「${scene.name}」吗？历史执行记录保留。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteScene(scene.id)
    ElMessage.success('场景已删除')
  } catch {
    // 失败原因由拦截器提示
  }
}

// ---- 执行记录 ----
const {
  filters: executionFilters,
  currentPage: executionPage,
  pageSize: executionPageSize,
  executions,
  total: executionTotal,
  loading: executionLoading,
  hasActive,
  setFilter: setExecutionFilter,
  resetFilters: resetExecutionFilters,
  goToPage: goToExecutionPage,
  changePageSize: changeExecutionPageSize,
  refresh: refreshExecutions
} = useSceneExecutionsQuery()

const { retryExecution } = useSceneExecutionRetryMutation()

const drawerVisible = ref(false)
const detailExecutionId = ref(null)
const retryingId = ref(null)

function openDetail(row) {
  detailExecutionId.value = row.id
  drawerVisible.value = true
}

async function handleRetry(row) {
  try {
    await ElMessageBox.confirm('确认手动重试该执行记录？将立即重新执行未完成步骤。', '手动重试', {
      confirmButtonText: '重试',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  retryingId.value = row.id
  try {
    await retryExecution(row.id)
    ElMessage.success('已提交重试')
  } catch {
    // 非 FAILED 记录会返回 409
  } finally {
    retryingId.value = null
  }
}

// ---- 执行记录筛选项（场景 / 设备下拉） ----
const sceneOptions = ref([])
const deviceOptions = ref([])

async function loadOptions() {
  try {
    const page = unwrapResult(await sceneApi.listScenes({ pageNum: 1, pageSize: 100 }), { records: [] })
    sceneOptions.value = page.records || []
  } catch {
    sceneOptions.value = []
  }
  try {
    const page = unwrapResult(await deviceApi.getList({ page: 1, size: 200 }), { records: [] })
    deviceOptions.value = page.records || []
  } catch {
    deviceOptions.value = []
  }
}

onMounted(loadOptions)
</script>

<style scoped>
.scene-center__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--grid-gutter);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  flex-wrap: wrap;
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-item {
  width: 150px;
}

.filter-item--wide {
  width: 200px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.pending-hint {
  margin-bottom: var(--grid-gutter);
}

.scene-table,
.execution-table {
  padding: var(--spacing-sm);
}

.scene-mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.scene-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}
</style>
