<template>
  <div class="device-groups-page page">
    <PageHeader title="分组管理" desc="用分组树组织设备、用标签标注设备，并支持按分组批量操作">
      <template #actions>
        <el-button type="primary" @click="openCreateRoot">
          <svg-icon name="add" :size="16" />
          新建根分组
        </el-button>
      </template>
    </PageHeader>

    <div class="groups-layout">
      <!-- 左：分组树 -->
      <div class="groups-tree card">
        <div class="groups-tree__header">
          <span class="groups-tree__title">分组树</span>
          <el-button link type="primary" :loading="treeLoading" @click="refreshTree">
            <svg-icon name="refresh" :size="14" />
            刷新
          </el-button>
        </div>

        <el-tree
          v-if="tree.length > 0"
          :data="tree"
          node-key="id"
          :props="TREE_PROPS"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          @node-click="handleNodeClick"
        >
          <template #default="{ data }">
            <div class="group-node">
              <span class="group-node__label">
                <svg-icon name="building" :size="14" />
                <span class="group-node__name">{{ data.name }}</span>
                <span class="group-node__count">{{ data.deviceCount ?? 0 }}</span>
              </span>
              <span class="group-node__actions">
                <el-button link size="small" @click.stop="openCreateChild(data)">新建子分组</el-button>
                <el-button link size="small" @click.stop="openEdit(data)">编辑</el-button>
                <el-button link size="small" @click.stop="move(data, -1)">上移</el-button>
                <el-button link size="small" @click.stop="move(data, 1)">下移</el-button>
                <el-button link size="small" type="danger" @click.stop="handleDeleteGroup(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>

        <EmptyState
          v-else-if="!treeLoading"
          description="暂无分组，点击「新建根分组」开始"
          :image-size="80"
        />
      </div>

      <!-- 右：分组详情 + 标签管理 -->
      <div class="groups-detail">
        <div class="group-devices card">
          <template v-if="selectedGroup">
            <div class="group-devices__header">
              <div class="group-devices__title">
                <span>{{ selectedGroup.name }}</span>
                <span class="group-devices__desc">{{ selectedGroup.description || '暂无描述' }}</span>
              </div>
              <div class="group-devices__batch">
                <el-button
                  size="small"
                  :disabled="groupDeviceTotal === 0 || running"
                  @click="batchCommandVisible = true"
                >
                  批量下发命令
                </el-button>
                <el-button
                  size="small"
                  :disabled="groupDeviceTotal === 0 || running"
                  :loading="running"
                  @click="handleGroupEnabled(true)"
                >
                  批量启用
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="groupDeviceTotal === 0 || running"
                  @click="handleGroupEnabled(false)"
                >
                  批量禁用
                </el-button>
              </div>
            </div>

            <el-table v-loading="groupDeviceLoading" :data="groupDevices" style="width: 100%">
              <el-table-column label="设备名称" min-width="140">
                <template #default="{ row }">{{ row.deviceName }}</template>
              </el-table-column>
              <el-table-column prop="deviceKey" label="设备标识" min-width="140" />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="STATUS_TAG_TYPES[row.status] || 'info'">
                    {{ STATUS_LABELS[row.status] || row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="标签" min-width="160">
                <template #default="{ row }">
                  <el-tag
                    v-for="tag in row.tags"
                    :key="`t-${tag.id}`"
                    size="small"
                    effect="plain"
                    class="device-tag-chip"
                    :style="tag.color ? { color: tag.color, borderColor: tag.color } : {}"
                  >
                    {{ tag.name }}
                  </el-tag>
                  <span v-if="!row.tags || row.tags.length === 0" class="text-muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90" fixed="right">
                <template #default="{ row }">
                  <el-button link type="danger" @click="handleRemoveDevice(row)">移出</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <EmptyState description="该分组暂无设备" :image-size="80" />
              </template>
            </el-table>

            <div v-if="groupDeviceTotal > 0" class="group-devices__pagination">
              <el-pagination
                layout="prev, pager, next"
                :current-page="groupDevicePage"
                :page-size="groupDeviceSize"
                :total="groupDeviceTotal"
                background
                @current-change="loadGroupDevices"
              />
            </div>
          </template>

          <EmptyState v-else description="从左侧选择分组查看其设备" :image-size="80" />
        </div>

        <div class="tag-manage card">
          <div class="tag-manage__header">
            <span class="tag-manage__title">标签管理</span>
            <el-button link type="primary" @click="openCreateTag">
              <svg-icon name="add" :size="14" />
              新建标签
            </el-button>
          </div>

          <div v-if="tags.length > 0" class="tag-list">
            <div v-for="tag in tags" :key="tag.id" class="tag-item">
              <span class="tag-item__dot" :style="{ background: tag.color || 'var(--color-text-tertiary)' }" />
              <span class="tag-item__name">{{ tag.name }}</span>
              <span class="tag-item__actions">
                <el-button link size="small" @click="openEditTag(tag)">编辑</el-button>
                <el-button link size="small" type="danger" @click="handleDeleteTag(tag)">删除</el-button>
              </span>
            </div>
          </div>
          <EmptyState v-else description="暂无标签，点击「新建标签」开始" :image-size="80" />
        </div>
      </div>
    </div>

    <!-- 分组新建 / 编辑 -->
    <el-dialog
      v-model="groupDialogVisible"
      :title="groupEditingId ? '编辑分组' : '新建分组'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="分组名称" required>
          <el-input v-model="groupForm.name" maxlength="64" placeholder="如：华东厂区" />
        </el-form-item>
        <el-form-item label="父分组">
          <el-select v-model="groupForm.parentId" clearable placeholder="不选则为根分组" style="width: 100%">
            <el-option
              v-for="option in parentOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="groupForm.description"
            type="textarea"
            :rows="3"
            maxlength="255"
            placeholder="选填"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="groupSaving" @click="handleSubmitGroup">
          {{ groupEditingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 标签新建 / 编辑 -->
    <el-dialog
      v-model="tagDialogVisible"
      :title="tagEditingId ? '编辑标签' : '新建标签'"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <el-form-item label="标签名称" required>
          <el-input v-model="tagForm.name" maxlength="64" placeholder="如：重点设备" />
        </el-form-item>
        <el-form-item label="展示色">
          <el-color-picker v-model="tagForm.color" />
          <span class="color-hint">{{ tagForm.color || '未设置' }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tagDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="tagSaving" @click="handleSubmitTag">
          {{ tagEditingId ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <BatchCommandDialog
      v-model:visible="batchCommandVisible"
      :count="groupDeviceTotal"
      :loading="running"
      @submit="handleBatchCommand"
    />
    <BatchResultDrawer v-model:visible="resultVisible" :result="result" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import BatchCommandDialog from '@/components/device/BatchCommandDialog.vue'
import BatchResultDrawer from '@/components/device/BatchResultDrawer.vue'
import { deviceGroupApi } from '@/api/deviceGroup'
import { unwrapResult } from '@/utils/result'
import {
  useDeviceGroupTreeQuery,
  useDeviceGroupMutations,
  useTagListQuery,
  useTagMutations
} from '@/composables/useDeviceGroups'
import { useDeviceBatch } from '@/composables/useDeviceBatch'

const TREE_PROPS = { label: 'name', children: 'children' }
const STATUS_LABELS = { ONLINE: '在线', OFFLINE: '离线', INACTIVE: '未激活' }
const STATUS_TAG_TYPES = { ONLINE: 'success', OFFLINE: 'info', INACTIVE: 'warning' }

const { tree, loading: treeLoading, refresh: refreshTree } = useDeviceGroupTreeQuery()
const { saving: groupSaving, createGroup, updateGroup, deleteGroup } = useDeviceGroupMutations()
const { tags, refresh: refreshTags } = useTagListQuery()
const { saving: tagSaving, createTag, updateTag, deleteTag } = useTagMutations()

const { running, result, resultVisible, sendBatchCommands, batchEnable, batchDisable } =
  useDeviceBatch()

/* ---------- 分组树选中与设备列表 ---------- */

const selectedGroup = ref(null)
const groupDevices = ref([])
const groupDeviceTotal = ref(0)
const groupDevicePage = ref(1)
const groupDeviceSize = ref(10)
const groupDeviceLoading = ref(false)

function findNode(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node
    const found = findNode(node.children ?? [], id)
    if (found) return found
  }
  return null
}

async function loadGroupDevices(page = 1) {
  if (!selectedGroup.value) return
  groupDevicePage.value = page
  groupDeviceLoading.value = true
  try {
    const res = await deviceGroupApi.pageDevices(selectedGroup.value.id, {
      page,
      size: groupDeviceSize.value
    })
    const data = unwrapResult(res, { records: [], total: 0 })
    groupDevices.value = data.records ?? []
    groupDeviceTotal.value = data.total ?? 0
  } catch {
    groupDevices.value = []
    groupDeviceTotal.value = 0
  } finally {
    groupDeviceLoading.value = false
  }
}

function handleNodeClick(data) {
  selectedGroup.value = data
  loadGroupDevices(1)
}

// 树刷新后按 id 重新绑定选中节点，保证 deviceCount / 名称同步
watch(tree, (nodes) => {
  if (!selectedGroup.value) return
  selectedGroup.value = findNode(nodes, selectedGroup.value.id)
  if (!selectedGroup.value) {
    groupDevices.value = []
    groupDeviceTotal.value = 0
  }
})

/* ---------- 分组新建 / 编辑 / 移动 / 删除 ---------- */

const groupDialogVisible = ref(false)
const groupEditingId = ref(null)
const groupForm = reactive({ name: '', parentId: null, description: '' })

const parentOptions = computed(() =>
  flatten(tree.value).filter((group) => group.id !== groupEditingId.value)
)

function flatten(nodes) {
  return nodes.flatMap((node) => [node, ...flatten(node.children ?? [])])
}

function openCreateRoot() {
  groupEditingId.value = null
  Object.assign(groupForm, { name: '', parentId: null, description: '' })
  groupDialogVisible.value = true
}

function openCreateChild(node) {
  groupEditingId.value = null
  Object.assign(groupForm, { name: '', parentId: node.id, description: '' })
  groupDialogVisible.value = true
}

function openEdit(node) {
  groupEditingId.value = node.id
  Object.assign(groupForm, {
    name: node.name,
    parentId: node.parentId ?? null,
    description: node.description ?? ''
  })
  groupDialogVisible.value = true
}

async function handleSubmitGroup() {
  if (!groupForm.name.trim()) {
    ElMessage.warning('请填写分组名称')
    return
  }
  const payload = {
    name: groupForm.name.trim(),
    parentId: groupForm.parentId ?? null,
    description: groupForm.description.trim() || null
  }
  try {
    if (groupEditingId.value) {
      await updateGroup(groupEditingId.value, payload)
      ElMessage.success('分组已更新')
    } else {
      await createGroup(payload)
      ElMessage.success('分组已创建')
    }
    groupDialogVisible.value = false
  } catch {
    // 具体原因由 axios 拦截器统一提示（如 6213 同级重名）
  }
}

function siblingsOf(node) {
  if (node.parentId === null || node.parentId === undefined) return tree.value
  const parent = findNode(tree.value, node.parentId)
  return parent?.children ?? []
}

/** 上移 / 下移：重排同级后按新顺序统一写回 sort_order，避免默认全为 0 时交换无效。 */
async function move(node, delta) {
  const siblings = siblingsOf(node)
  const index = siblings.findIndex((item) => item.id === node.id)
  const targetIndex = index + delta
  if (index < 0 || targetIndex < 0 || targetIndex >= siblings.length) return

  const reordered = [...siblings]
  const [moved] = reordered.splice(index, 1)
  reordered.splice(targetIndex, 0, moved)

  try {
    for (let i = 0; i < reordered.length; i++) {
      const item = reordered[i]
      if ((item.sortOrder ?? 0) !== i) {
        await updateGroup(item.id, {
          name: item.name,
          parentId: item.parentId ?? null,
          description: item.description ?? null,
          sortOrder: i
        })
      }
    }
    ElMessage.success('排序已更新')
  } catch {
    // 失败原因由拦截器提示
  }
}

async function handleDeleteGroup(node) {
  try {
    await ElMessageBox.confirm(
      `确定要删除分组「${node.name}」吗？仅空分组可删除。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteGroup(node.id)
    ElMessage.success('分组已删除')
    if (selectedGroup.value?.id === node.id) {
      selectedGroup.value = null
      groupDevices.value = []
      groupDeviceTotal.value = 0
    }
  } catch {
    // 非空分组后端返回 6216，拦截器已提示「请先移动子分组或移出设备」
  }
}

/* ---------- 分组内批量操作 ---------- */

const batchCommandVisible = ref(false)

function groupTarget() {
  return { groupIds: [selectedGroup.value.id] }
}

async function handleBatchCommand(payload) {
  if (!selectedGroup.value) return
  try {
    await sendBatchCommands({ ...payload, ...groupTarget() })
    batchCommandVisible.value = false
    ElMessage.success('批量下发已提交')
    loadGroupDevices(groupDevicePage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}

async function handleGroupEnabled(enabled) {
  if (!selectedGroup.value) return
  if (!enabled) {
    try {
      await ElMessageBox.confirm(
        '批量禁用将立即踢掉在线连接，确定继续吗？',
        '确认禁用',
        { confirmButtonText: '禁用', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return
    }
  }
  try {
    await (enabled ? batchEnable : batchDisable)(groupTarget())
    ElMessage.success(enabled ? '已批量启用' : '已批量禁用')
    loadGroupDevices(groupDevicePage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}

async function handleRemoveDevice(device) {
  try {
    await ElMessageBox.confirm(
      `确定将「${device.deviceName}」移出当前分组吗？`,
      '确认移出',
      { confirmButtonText: '移出', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deviceGroupApi.removeDevices(selectedGroup.value.id, [device.id])
    ElMessage.success('已移出分组')
    refreshTree()
    loadGroupDevices(groupDevicePage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}

/* ---------- 标签管理 ---------- */

const tagDialogVisible = ref(false)
const tagEditingId = ref(null)
const tagForm = reactive({ name: '', color: '' })

function openCreateTag() {
  tagEditingId.value = null
  Object.assign(tagForm, { name: '', color: '' })
  tagDialogVisible.value = true
}

function openEditTag(tag) {
  tagEditingId.value = tag.id
  Object.assign(tagForm, { name: tag.name, color: tag.color ?? '' })
  tagDialogVisible.value = true
}

async function handleSubmitTag() {
  if (!tagForm.name.trim()) {
    ElMessage.warning('请填写标签名称')
    return
  }
  const payload = {
    name: tagForm.name.trim(),
    color: tagForm.color || null
  }
  try {
    if (tagEditingId.value) {
      await updateTag(tagEditingId.value, payload)
      ElMessage.success('标签已更新')
    } else {
      await createTag(payload)
      ElMessage.success('标签已创建')
    }
    tagDialogVisible.value = false
    refreshTags()
    loadGroupDevices(groupDevicePage.value)
  } catch {
    // 具体原因由拦截器提示（如 6215 重名 / 颜色非法）
  }
}

async function handleDeleteTag(tag) {
  try {
    await ElMessageBox.confirm(
      `确定删除标签「${tag.name}」吗？将自动解除其全部设备关联。`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await deleteTag(tag.id)
    ElMessage.success('标签已删除')
    refreshTags()
    loadGroupDevices(groupDevicePage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}

onMounted(() => {
  refreshTree()
  refreshTags()
})
</script>

<style scoped>
.groups-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--grid-gutter);
  align-items: start;
}

.groups-tree {
  padding: var(--spacing-md);
}

.groups-tree__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
}

.groups-tree__title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.group-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--spacing-sm);
  padding-right: var(--spacing-sm);
}

.group-node__label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.group-node__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-node__count {
  padding: 0 6px;
  border-radius: 8px;
  background: var(--color-bg);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-xs);
}

.group-node__actions {
  display: none;
  flex-shrink: 0;
}

.group-node:hover .group-node__actions {
  display: inline-flex;
}

.groups-detail {
  display: flex;
  flex-direction: column;
  gap: var(--grid-gutter);
  min-width: 0;
}

.group-devices {
  padding: var(--spacing-md);
}

.group-devices__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.group-devices__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.group-devices__desc {
  font-weight: 400;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.group-devices__batch {
  display: flex;
  gap: var(--spacing-sm);
}

.group-devices__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-md);
}

.device-tag-chip {
  margin-right: 6px;
}

.text-muted {
  color: var(--color-text-tertiary);
}

.tag-manage {
  padding: var(--spacing-md);
}

.tag-manage__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-sm);
}

.tag-manage__title {
  font-weight: 600;
  color: var(--color-text-primary);
}

.tag-list {
  display: flex;
  flex-direction: column;
}

.tag-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color-light);
}

.tag-item:last-child {
  border-bottom: none;
}

.tag-item__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.tag-item__name {
  flex: 1;
  color: var(--color-text-primary);
}

.dialog-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.color-hint {
  margin-left: var(--spacing-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}
</style>