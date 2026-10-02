import { computed } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'
import { useAuthStore } from '@/stores/auth'

/**
 * 产品管理页的服务端状态与写操作。
 *
 * 列表走 Vue Query（queryKey `['products']`），写操作成功后失效该键重新拉取；
 * 停用 / 删除影响面大，先在动作内部做二次确认，确认框取消即视为放弃（返回 false）。
 * 写操作按 ADMIN 置灰仅用于前端体验，后端仍独立鉴权。
 */
export function useProductList() {
  const authStore = useAuthStore()
  const queryClient = useQueryClient()

  const productsQuery = useQuery({
    queryKey: ['products'],
    queryFn: async () => unwrapResult(await productApi.getList(), [])
  })

  const products = computed(() => productsQuery.data.value ?? [])
  const loading = computed(() => productsQuery.isFetching.value)
  const canWrite = computed(() => authStore.hasRole(['ADMIN']))

  function invalidate() {
    return queryClient.invalidateQueries({ queryKey: ['products'] })
  }

  async function createProduct(form) {
    try {
      await productApi.create(form)
      ElMessage.success('产品创建成功')
      await invalidate()
      return true
    } catch {
      return false
    }
  }

  async function updateProduct(id, form) {
    try {
      await productApi.update(id, form)
      ElMessage.success('产品更新成功')
      await invalidate()
      return true
    } catch {
      return false
    }
  }

  /**
   * 停用 / 启用切换（幂等）。停用会使旗下设备无法认证并踢线，故需二次确认。
   */
  async function toggleProduct(product) {
    const enabled = product.status === 'ENABLED'
    const action = enabled ? '停用' : '启用'
    try {
      await ElMessageBox.confirm(
        enabled
          ? `停用后「${product.productName}」旗下设备将无法通过认证，确定停用吗？`
          : `确定启用产品「${product.productName}」吗？`,
        `确认${action}`,
        { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }

    try {
      if (enabled) {
        await productApi.disable(product.id)
      } else {
        await productApi.enable(product.id)
      }
      ElMessage.success(`产品已${action}`)
      await invalidate()
      return true
    } catch {
      return false
    }
  }

  async function deleteProduct(product) {
    try {
      await ElMessageBox.confirm(
        `确定要删除产品「${product.productName}」吗？此操作不可恢复。`,
        '确认删除',
        { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }

    try {
      await productApi.remove(product.id)
      ElMessage.success('产品已删除')
      await invalidate()
      return true
    } catch {
      // 命中业务码 6003（产品下存在设备）时拦截器已提示后端文案，这里不再重复弹窗
      return false
    }
  }

  return {
    products,
    loading,
    canWrite,
    reload: () => productsQuery.refetch(),
    createProduct,
    updateProduct,
    toggleProduct,
    deleteProduct
  }
}

export default useProductList