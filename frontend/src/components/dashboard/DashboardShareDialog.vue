<template>
  <el-dialog
    :model-value="modelValue"
    title="大屏分享"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="share-dialog">
      <div class="share-dialog__section">
        <div class="share-dialog__section-head">
          <span class="share-dialog__section-title">已有分享</span>
          <el-button size="small" @click="beginCreate">
            <svg-icon name="add" :size="14" />
            新建分享
          </el-button>
        </div>

        <p v-if="loading && shares.length === 0" class="share-dialog__hint">加载中...</p>
        <p v-else-if="shares.length === 0" class="share-dialog__hint">
          还没有分享。创建一个链接后，无需登录即可在浏览器打开大屏。
        </p>
        <div v-else class="share-list">
          <div v-for="share in shares" :key="share.id" class="share-row">
            <div class="share-row__main">
              <div class="share-row__title">
                <span class="share-row__name">{{ share.name }}</span>
                <el-tag size="small" :type="share.enabled ? 'success' : 'info'">
                  {{ share.enabled ? '已启用' : '已停用' }}
                </el-tag>
              </div>
              <div class="share-row__meta">
                <span>{{ (share.dashboardIds || []).length }} 个看板</span>
                <span>{{ share.intervalSeconds }}s 轮播</span>
                <span class="share-row__preview">令牌 {{ share.tokenPreview }}…</span>
              </div>
            </div>
            <div class="share-row__actions">
              <el-button link type="primary" @click="beginEdit(share)">编辑</el-button>
              <el-button link type="primary" @click="toggleEnabled(share)">
                {{ share.enabled ? '停用' : '启用' }}
              </el-button>
              <el-button link type="primary" @click="rotate(share)">轮换令牌</el-button>
              <el-button link type="danger" @click="remove(share)">删除</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="share-dialog__section">
        <div class="share-dialog__section-head">
          <span class="share-dialog__section-title">{{ form.id ? '编辑分享' : '新建分享' }}</span>
          <el-button v-if="form.id" link @click="beginCreate">取消编辑</el-button>
        </div>

        <el-form label-position="top" @submit.prevent>
          <div class="share-form__grid">
            <el-form-item label="分享名称" required>
              <el-input v-model="form.name" maxlength="64" placeholder="如：产线大屏" />
            </el-form-item>
            <el-form-item label="轮播间隔（秒）" required>
              <el-input-number
                v-model="form.intervalSeconds"
                :min="5"
                :max="3600"
                controls-position="right"
              />
            </el-form-item>
          </div>
          <el-form-item label="包含看板" required>
            <el-select
              v-model="form.dashboardIds"
              multiple
              filterable
              class="share-form__control"
              placeholder="按顺序选择要轮播的看板"
            >
              <el-option
                v-for="board in dashboards"
                :key="board.id"
                :label="board.name"
                :value="board.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="启用">
            <el-switch v-model="form.enabled" />
            <span class="share-form__hint">停用后公开链接立即不可访问</span>
          </el-form-item>
        </el-form>

        <div v-if="createdLink" class="share-link">
          <div class="share-link__head">
            <span class="share-link__title">分享链接（仅显示一次）</span>
            <el-button size="small" type="primary" @click="copyLink">复制链接</el-button>
          </div>
          <el-input :model-value="createdLink" readonly />
          <p class="share-form__hint">
            关闭后无法再次查看明文链接；如遗失请在列表中轮换令牌重新生成。
          </p>
        </div>

        <div class="share-form__actions">
          <el-button @click="emit('update:modelValue', false)">关闭</el-button>
          <el-button type="primary" :loading="saving" @click="save">
            {{ form.id ? '保存' : '创建分享' }}
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SvgIcon from '@/components/Icon.vue'
import { useDashboardShares } from '@/composables/useDashboardShares'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  dashboards: { type: Array, default: () => [] },
  presetDashboardId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue'])

const {
  shares,
  loading,
  saving,
  create,
  update,
  remove: removeShare,
  rotate: rotateShare
} = useDashboardShares()

const form = reactive(emptyForm())
const createdLink = ref('')

const absoluteLink = computed(() => {
  if (!createdLink.value) return ''
  return `${window.location.origin}${createdLink.value}`
})

