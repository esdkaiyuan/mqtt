<template>
  <div class="tm-table">
    <div class="tm-table__toolbar">
      <el-button v-if="!readonly" type="primary" size="small" @click="$emit('add')">新增事件</el-button>
      <span class="tm-table__count">共 {{ events.length }} 个事件</span>
    </div>
    <el-table :data="events" border stripe>
      <el-table-column prop="identifier" label="标识符" min-width="150" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)" size="small">{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="输出参数" width="100">
        <template #default="{ row }">{{ (row.outputData || []).length }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="!readonly" label="操作" width="120" fixed="right">
        <template #default="{ $index }">
          <el-button link type="primary" @click="$emit('edit', $index)">编辑</el-button>
          <el-button link type="danger" @click="$emit('remove', $index)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无事件</template>
    </el-table>
  </div>
</template>

<script setup>
defineProps({
  events: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false }
})

defineEmits(['add', 'edit', 'remove'])

function typeTag(type) {
  if (type === 'alert') return 'warning'
  if (type === 'fault') return 'danger'
  return 'info'
}
</script>

<style scoped>
.tm-table__toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.tm-table__count {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>