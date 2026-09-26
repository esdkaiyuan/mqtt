<template>
  <div class="data-table">
    <el-table
      :data="paginatedData"
      style="width: 100%"
      v-loading="loading"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      border
      stripe
    >
      <el-table-column v-if="selectable" type="selection" width="55" align="center" />

      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :sortable="column.sortable"
        :align="column.align || 'center'"
        show-overflow-tooltip
      >
        <template #default="{ row }" v-if="column.slot">
          <slot :name="column.slot" :row="row" />
        </template>
      </el-table-column>

      <el-table-column v-if="actions.length > 0" label="操作" :width="actionWidth" align="center" fixed="right">
        <template #default="{ row }">
          <el-button
            v-for="action in actions"
            :key="action.key"
            :type="action.type || 'primary'"
            :icon="action.icon"
            size="small"
            link
            @click="$emit('action', { key: action.key, row })"
          >
            {{ action.label }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer" v-if="showPagination">
      <div class="footer-left">
        <span v-if="selectable" class="selection-info">
          已选择 {{ selectedRows.length }} 项
        </span>
      </div>
      <div class="footer-right">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalItems"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  columns: {
    type: Array,
    default: () => []
  },
  actions: {
    type: Array,
    default: () => []
  },
  actionWidth: {
    type: String,
    default: '150'
  },
  selectable: {
    type: Boolean,
    default: false
  },
  showPagination: {
    type: Boolean,
    default: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  defaultPageSize: {
    type: Number,
    default: 10
  }
})

const emit = defineEmits(['action', 'selection-change', 'page-change'])

const currentPage = ref(1)
const pageSize = ref(props.defaultPageSize)
const selectedRows = ref([])
const sortProp = ref('')
const sortOrder = ref('')

const totalItems = computed(() => {
  return sortedData.value.length
})

const sortedData = computed(() => {
  if (!sortProp.value || !sortOrder.value) {
    return props.data
  }

  return [...props.data].sort((a, b) => {
    const aVal = a[sortProp.value]
    const bVal = b[sortProp.value]

    if (sortOrder.value === 'ascending') {
      return aVal > bVal ? 1 : -1
    } else {
      return aVal < bVal ? 1 : -1
    }
  })
})

const paginatedData = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return sortedData.value.slice(start, end)
})

const handleSelectionChange = (selection) => {
  selectedRows.value = selection
  emit('selection-change', selection)
}

const handleSortChange = ({ prop, order }) => {
  sortProp.value = prop
  sortOrder.value = order
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  emitPageChange()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  emitPageChange()
}

const emitPageChange = () => {
  emit('page-change', {
    page: currentPage.value,
    pageSize: pageSize.value
  })
}

watch(() => props.data, () => {
  currentPage.value = 1
})

defineExpose({
  selectedRows,
  currentPage,
  pageSize
})
</script>

<style scoped>
.data-table {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #ebeef5;
}

.footer-left {
  color: #666;
  font-size: 13px;
}

.selection-info {
  color: #409eff;
}

.footer-right {
  display: flex;
  align-items: center;
}
</style>
