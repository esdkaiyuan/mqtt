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
      <div class="filter-actions">
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay">加载中...</div>

    <EmptyState v-else-if="devices.length === 0" description="暂无设备，点击「创建设备」添加" />

    <div v-else class="device-grid">
      <DeviceCard
        v-for="device in devices"
        :key="device.id"
        :device="device"
        @view="viewDevice"
        @edit="openEditDialog"
        @delete="deleteDevice"
      />
    </div>

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
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import DeviceCard from '@/components/device/DeviceCard.vue'
import CreateDeviceDialog from '@/components/device/CreateDeviceDialog.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import CredentialDialog from '@/components/device/CredentialDialog.vue'
import { useDeviceList } from '@/composables/useDeviceList'
import { useDeviceCrud } from '@/composables/useDeviceCrud'

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
  width: 200px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
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