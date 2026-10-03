import { computed, ref } from 'vue'
import { useMutation } from '@tanstack/vue-query'
import { deviceApi } from '@/api/device'
import { unwrapResult } from '@/utils/result'

/**
 * 设备批量操作（T-18 设计文档 §11.2）。
 *
 * 选择集与结果抽屉在此收敛：页面只负责渲染选择状态、把操作目标
 * （`{ deviceIds, groupIds, tagIds }` 并集）交给对应动作。
 * 逐台结果一次性返回，用抽屉展示明细，无需轮询。
 */
export function useDeviceBatch() {
  const selectedIds = ref([])
  const resultVisible = ref(false)
  const result = ref(null)

  const selectedCount = computed(() => selectedIds.value.length)

  function isSelected(deviceId) {
    return selectedIds.value.includes(deviceId)
  }

  function toggleSelect(deviceId) {
    if (isSelected(deviceId)) {
      selectedIds.value = selectedIds.value.filter((id) => id !== deviceId)
    } else {
      selectedIds.value = [...selectedIds.value, deviceId]
    }
  }

  function clearSelection() {
    selectedIds.value = []
  }

  /** 本页全选 / 取消全选：只增删本页设备 ID，保留跨页已选。 */
  function toggleSelectAll(devices, checked) {
    const pageIds = devices.map((device) => device.id)
    if (checked) {
      selectedIds.value = [...new Set([...selectedIds.value, ...pageIds])]
    } else {
      const pageSet = new Set(pageIds)
      selectedIds.value = selectedIds.value.filter((id) => !pageSet.has(id))
    }
  }

  function closeResult() {
    resultVisible.value = false
  }

  function openResult(data) {
    result.value = data
    resultVisible.value = true
  }

  const commandsMutation = useMutation({
    mutationFn: async (payload) => unwrapResult(await deviceApi.batchCommands(payload), null),
    onSuccess: openResult
  })
  const enableMutation = useMutation({
    mutationFn: async (target) => unwrapResult(await deviceApi.batchEnable(target), null),
    onSuccess: openResult
  })
  const disableMutation = useMutation({
    mutationFn: async (target) => unwrapResult(await deviceApi.batchDisable(target), null),
    onSuccess: openResult
  })
  const assignGroupMutation = useMutation({
    mutationFn: async (payload) => unwrapResult(await deviceApi.batchAssignGroup(payload), null),
    onSuccess: openResult
  })
  const assignTagMutation = useMutation({
    mutationFn: async (payload) => unwrapResult(await deviceApi.batchAssignTag(payload), null),
    onSuccess: openResult
  })

  const running = computed(
    () =>
      commandsMutation.isPending.value ||
      enableMutation.isPending.value ||
      disableMutation.isPending.value ||
      assignGroupMutation.isPending.value ||
      assignTagMutation.isPending.value
  )

  return {
    selectedIds,
    selectedCount,
    isSelected,
    toggleSelect,
    clearSelection,
    toggleSelectAll,
    resultVisible,
    result,
    closeResult,
    running,
    sendBatchCommands: (payload) => commandsMutation.mutateAsync(payload),
    batchEnable: (target) => enableMutation.mutateAsync(target),
    batchDisable: (target) => disableMutation.mutateAsync(target),
    batchAssignGroup: (payload) => assignGroupMutation.mutateAsync(payload),
    batchAssignTag: (payload) => assignTagMutation.mutateAsync(payload)
  }
}

export default useDeviceBatch