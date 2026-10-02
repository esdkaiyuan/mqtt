<template>
  <div class="tm-table">
    <div class="tm-table__toolbar">
      <el-button v-if="!readonly" type="primary" size="small" @click="$emit('add')">新增服务</el-button>
      <span class="tm-table__count">共 {{ services.length }} 个服务</span>
    </div>
    <el-table :data="services" border stripe>
      <el-table-column prop="identifier" label="标识符" min-width="150" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="调用方式" width="110">
        <template #default="{ row }">{{ row.callType === 'sync' ? '同步' : '异步' }}</template>
      </el-table-column>
      <el-table-column label="入参" width="80">
        <template #default="{ row }">{{ (row.inputData || []).length }}</template>
      </el-table-column>
      <el-table-column label="出参" width="80">
        <template #default="{ row }">{{ (row.outputData || []).length }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="!readonly" label="操作" width="120" fixed="right">
        <template #default="{ $index }">
          <el-button link type="primary" @click="$emit('edit', $index)">编辑</el-button>
          <el-button link type="danger" @click="$emit('remove', $index)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无服务</template>
    </el-table>
  </div>
</template>

<script setup>
defineProps({
  services: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false }
})

defineEmits(['add', 'edit', 'remove'])
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