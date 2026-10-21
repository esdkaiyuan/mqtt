<template>
  <div class="hmi-list-page page">
    <PageHeader title="上位机" desc="按物模型一键生成单设备 Web 组态，实时看值、控值与调用服务">
      <template #actions>
        <el-button type="primary" @click="createHmi">
          <svg-icon name="add" :size="14" />
          新建上位机
        </el-button>
      </template>
    </PageHeader>

    <div class="hmi-intro card">
      <div class="hmi-intro__text">
        <h3>什么是上位机？</h3>
        <p>
          选择设备后，平台会按物模型的属性与服务自动生成开关、滑块、数值卡、趋势图和事件面板。
          写值通过设备影子下发，离线设备会在上线后自动补发。
        </p>
      </div>
      <div class="hmi-intro__flow">
        <span>选择设备</span>
        <span class="flow-arrow">→</span>
        <span>自动生成</span>
        <span class="flow-arrow">→</span>
        <span>保存运行</span>
      </div>
    </div>

    <div v-if="listQuery.isLoading.value" class="hmi-loading card">加载中...</div>

    <EmptyState v-else-if="views.length === 0" description="还没有上位机，按物模型生成一个吧">
      <el-button type="primary" size="small" @click="createHmi">新建上位机</el-button>
    </EmptyState>

    <div v-else class="hmi-cards">
      <div v-for="item in views" :key="item.id" class="hmi-card card" @click="openHmi(item)">
        <div class="hmi-card__head">
          <h3 class="hmi-card__title">{{ item.name }}</h3>
          <el-dropdown trigger="click" @command="(command) => handleCommand(command, item)">
            <el-button link class="hmi-card__more" @click.stop>···</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="delete">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <p class="hmi-card__device">{{ item.deviceName || `设备 ${item.deviceId}` }}</p>
        <p class="hmi-card__key">{{ item.deviceKey || '—' }}</p>
        <div class="hmi-card__meta">
          <span>{{ item.widgetCount }} 个控件</span>
          <span>{{ formatTime(item.updatedAt) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SvgIcon from '@/components/Icon.vue'
import { hmiApi } from '@/api/hmi'
import { unwrapResult } from '@/utils/result'

const router = useRouter()

const listQuery = useQuery({
  queryKey: ['hmi-views'],
  queryFn: async () => unwrapResult(await hmiApi.list(), [])
})

const views = computed(() => listQuery.data.value || [])

function createHmi() {
  router.push('/workbench/hmi/new')
}

function openHmi(item) {
  router.push(`/workbench/hmi/${item.id}`)
}

async function handleCommand(command, item) {
  if (command !== 'delete') return
  try {
    await ElMessageBox.confirm(`确定删除上位机「${item.name}」吗？`, '删除上位机', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await hmiApi.remove(item.id)
    ElMessage.success('上位机已删除')
    listQuery.refetch()
  } catch {
    // 错误提示由 axios 拦截器统一给出
  }
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.hmi-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-xl);
  margin-bottom: var(--grid-gutter);
}

.hmi-intro__text h3 {
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.hmi-intro__text p {
  max-width: 640px;
  font-size: var(--font-size-sm);
  line-height: 1.7;
  color: var(--color-text-tertiary);
}

.hmi-intro__flow {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.flow-arrow {
  color: var(--color-text-tertiary);
}

.hmi-loading {
  padding: var(--spacing-xl);
  color: var(--color-text-tertiary);
  font-size: var(--font-size-sm);
}

.hmi-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--grid-gutter);
}

.hmi-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  cursor: pointer;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast), transform var(--transition-fast);
}

.hmi-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.hmi-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.hmi-card__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-primary);
}

.hmi-card__more {
  color: var(--color-text-tertiary);
}

.hmi-card__device {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.hmi-card__key {
  font-family: var(--font-family-mono);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.hmi-card__meta {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--border-color-light);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

@media (max-width: 900px) {
  .hmi-intro {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
