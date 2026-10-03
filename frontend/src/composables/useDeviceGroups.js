import { computed } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { deviceGroupApi } from '@/api/deviceGroup'
import { deviceTagApi } from '@/api/deviceTag'
import { unwrapResult } from '@/utils/result'

const GROUP_TREE_KEY = ['device-group-tree']
const GROUP_LIST_KEY = ['device-group-list']
const TAG_LIST_KEY = ['device-tag-list']

/** 分组增删改都会改变树与平铺列表，统一在此收敛失效范围。 */
function invalidateGroupCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: GROUP_TREE_KEY })
  queryClient.invalidateQueries({ queryKey: GROUP_LIST_KEY })
}

/** 标签增删改只需失效标签列表。 */
function invalidateTagCaches(queryClient) {
  queryClient.invalidateQueries({ queryKey: TAG_LIST_KEY })
}

/**
 * 分组树查询（含 children / deviceCount）。
 * 供分组管理页的 el-tree 与设备列表的分组筛选选择器共用同一份缓存。
 */
export function useDeviceGroupTreeQuery() {
  const treeQuery = useQuery({
    queryKey: GROUP_TREE_KEY,
    queryFn: async () => unwrapResult(await deviceGroupApi.tree(), [])
  })

  return {
    tree: computed(() => treeQuery.data.value ?? []),
    loading: computed(() => treeQuery.isFetching.value),
    refresh: () => treeQuery.refetch()
  }
}

/** 分组平铺列表查询（下拉 / 父分组选择器用）。 */
export function useDeviceGroupListQuery() {
  const listQuery = useQuery({
    queryKey: GROUP_LIST_KEY,
    queryFn: async () => unwrapResult(await deviceGroupApi.list(), [])
  })

  return {
    groups: computed(() => listQuery.data.value ?? []),
    loading: computed(() => listQuery.isFetching.value),
    refresh: () => listQuery.refetch()
  }
}

/** 分组增删改；成功后失效树与平铺列表。 */
export function useDeviceGroupMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => invalidateGroupCaches(queryClient)

  const createMutation = useMutation({
    mutationFn: (payload) => deviceGroupApi.create(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => deviceGroupApi.update(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => deviceGroupApi.delete(id),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    createGroup: (payload) => createMutation.mutateAsync(payload),
    updateGroup: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteGroup: (id) => deleteMutation.mutateAsync(id)
  }
}

/** 标签列表查询。 */
export function useTagListQuery() {
  const listQuery = useQuery({
    queryKey: TAG_LIST_KEY,
    queryFn: async () => unwrapResult(await deviceTagApi.list(), [])
  })

  return {
    tags: computed(() => listQuery.data.value ?? []),
    loading: computed(() => listQuery.isFetching.value),
    refresh: () => listQuery.refetch()
  }
}

/** 标签增删改；成功后失效标签列表。 */
export function useTagMutations() {
  const queryClient = useQueryClient()
  const invalidate = () => invalidateTagCaches(queryClient)

  const createMutation = useMutation({
    mutationFn: (payload) => deviceTagApi.create(payload),
    onSuccess: invalidate
  })
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }) => deviceTagApi.update(id, payload),
    onSuccess: invalidate
  })
  const deleteMutation = useMutation({
    mutationFn: (id) => deviceTagApi.delete(id),
    onSuccess: invalidate
  })

  return {
    saving: computed(() => createMutation.isPending.value || updateMutation.isPending.value),
    deleting: computed(() => deleteMutation.isPending.value),
    createTag: (payload) => createMutation.mutateAsync(payload),
    updateTag: (id, payload) => updateMutation.mutateAsync({ id, payload }),
    deleteTag: (id) => deleteMutation.mutateAsync(id)
  }
}