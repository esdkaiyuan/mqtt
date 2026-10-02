<template>
  <div class="product-list-page page">
    <PageHeader title="产品管理" desc="设备类型模板，定义认证方式、主题前缀与物模型">
      <template #actions>
        <el-button type="primary" :disabled="!canWrite" @click="openCreateDialog">
          <svg-icon name="add" :size="16" />
          创建产品
        </el-button>
      </template>
    </PageHeader>

    <div class="product-table-wrap">
      <el-table v-loading="loading" :data="products" border stripe>
        <el-table-column prop="productKey" label="产品标识" min-width="160">
          <template #default="{ row }">
            <span class="product-table__mono">{{ row.productKey }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="名称" min-width="150" />
        <el-table-column prop="authMode" label="认证方式" width="110" />
        <el-table-column prop="topicPrefix" label="主题前缀" min-width="180">
          <template #default="{ row }">
            <span class="product-table__mono">{{ row.topicPrefix }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
              {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物模型" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="openThingModel(row)">
              {{ row.thingModelVersion > 0 ? `v${row.thingModelVersion}` : '未建模' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!canWrite" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button link type="primary" @click="openThingModel(row)">物模型</el-button>
            <el-button link type="primary" :disabled="!canWrite" @click="toggleProduct(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" :disabled="!canWrite" @click="deleteProduct(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState description="暂无产品，点击「创建产品」添加" />
        </template>
      </el-table>
    </div>

    <ProductFormDialog
      v-model:visible="showFormDialog"
      :product="editingProduct"
      :loading="formLoading"
      @submit="handleSubmit"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SvgIcon from '@/components/Icon.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ProductFormDialog from '@/components/product/ProductFormDialog.vue'
import { useProductList } from '@/composables/useProductList'

const router = useRouter()

const {
  products,
  loading,
  canWrite,
  createProduct,
  updateProduct,
  toggleProduct,
  deleteProduct
} = useProductList()

const showFormDialog = ref(false)
const formLoading = ref(false)
const editingProduct = ref(null)

function openCreateDialog() {
  editingProduct.value = null
  showFormDialog.value = true
}

function openEditDialog(product) {
  editingProduct.value = product
  showFormDialog.value = true
}

function openThingModel(product) {
  router.push(`/workbench/products/${product.id}/thing-model`)
}

async function handleSubmit(form) {
  formLoading.value = true
  try {
    const ok = editingProduct.value
      ? await updateProduct(editingProduct.value.id, form)
      : await createProduct(form)
    if (ok) {
      showFormDialog.value = false
    }
  } finally {
    formLoading.value = false
  }
}
</script>

<style scoped>
.product-table-wrap {
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.product-table__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}
</style>