<template>
  <div class="rule-center">
    <el-tabs v-model="activeTab" class="rule-center__tabs">
      <el-tab-pane label="规则定义" name="definitions">
        <RuleList v-if="activeTab === 'definitions'" />
      </el-tab-pane>
      <el-tab-pane label="执行记录" name="executions">
        <RuleExecutions v-if="activeTab === 'executions'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import RuleList from './RuleList.vue'
import RuleExecutions from './RuleExecutions.vue'

// 页签切换时懒加载，避免执行记录页在未激活时发起无效查询
const activeTab = ref('definitions')
</script>

<style scoped>
.rule-center__tabs :deep(.el-tabs__header) {
  margin-bottom: var(--grid-gutter);
}
</style>