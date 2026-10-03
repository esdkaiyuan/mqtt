<template>
  <div class="rule-list">
    <div class="filter-bar">
      <el-select
        :model-value="filters.sourceType"
        placeholder="全部来源"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('sourceType', value)"
      >
        <el-option
          v-for="option in SOURCE_TYPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        :model-value="filters.actionType"
        placeholder="全部动作"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('actionType', value)"
      >
        <el-option
          v-for="option in ACTION_TYPE_OPTIONS"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <el-select
        :model-value="filters.enabled"
        placeholder="全部状态"
        clearable
        class="filter-item"
        @update:model-value="(value) => setFilter('enabled', value)"
      >
        <el-option label="启用" value="ENABLED" />
        <el-option label="停用" value="DISABLED" />
      </el-select>
      <el-input
        :model-value="filters.keyword"
        placeholder="搜索规则名称"
        clearable
        class="filter-item filter-item--wide"
        @update:model-value="(value) => setFilter('keyword', value)"
      />
      <div class="filter-actions">
        <el-button @click="resetFilters">重置</el-button>
        <el-button :loading="loading" @click="refresh">
          <svg-icon name="refresh" :size="14" />
          刷新
        </el-button>
        <el-button type="primary" @click="openCreate">
          <svg-icon name="add" :size="16" />
          新建规则
        </el-button>
      </div>
    </div>

    <div v-if="loading && rules.length === 0" class="loading-overlay">加载中...</div>

    <EmptyState
      v-else-if="rules.length === 0"
      description="暂无消息规则，点击「新建规则」开始配置"
    />

    <template v-else>
      <div class="rule-table card">
        <el-table :data="rules" style="width: 100%">
          <el-table-column prop="name" label="规则名称" min-width="140" />
          <el-table-column label="来源" width="90">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ SOURCE_TYPE_LABELS[row.sourceType] || row.sourceType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发条件" min-width="180">
            <template #default="{ row }">
              <span class="rule-mono">{{ formatTriggerCondition(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行动作" min-width="180">
            <template #default="{ row }">
              <span class="rule-mono">{{ formatActionSummary(row) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="冷却" width="80">
            <template #default="{ row }">
              <span>{{ row.cooldownSeconds > 0 ? `${row.cooldownSeconds}s` : '不限' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近触发" width="170">
            <template #default="{ row }">{{ formatDateTime(row.lastTriggeredAt) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-switch
                :model-value="row.enabled === 1"
                :loading="togglingId === row.id"
                @change="(value) => handleToggle(row, value)"
              />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState description="暂无消息规则" :image-size="80" />
          </template>
        </el-table>
      </div>

      <div class="rule-pagination">
        <el-pagination
          layout="total, sizes, prev, pager, next"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          background
          @current-change="goToPage"
          @size-change="changePageSize"
        />
      </div>
    </template>

    <RuleEditor v-model="editorVisible" :rule="editingRule" @saved="refresh" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import RuleEditor from './RuleEditor.vue'
import { useRuleListQuery, useRuleMutations } from '@/composables/useRules'
import {
  SOURCE_TYPE_LABELS,
  SOURCE_TYPE_OPTIONS,
  ACTION_TYPE_OPTIONS,
  formatTriggerCondition,
  formatActionSummary,
  formatDateTime
} from '@/utils/rule'

const {
  filters,
  currentPage,
  pageSize,
  rules,
  total,
  loading,
  setFilter,
  resetFilters,
  goToPage,
  changePageSize,
  refresh
} = useRuleListQuery()

const { setEnabled, deleteRule } = useRuleMutations()

const editorVisible = ref(false)
const editingRule = ref(null)
const togglingId = ref(null)

function openCreate() {
  editingRule.value = null
  editorVisible.value = true
}

function openEdit(rule) {
  editingRule.value = rule
  editorVisible.value = true
}

/** 启停开关：仅切换 enabled，成功后失效规则列表缓存 */
async function handleToggle(rule, value) {
  togglingId.value = rule.id
  try {
    await setEnabled(rule.id, value)
    ElMessage.success(value ? '规则已启用' : '规则已停用')
  } catch {
    // 失败时列表缓存未失效，开关会回弹到原值
  } finally {
    togglingId.value = null
  }
}

async function handleDelete(rule) {
  try {
    await ElMessageBox.confirm(`确定要删除规则「${rule.name}」吗？历史执行记录保留。`, '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await deleteRule(rule.id)
    ElMessage.success('规则已删除')
  } catch {
    // 失败原因由拦截器提示
  }
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--grid-gutter);
  padding: var(--spacing-md);
  flex-wrap: wrap;
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.filter-item {
  width: 150px;
}

.filter-item--wide {
  width: 200px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

.rule-table {
  padding: var(--spacing-sm);
}

.rule-mono {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.rule-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--grid-gutter);
}
</style>