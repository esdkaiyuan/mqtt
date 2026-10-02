<template>
  <div class="tm-table">
    <div class="tm-table__toolbar">
      <el-button v-if="!readonly" type="primary" size="small" @click="$emit('add')">新增属性</el-button>
      <span class="tm-table__count">共 {{ properties.length }} 个属性</span>
    </div>
    <el-table :data="properties" border stripe>
      <el-table-column prop="identifier" label="标识符" min-width="150" />
      <el-table-column prop="name" label="名称" min-width="140" />
      <el-table-column label="数据类型" min-width="150">
        <template #default="{ row }">
          <span class="tm-table__mono">{{ dataTypeSummary(row.dataType) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="读写" width="80">
        <template #default="{ row }">{{ row.accessMode === 'rw' ? '读写' : '只读' }}</template>
      </el-table-column>
      <el-table-column label="必选" width="80">
        <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="!readonly" label="操作" width="120" fixed="right">
        <template #default="{ $index }">
          <el-button link type="primary" @click="$emit('edit', $index)">编辑</el-button>
          <el-button link type="danger" @click="$emit('remove', $index)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无属性</template>
    </el-table>
  </div>
</template>

<script setup>
import { dataTypeSummary } from '@/utils/thingModel'

defineProps({
  properties: { type: Array, default: () => [] },
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

.tm-table__mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
}
</style>