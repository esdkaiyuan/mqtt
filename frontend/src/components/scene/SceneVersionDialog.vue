<template>
  <el-dialog
    :model-value="modelValue"
    :title="scene ? `版本历史 · ${scene.name}` : '版本历史'"
    width="760px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="version-dialog">
      <p class="version-dialog__hint">
        每次创建 / 保存 / 回滚都会保留一份完整配置快照，最多保留最近 20 个版本。
        回滚会覆盖当前配置（含启用状态），并生成一个新的回滚版本。
      </p>

      <p v-if="loading" class="version-dialog__hint">加载中...</p>
      <EmptyState
        v-else-if="versions.length === 0"
        description="暂无版本记录"
        :image-size="80"
      />
      <el-table v-else :data="versions" style="width: 100%">
        <el-table-column label="版本" width="110">
          <template #default="{ row, $index }">
            <span class="version-no">#{{ row.versionNo }}</span>
            <el-tag v-if="$index === 0" size="small" type="success" effect="plain">当前</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变更" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="CHANGE_TYPE_TAG_TYPES[row.changeType] || 'info'" effect="plain">
              {{ CHANGE_TYPE_LABELS[row.changeType] || row.changeType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sceneName" label="场景名称" min-width="130" show-overflow-tooltip />
        <el-table-column label="触发源" width="100">
          <template #default="{ row }">
            {{ TRIGGER_SOURCE_LABELS[row.triggerType] || row.triggerType || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="stepCount" label="步骤数" width="80" />
        <el-table-column label="保存时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row, $index }">
            <el-button
              link
              type="primary"
              :disabled="saving || $index === 0"
              @click="restore(row)"
            >
              回滚
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import EmptyState from '@/components/common/EmptyState.vue'
import { sceneApi } from '@/api/scene'
import { unwrapResult } from '@/utils/result'
import { TRIGGER_SOURCE_LABELS, formatDateTime } from '@/utils/scene'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  scene: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'restored'])

const CHANGE_TYPE_LABELS = { CREATE: '创建', UPDATE: '保存', RESTORE: '回滚' }
const CHANGE_TYPE_TAG_TYPES = { CREATE: 'success', UPDATE: 'primary', RESTORE: 'warning' }

const versions = ref([])
const loading = ref(false)
const saving = ref(false)

async function loadVersions() {
  if (!props.scene?.id) {
    versions.value = []
    return
  }
  loading.value = true
  try {
    versions.value = unwrapResult(await sceneApi.listSceneVersions(props.scene.id), [])
  } catch {
    versions.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.scene?.id],
  ([visible]) => {
    if (!visible) return
    loadVersions()
  },
  { immediate: true }
)

async function restore(version) {
  if (!props.scene?.id || !version?.id) return
  try {
    await ElMessageBox.confirm(
      `回滚到版本 #${version.versionNo}？当前配置会先保留为历史版本，可在回滚后再次恢复。`,
      '确认回滚',
      { confirmButtonText: '回滚', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await sceneApi.restoreSceneVersion(props.scene.id, version.id)
    ElMessage.success(`已回滚到版本 #${version.versionNo}`)
    emit('restored')
    await loadVersions()
  } catch {
    // 具体原因由 axios 拦截器提示（6312 / 6241）
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.version-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.version-dialog__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.6;
}

.version-no {
  margin-right: 6px;
  font-family: var(--font-family-mono);
  font-weight: var(--font-weight-medium);
}
</style>
