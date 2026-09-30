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

    <div v-if="deviceStore.loading" class="loading-overlay">
      加载中...
    </div>

    <div v-else-if="devices.length === 0" class="empty-state">
      <svg-icon name="device" :size="48" color="#E0E0E0" />
      <p class="empty-state-text">暂无设备，点击"创建设备"添加</p>
    </div>

    <div v-else class="device-grid">
      <div
        v-for="device in devices"
        :key="device.id"
        class="device-card card"
      >
        <div class="device-card-header">
          <div class="device-icon">
            <svg-icon name="device" :size="24" />
          </div>
          <div class="device-info">
            <h3 class="device-name">{{ device.deviceName }}</h3>
            <span :class="['device-type-tag', device.deviceType]">{{ formatDeviceType(device.deviceType) }}</span>
          </div>
          <span :class="['status-badge', device.status?.toLowerCase()]">
            {{ formatStatus(device.status) }}
          </span>
        </div>

        <div class="device-card-body">
          <div class="device-detail-row">
            <span class="detail-label">设备标识</span>
            <span class="detail-value">{{ device.deviceKey }}</span>
          </div>
          <div class="device-detail-row">
            <span class="detail-label">Topic</span>
            <span class="detail-value topic-text">{{ device.topic }}</span>
          </div>
          <div v-if="device.description" class="device-detail-row">
            <span class="detail-label">描述</span>
            <span class="detail-value">{{ device.description }}</span>
          </div>
        </div>

        <div class="device-card-actions">
          <button class="btn-link" @click="viewDevice(device)">
            <svg-icon name="device" :size="14" />
            查看
          </button>
          <button class="btn-link" @click="editDevice(device)">
            <svg-icon name="edit" :size="14" />
            编辑
          </button>
          <button class="btn-link-danger" @click="deleteDevice(device)">
            <svg-icon name="delete" :size="14" />
            删除
          </button>
        </div>
      </div>
    </div>

    <div v-if="totalPages > 1" class="pagination">
      <button
        class="btn-page"
        :disabled="currentPage === 1"
        @click="goToPage(currentPage - 1)"
      >
        上一页
      </button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button
        class="btn-page"
        :disabled="currentPage === totalPages"
        @click="goToPage(currentPage + 1)"
      >
        下一页
      </button>
    </div>

    <el-dialog
      v-model="showCreateDialog"
      title="创建设备"
      width="500px"
      :close-on-click-modal="false"
    >
      <form @submit.prevent="handleCreate" class="dialog-form">
        <div class="form-group">
          <label class="form-label">所属产品 <span class="required">*</span></label>
          <select
            v-model="createForm.productId"
            class="form-input"
            :disabled="productsLoading"
          >
            <option value="">{{ productsLoading ? '加载中...' : '请选择产品' }}</option>
            <option
              v-for="product in products"
              :key="product.id"
              :value="product.id"
            >
              {{ product.productName }}（{{ product.productKey }}）
            </option>
          </select>
          <p v-if="!productsLoading && products.length === 0" class="form-hint">
            暂无可用产品，请先通过 POST /api/products 创建并启用产品
          </p>
        </div>
        <div class="form-group">
          <label class="form-label">设备名称 <span class="required">*</span></label>
          <input
            v-model="createForm.deviceName"
            placeholder="请输入设备名称"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <label class="form-label">设备标识 <span class="required">*</span></label>
          <input
            v-model="createForm.deviceKey"
            placeholder="全局唯一标识，如sensor-001"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <label class="form-label">设备类型 <span class="required">*</span></label>
          <select v-model="createForm.deviceType" class="form-input">
            <option value="">请选择类型</option>
            <option value="sensor">传感器</option>
            <option value="gateway">网关</option>
            <option value="actuator">执行器</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">MQTT Topic <span class="required">*</span></label>
          <input
            v-model="createForm.topic"
            placeholder="如 device/sensor-001/data"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <label class="form-label">设备描述</label>
          <textarea
            v-model="createForm.description"
            placeholder="设备描述信息（选填）"
            class="form-input form-textarea"
            rows="3"
          ></textarea>
        </div>
      </form>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn-secondary" @click="showCreateDialog = false">取消</button>
          <button class="btn-primary" @click="handleCreate" :disabled="createLoading">
            {{ createLoading ? '创建中...' : '创建设备' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showEditDialog"
      title="编辑设备"
      width="500px"
      :close-on-click-modal="false"
    >
      <form @submit.prevent="handleEdit" class="dialog-form">
        <div class="form-group">
          <label class="form-label">设备名称</label>
          <input v-model="editForm.deviceName" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">设备类型</label>
          <select v-model="editForm.deviceType" class="form-input">
            <option value="sensor">传感器</option>
            <option value="gateway">网关</option>
            <option value="actuator">执行器</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">MQTT Topic</label>
          <input v-model="editForm.topic" class="form-input" />
        </div>
        <div class="form-group">
          <label class="form-label">设备描述</label>
          <textarea v-model="editForm.description" class="form-input form-textarea" rows="3"></textarea>
        </div>
      </form>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn-secondary" @click="showEditDialog = false">取消</button>
          <button class="btn-primary" @click="handleEdit" :disabled="editLoading">
            {{ editLoading ? '保存中...' : '保存' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showCredentialDialog"
      title="设备凭据（仅显示一次）"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="credential-tip">
        以下凭据仅在本次创建时返回，平台不提供二次查询，请立即复制并妥善保存。
      </div>
      <div class="credential-row">
        <span class="credential-label">MQTT 用户名</span>
        <div class="credential-value">
          <code>{{ createdCredential.username }}</code>
          <button type="button" class="btn-link" @click="copyText(createdCredential.username)">
            复制
          </button>
        </div>
      </div>
      <div class="credential-row">
        <span class="credential-label">设备密钥</span>
        <div class="credential-value">
          <code>{{ createdCredential.deviceSecret }}</code>
          <button type="button" class="btn-link" @click="copyText(createdCredential.deviceSecret)">
            复制
          </button>
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <button class="btn-primary" @click="showCredentialDialog = false">我已保存</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useDeviceStore } from '@/stores/device'
import { productApi } from '@/api/product'
import SvgIcon from '@/components/Icon.vue'

const router = useRouter()
const deviceStore = useDeviceStore()

const filters = reactive({
  deviceName: '',
  deviceType: '',
  status: ''
})

const currentPage = ref(1)
const pageSize = ref(10)
const totalPages = computed(() => Math.ceil(deviceStore.totalDevices / pageSize.value))
const devices = computed(() => deviceStore.devices)

const showCreateDialog = ref(false)
const createLoading = ref(false)
const products = ref([])
const productsLoading = ref(false)
const createForm = reactive({
  productId: '',
  deviceName: '',
  deviceKey: '',
  deviceType: '',
  topic: '',
  description: ''
})

const showCredentialDialog = ref(false)
const createdCredential = ref({ username: '', deviceSecret: '' })

const showEditDialog = ref(false)
const editLoading = ref(false)
const editForm = reactive({
  id: null,
  deviceName: '',
  deviceType: '',
  topic: '',
  description: ''
})

async function loadDevices(page = 1) {
  currentPage.value = page
  await deviceStore.fetchDevices({
    pageNum: page,
    pageSize: pageSize.value,
    deviceName: filters.deviceName || undefined,
    deviceType: filters.deviceType || undefined,
    status: filters.status || undefined
  })
}

function handleSearch() {
  loadDevices(1)
}

function handleReset() {
  filters.deviceName = ''
  filters.deviceType = ''
  filters.status = ''
  loadDevices(1)
}

function goToPage(page) {
  if (page >= 1 && page <= totalPages.value) {
    loadDevices(page)
  }
}

async function loadProducts() {
  productsLoading.value = true
  try {
    const result = await productApi.getList()
    const list = result.data || result || []
    products.value = list.filter(product => product.status === 'ENABLED')
  } catch (error) {
    products.value = []
  } finally {
    productsLoading.value = false
  }
}

function openCreateDialog() {
  Object.keys(createForm).forEach(key => {
    createForm[key] = ''
  })
  showCreateDialog.value = true
  loadProducts()
}

async function handleCreate() {
  if (!createForm.productId || !createForm.deviceName || !createForm.deviceKey || !createForm.deviceType || !createForm.topic) {
    ElMessage.warning('请填写所有必填项')
    return
  }

  createLoading.value = true
  try {
    const created = await deviceStore.createDevice({ ...createForm })
    showCreateDialog.value = false
    await loadDevices(currentPage.value)

    createdCredential.value = {
      username: created?.username || '',
      deviceSecret: created?.deviceSecret || ''
    }
    showCredentialDialog.value = true
  } catch (error) {
    // 创建失败的具体原因已由 axios 拦截器统一提示，此处只需结束 loading
  } finally {
    createLoading.value = false
  }
}

async function copyText(text) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (error) {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}

function viewDevice(device) {
  router.push(`/devices/${device.id}`)
}

function editDevice(device) {
  editForm.id = device.id
  editForm.deviceName = device.deviceName
  editForm.deviceType = device.deviceType
  editForm.topic = device.topic
  editForm.description = device.description || ''
  showEditDialog.value = true
}

async function handleEdit() {
  editLoading.value = true
  try {
    await deviceStore.updateDevice(editForm.id, editForm)
    showEditDialog.value = false
  } catch (error) {
    // 更新失败的具体原因已由 axios 拦截器统一提示，此处只需结束 loading
  } finally {
    editLoading.value = false
  }
}

async function deleteDevice(device) {
  try {
    await ElMessageBox.confirm(
      `确定要删除设备"${device.deviceName}"吗？此操作不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await deviceStore.deleteDevice(device.id)
  } catch (error) {
    // 用户取消确认对话框时 ElMessageBox 会 reject；接口失败亦由拦截器提示，这里静默即可
  }
}

function formatStatus(status) {
  const statusMap = {
    'ONLINE': '在线',
    'OFFLINE': '离线',
    'INACTIVE': '未激活'
  }
  return statusMap[status] || status
}

function formatDeviceType(type) {
  const map = { 'sensor': '传感器', 'gateway': '网关', 'actuator': '执行器' }
  return map[type] || type
}

onMounted(() => {
  loadDevices()
})
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

.btn-secondary {
  padding: 8px 16px;
  background: var(--color-white);
  color: var(--color-gray-dark);
  border: 1px solid var(--color-border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-secondary:hover {
  background-color: var(--color-gray-light);
}

.device-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
}

.device-card {
  padding: var(--spacing-md);
  transition: box-shadow 0.2s;
}

.device-card:hover {
  box-shadow: var(--shadow-md);
}

.device-card-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-md);
  border-bottom: 1px solid var(--border-color-light);
}

.device-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-gray-light);
  border-radius: var(--border-radius-sm);
}

.device-info {
  flex: 1;
  min-width: 0;
}

.device-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-gray-dark);
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.device-type {
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
}

.device-type-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.device-type-tag.sensor {
  background: #E8F3FF;
  color: #165DFF;
}

.device-type-tag.gateway {
  background: #E8FFEA;
  color: #00B42A;
}

.device-type-tag.actuator {
  background: #FFF3E8;
  color: #FF7D00;
}

.device-card-body {
  margin-bottom: var(--spacing-md);
}

.device-detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: var(--font-size-sm);
}

.detail-label {
  color: var(--color-gray-text);
  flex-shrink: 0;
}

.detail-value {
  color: var(--color-gray-dark);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.topic-text {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-xs);
}

.device-card-actions {
  display: flex;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-md);
  border-top: 1px solid var(--border-color-light);
}

.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--color-primary);
  background: none;
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-link-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  color: var(--color-danger);
  background: none;
  border: none;
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
}

.btn-page {
  padding: 6px 16px;
  background: var(--color-white);
  color: var(--color-gray-dark);
  border: 1px solid var(--color-border-color);
  border-radius: var(--border-radius-sm);
  font-size: var(--font-size-sm);
  cursor: pointer;
  font-family: var(--font-family);
}

.btn-page:hover:not(:disabled) {
  background-color: var(--color-gray-light);
}

.btn-page:disabled {
  color: var(--color-gray-text);
  cursor: not-allowed;
}

.page-info {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

.form-hint {
  margin-top: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-gray-text);
}

.credential-tip {
  padding: 10px 12px;
  margin-bottom: var(--spacing-md);
  background: #FFF7E6;
  border: 1px solid #FFD591;
  border-radius: var(--border-radius-sm);
  color: #AD6800;
  font-size: var(--font-size-sm);
  line-height: 1.5;
}

.credential-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: 6px 0;
}

.credential-label {
  width: 90px;
  flex-shrink: 0;
  color: var(--color-gray-text);
  font-size: var(--font-size-sm);
}

.credential-value {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: 6px 10px;
  background: var(--color-gray-light);
  border-radius: var(--border-radius-sm);
  min-width: 0;
}

.credential-value code {
  font-family: 'Courier New', monospace;
  font-size: var(--font-size-sm);
  color: var(--color-gray-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.empty-state-text {
  font-size: var(--font-size-sm);
  color: var(--color-gray-text);
  margin-top: var(--spacing-md);
}
</style>