function emptyForm() {
  return { id: null, name: '', dashboardIds: [], intervalSeconds: 15, enabled: true }
}

function resetForm(preset = null) {
  Object.assign(form, emptyForm())
  const presetId = preset ?? props.presetDashboardId
  if (presetId != null) {
    form.dashboardIds = [Number(presetId)]
    const board = props.dashboards.find((item) => String(item.id) === String(presetId))
    form.name = board ? `${board.name} 大屏` : '数据大屏'
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    createdLink.value = ''
    resetForm()
  },
  { immediate: true }
)

function beginCreate() {
  createdLink.value = ''
  resetForm()
}

function beginEdit(share) {
  createdLink.value = ''
  Object.assign(form, {
    id: share.id,
    name: share.name,
    dashboardIds: [...(share.dashboardIds || [])],
    intervalSeconds: share.intervalSeconds || 15,
    enabled: share.enabled !== false
  })
}

function payload() {
  return {
    name: form.name.trim(),
    dashboardIds: form.dashboardIds,
    intervalSeconds: form.intervalSeconds,
    enabled: form.enabled
  }
}

function validate() {
  if (!form.name.trim()) return '请填写分享名称'
  if (form.dashboardIds.length === 0) return '请至少选择一个看板'
  if (form.intervalSeconds < 5 || form.intervalSeconds > 3600) return '轮播间隔需在 5 ~ 3600 秒'
  return ''
}

async function save() {
  const error = validate()
  if (error) {
    ElMessage.warning(error)
    return
  }
  try {
    const result = form.id
      ? await update(form.id, payload())
      : await create(payload())
    if (result?.path) {
      createdLink.value = result.path
    }
    ElMessage.success(form.id ? '分享已更新' : '分享已创建')
    if (!form.id) {
      resetForm()
    } else {
      form.id = null
      resetForm()
    }
  } catch {
    // 具体原因由 axios 拦截器提示
  }
}

async function toggleEnabled(share) {
  try {
    await update(share.id, {
      name: share.name,
      dashboardIds: share.dashboardIds,
      intervalSeconds: share.intervalSeconds,
      enabled: !share.enabled
    })
    ElMessage.success(share.enabled ? '分享已停用' : '分享已启用')
  } catch {
    // 具体原因由 axios 拦截器提示
  }
}

async function rotate(share) {
  try {
    await ElMessageBox.confirm(
      `轮换后旧链接立即失效，确定重新生成「${share.name}」的令牌吗？`,
      '轮换分享令牌',
      { confirmButtonText: '轮换', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const result = await rotateShare(share.id)
    if (result?.path) createdLink.value = result.path
    ElMessage.success('令牌已轮换，请复制新链接')
  } catch {
    // 具体原因由 axios 拦截器提示
  }
}

async function remove(share) {
  try {
    await ElMessageBox.confirm(
      `删除后「${share.name}」的公开链接立即失效，确定继续吗？`,
      '删除分享',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await removeShare(share.id)
    ElMessage.success('分享已删除')
  } catch {
    // 具体原因由 axios 拦截器提示
  }
}

async function copyLink() {
  if (!absoluteLink.value) return
  try {
    await navigator.clipboard.writeText(absoluteLink.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择链接')
  }
}
</script>

<style scoped>
.share-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.share-dialog__section {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.share-dialog__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.share-dialog__section-title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.share-dialog__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.share-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.share-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
}

.share-row__main {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xxs, 4px);
  min-width: 0;
}

.share-row__title {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.share-row__name {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.share-row__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.share-row__preview {
  font-family: var(--font-family-mono);
}

.share-row__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--spacing-xs);
  flex-shrink: 0;
}

.share-form__grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(160px, 1fr);
  gap: var(--spacing-md);
}

.share-form__control {
  width: 100%;
}

.share-form__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.share-link {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-md);
  background: var(--color-bg);
  border-radius: var(--border-radius-sm);
}

.share-link__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.share-link__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.share-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
}

@media (max-width: 640px) {
  .share-form__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .share-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .share-row__actions {
    justify-content: flex-start;
  }
}
</style>
