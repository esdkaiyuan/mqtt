<template>
  <div class="device-list-page page">
    <PageHeader title="设备管理" desc="管理接入平台的设备与其连接凭据">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          创建设备
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar">
      <el-input
        v-model="filters.deviceName"
        placeholder="输入设备名称"
        clearable
        class="filter-item"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filters.deviceType" placeholder="全部类型" clearable class="filter-item">
        <el-option label="传感器" value="sensor" />
        <el-option label="网关" value="gateway" />
        <el-option label="执行器" value="actuator" />
      </el-select>
      <el-select v-model="filters.status" placeholder="全部状态" clearable class="filter-item">
        <el-option label="在线" value="ONLINE" />
        <el-option label="离线" value="OFFLINE" />
        <el-option label="未激活" value="INACTIVE" />
      </el-select>
      <el-tree-select
        v-model="filters.groupId"
        :data="groupTree"
        :props="TREE_PROPS"
        node-key="id"
        clearable
        check-strictly
        :render-after-expand="false"
        default-expand-all
        placeholder="全部分组"
        class="filter-item"
      />
      <el-select v-model="filters.tagId" placeholder="全部标签" clearable class="filter-item">
        <el-option v-for="tag in tags" :key="tag.id" :label="tag.name" :value="tag.id" />
      </el-select>
      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div v-if="selectedCount > 0" class="batch-bar">
      <span class="batch-bar__count">已选 {{ selectedCount }} 台</span>
      <div class="batch-bar__actions">
        <el-button size="small" :disabled="running" @click="batchCommandVisible = true">下发命令</el-button>
        <el-button size="small" :disabled="running" @click="handleBatchEnabled(true)">启用</el-button>
        <el-button size="small" type="danger" plain :disabled="running" @click="handleBatchEnabled(false)">
          禁用
        </el-button>
        <el-button size="small" @click="openAssign('group', 'ADD')">加入分组</el-button>
        <el-button size="small" @click="openAssign('group', 'REMOVE')">移出分组</el-button>
        <el-button size="small" @click="openAssign('tag', 'ADD')">打标签</el-button>
        <el-button size="small" @click="openAssign('tag', 'REMOVE')">去标签</el-button>
        <el-button size="small" link @click="clearSelection">清空选择</el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay">加载中...</div>

    <EmptyState v-else-if="devices.length === 0" description="暂无设备，点击「创建设备」添加" />

    <template v-else>
      <div class="list-toolbar">
        <el-checkbox
          :model-value="allSelected"
          :indeterminate="someSelected"
          @change="(checked) => toggleSelectAll(devices, checked)"
        >
          全选本页
        </el-checkbox>
      </div>

      <div class="device-grid">
        <DeviceCard
          v-for="device in devices"
          :key="device.id"
          :device="device"
          selectable
          :selected="isSelected(device.id)"
          @toggle-select="toggleSelect(device.id)"
          @view="viewDevice"
          @edit="openEditDialog"
          @delete="deleteDevice"
        />
      </div>
    </template>

    <div v-if="total > 0" class="device-pagination">
      <el-pagination
        layout="prev, pager, next"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        background
        @current-change="goToPage"
      />
    </div>

    <CreateDeviceDialog
      v-model:visible="showCreateDialog"
      :products="products"
      :products-loading="productsLoading"
      :loading="createLoading"
      @submit="handleCreate"
    />

    <EditDeviceDialog
      v-model:visible="showEditDialog"
      :device="editingDevice"
      :loading="editLoading"
      @submit="handleEdit"
    />

    <CredentialDialog
      v-model:visible="showCredentialDialog"
      :credential="createdCredential"
    />

    <BatchCommandDialog
      v-model:visible="batchCommandVisible"
      :count="selectedCount"
      :loading="running"
      @submit="handleBatchCommand"
    />

    <BatchAssignDialog
      v-model:visible="assignVisible"
      :mode="assignMode"
      :action="assignAction"
      :count="selectedCount"
      :options="assignOptions"
      :loading="running"
      @submit="handleAssign"
    />

    <BatchResultDrawer v-model:visible="resultVisible" :result="result" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import DeviceCard from '@/components/device/DeviceCard.vue'
