import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { unwrapResult } from '@/utils/result'
import {
  clone,
  createEmptyThingModel,
  parseThingModel,
  serializeThingModel
} from '@/utils/thingModel'

const EMPTY_MODEL = createEmptyThingModel()

/** 从 Content-Disposition 拿不到文件名时，用产品标识与版本兜底。 */
function fallbackFileName(productKey, version) {
  const base = productKey || 'thing-model'
  return `${base}-thing-model-v${version || 0}.json`
}

function triggerDownload(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

/**
 * 物模型编辑器的服务端状态与草稿。
 *
 * - 服务端定义走 Vue Query（queryKey `['thing-model', productId]`），拉到后重置草稿；
 * - `draft` 是页面直接编辑的对象，`dirty` 以序列化结果比对判定是否有未保存修改；
 * - 导入 / 导出走 Blob 与文件读取，前端不重复校验（以服务端校验为准）。
 *
 * @param {import('vue').Ref<string|number>} productId 产品 ID
 * @param {import('vue').Ref<string>} [productKey] 产品标识，用于导出文件名兜底
 */
export function useThingModel(productId, productKey) {
  const queryClient = useQueryClient()

  const modelQuery = useQuery({
    queryKey: computed(() => ['thing-model', productId.value]),
    queryFn: async () => {
      const data = unwrapResult(await productApi.getThingModel(productId.value), {})
      return {
        model: parseThingModel(data?.thingModel),
        version: data?.version ?? 0,
        updatedAt: data?.updatedAt ?? null
      }
    }
  })

  const savedModel = computed(() => modelQuery.data.value?.model ?? EMPTY_MODEL)
  const version = computed(() => modelQuery.data.value?.version ?? 0)
  const updatedAt = computed(() => modelQuery.data.value?.updatedAt ?? null)
  const loading = computed(() => modelQuery.isFetching.value)

  const draft = ref(clone(EMPTY_MODEL))
  const saving = ref(false)

  // 拉到（或重新拉到）服务端定义后覆盖草稿；这是唯一的草稿重置来源
  watch(
    savedModel,
    (model) => {
      draft.value = clone(model)
    },
    { immediate: true }
  )

  const dirty = computed(
    () => serializeThingModel(draft.value) !== serializeThingModel(savedModel.value)
  )

  async function refresh() {
    await modelQuery.refetch()
    await queryClient.invalidateQueries({ queryKey: ['products'] })
  }

  async function save() {
    saving.value = true
    try {
      await productApi.saveThingModel(productId.value, serializeThingModel(draft.value))
      ElMessage.success('物模型已保存')
      await refresh()
      return true
    } catch {
      return false
    } finally {
      saving.value = false
    }
  }

  async function clearModel() {
    try {
      await ElMessageBox.confirm(
        '清空后该产品的属性 / 事件 / 服务定义将全部移除，确定继续吗？',
        '确认清空物模型',
        { confirmButtonText: '清空', cancelButtonText: '取消', type: 'warning' }
      )
    } catch {
      return false
    }

    try {
      await productApi.clearThingModel(productId.value)
      ElMessage.success('物模型已清空')
      await refresh()
      return true
    } catch {
      return false
    }
  }

  /** 导出当前<b>服务端已保存</b>的 TSL（未保存改动不会被导出）。 */
  async function exportModel() {
    try {
      const blob = await productApi.exportThingModel(productId.value)
      triggerDownload(blob, fallbackFileName(productKey?.value, version.value))
      return true
    } catch {
      return false
    }
  }

  /** 读取本地 TSL 文件并导入（等价于保存，同样经服务端校验）。 */
  async function importModel(file) {
    if (!file) return false
    let text
    try {
      text = await file.text()
    } catch {
      ElMessage.error('文件读取失败')
      return false
    }
    try {
      const parsed = JSON.parse(text)
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        ElMessage.error('文件内容不是合法的物模型 JSON')
        return false
      }
    } catch {
      ElMessage.error('文件内容不是合法的 JSON')
      return false
    }

    try {
      await productApi.importThingModel(productId.value, text)
      ElMessage.success('物模型导入成功')
      await refresh()
      return true
    } catch {
      return false
    }
  }

  /** 有未保存修改时弹二次确认；返回是否允许离开。 */
  async function confirmLeave() {
    if (!dirty.value) return true
    try {
      await ElMessageBox.confirm('物模型有未保存的修改，确定离开吗？', '未保存提示', {
        confirmButtonText: '离开',
        cancelButtonText: '留在本页',
        type: 'warning'
      })
      return true
    } catch {
      return false
    }
  }

  return {
    draft,
    version,
    updatedAt,
    loading,
    dirty,
    saving,
    reload: refresh,
    save,
    clearModel,
    exportModel,
    importModel,
    confirmLeave
  }
}

export default useThingModel