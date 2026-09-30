<template>
  <div class="device-list-page page-container">
    <div class="page-header">
      <h2>设备管理</h2>
      <button class="btn-primary" @click="openCreateDialog">
        <svg-icon name="add" :size="16" />
        创建设备
      </button>
    </div>

    <div class="filter-bar card">
      <div class="filter-item">
        <label class="filter-label">设备名称</label>
        <input
          v-model="filters.deviceName"
          placeholder="输入设备名称"
          class="form-input"
          @keyup.enter="handleSearch"
        />
      </div>
      <div class="filter-item">
        <label class="filter-label">设备类型</label>
        <select v-model="filters.deviceType" class="form-input">
          <option value="">全部类型</option>
          <option value="sensor">传感器</option>
          <option value="gateway">网关</option>
          <option value="actuator">执行器</option>
        </select>
      </div>
      <div class="filter-item">
        <label class="filter-label">状态</label>
        <select v-model="filters.status" class="form-input">
          <option value="">全部状态</option>
          <option value="ONLINE">在线</option>
          <option value="OFFLINE">离线</option>
          <option value="INACTIVE">未激活</option>
        </select>
      </div>
      <div class="filter-actions">
        <button class="btn-primary" @click="handleSearch">查询</button>
        <button class="btn-secondary" @click="handleReset">重置</button>
      </div>
    </div>

    <div v-if="loading" class="loading-overlay">
      加载中...
    </div>

    <div v-else-if="devices.length === 0" class="empty-state">
      <svg-icon name="device" :size="48" color="#E0E0E0" />
      <p class="empty-state-text">暂无设备，点击"创建设备"添加</p>
    </div>

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

    <Pagination
      :page="currentPage"
      :total-pages="totalPages"
      @go="goToPage"
    />

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
import DeviceCard from '@/components/device/DeviceCard.vue'
import CreateDeviceDialog from '@/components/device/CreateDeviceDialog.vue'
import EditDeviceDialog from '@/components/device/EditDeviceDialog.vue'
import CredentialDialog from '@/components/device/CredentialDialog.vue'
import Pagination from '@/components/common/Pagination.vue'
import { useDeviceList } from '@/composables/useDeviceList'
import { useDeviceCrud } from '@/composables/useDeviceCrud'

const router = useRouter()

const {
  filters,
  currentPage,
  totalPages,
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
  router.push(`/devices/${device.id}`)
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: flex-end;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  flex-wrap: wrap;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-width: 160px;
}

.filter-label {
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  font-weight: 500;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}
</style>