import CreateDeviceDialog from '@/components/device/CreateDeviceDialog.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import CredentialDialog from '@/components/device/CredentialDialog.vue'
import BatchCommandDialog from '@/components/device/BatchCommandDialog.vue'
import BatchAssignDialog from '@/components/device/BatchAssignDialog.vue'
import BatchResultDrawer from '@/components/device/BatchResultDrawer.vue'
import { useDeviceList } from '@/composables/useDeviceList'
import { useDeviceCrud } from '@/composables/useDeviceCrud'
import { useDeviceGroupTreeQuery, useDeviceGroupListQuery, useTagListQuery } from '@/composables/useDeviceGroups'
import { useDeviceBatch } from '@/composables/useDeviceBatch'

const TREE_PROPS = { label: 'name', children: 'children' }

const router = useRouter()

const {
  filters,
  currentPage,
  pageSize,
  total,
  devices,
  loading,
  loadDevices,
  handleSearch,
  handleReset,
  goToPage
} = useDeviceList()

const {
  showCreateDialog,
  createLoading,
  products,
  productsLoading,
  openCreateDialog,
  createDevice,
  showCredentialDialog,
  createdCredential,
  showEditDialog,
  editLoading,
  editingDevice,
  openEditDialog,
  updateDevice,
  deleteDevice
} = useDeviceCrud()

const { tree: groupTree } = useDeviceGroupTreeQuery()
const { groups } = useDeviceGroupListQuery()
const { tags } = useTagListQuery()

const {
  selectedIds,
  selectedCount,
  isSelected,
  toggleSelect,
  clearSelection,
  toggleSelectAll,
  resultVisible,
  result,
  running,
  sendBatchCommands,
  batchEnable,
  batchDisable,
  batchAssignGroup,
  batchAssignTag
} = useDeviceBatch()

const allSelected = computed(
  () => devices.value.length > 0 && devices.value.every((device) => isSelected(device.id))
)
const someSelected = computed(
  () => devices.value.some((device) => isSelected(device.id)) && !allSelected.value
)

async function handleCreate(form) {
  if (await createDevice(form)) {
    await loadDevices(currentPage.value)
  }
}

async function handleEdit(form) {
  await updateDevice(form)
}

function viewDevice(device) {
  router.push(`/workbench/devices/${device.id}`)
}

/* ---------- 批量操作 ---------- */

const batchCommandVisible = ref(false)
const assignVisible = ref(false)
const assignMode = ref('group')
const assignAction = ref('ADD')

const assignOptions = computed(() => (assignMode.value === 'group' ? groups.value : tags.value))

function openAssign(mode, action) {
  assignMode.value = mode
  assignAction.value = action
  assignVisible.value = true
}

async function handleBatchCommand(payload) {
  try {
    await sendBatchCommands({ ...payload, deviceIds: selectedIds.value })
    batchCommandVisible.value = false
    ElMessage.success('批量下发已提交')
    await loadDevices(currentPage.value)
  } catch {
    // 失败原因由 axios 拦截器统一提示
  }
}

async function handleBatchEnabled(enabled) {
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
    await (enabled ? batchEnable : batchDisable)({ deviceIds: selectedIds.value })
    ElMessage.success(enabled ? '已批量启用' : '已批量禁用')
    await loadDevices(currentPage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}

async function handleAssign(targetId) {
  const payload = { deviceIds: selectedIds.value, action: assignAction.value }
  if (assignMode.value === 'group') {
    payload.groupId = targetId
  } else {
    payload.tagId = targetId
  }
  try {
    await (assignMode.value === 'group' ? batchAssignGroup : batchAssignTag)(payload)
    assignVisible.value = false
    ElMessage.success('批量操作已提交')
    await loadDevices(currentPage.value)
  } catch {
    // 失败原因由拦截器提示
  }
}
</script>

<style scoped>
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
  width: 180px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary);
  border-radius: var(--border-radius);
  flex-wrap: wrap;
}

.batch-bar__count {
  font-weight: 600;
  color: var(--color-primary);
}

.batch-bar__actions {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  margin-left: auto;
}

.list-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--grid-gutter);
  margin-bottom: var(--grid-gutter);
}

.device-pagination {
  display: flex;
  justify-content: flex-end;
}
</style>