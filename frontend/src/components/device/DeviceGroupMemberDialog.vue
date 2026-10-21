<template>
  <el-dialog
    :model-value="modelValue"
    :title="group ? `共享「${group.name}」` : '分组共享'"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="(value) => emit('update:modelValue', value)"
  >
    <div class="member-dialog">
      <p class="member-dialog__hint">
        共享包含该分组及其所有子分组。VIEW 只读；OPERATE 可查看设备并下发命令 / 写影子。
        分组结构与成员管理仍仅限所有者。
      </p>

      <div class="member-add">
        <el-input
          v-model="form.username"
          class="member-add__input"
          maxlength="100"
          placeholder="用户名或邮箱"
          @keyup.enter="addMember"
        />
        <el-select v-model="form.permission" class="member-add__permission">
          <el-option label="只读（VIEW）" value="VIEW" />
          <el-option label="可操作（OPERATE）" value="OPERATE" />
        </el-select>
        <el-button type="primary" :loading="saving" @click="addMember">添加</el-button>
      </div>

      <p v-if="loading && members.length === 0" class="member-dialog__hint">加载中...</p>
      <p v-else-if="members.length === 0" class="member-dialog__hint">还没有共享成员。</p>
      <div v-else class="member-list">
        <div v-for="member in members" :key="member.id" class="member-row">
          <div class="member-row__main">
            <span class="member-row__name">{{ member.username || '—' }}</span>
            <span class="member-row__email">{{ member.email || '—' }}</span>
          </div>
          <el-select
            :model-value="member.permission"
            class="member-row__permission"
            :disabled="saving"
            @change="(value) => changePermission(member, value)"
          >
            <el-option label="只读（VIEW）" value="VIEW" />
            <el-option label="可操作（OPERATE）" value="OPERATE" />
          </el-select>
          <el-button link type="danger" :disabled="saving" @click="removeMember(member)">移除</el-button>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deviceGroupApi } from '@/api/deviceGroup'
import { unwrapResult } from '@/utils/result'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  group: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])

const members = ref([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ username: '', permission: 'VIEW' })

async function loadMembers() {
  if (!props.group?.id) {
    members.value = []
    return
  }
  loading.value = true
  try {
    members.value = unwrapResult(await deviceGroupApi.listMembers(props.group.id), [])
  } catch {
    members.value = []
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.group?.id],
  ([visible]) => {
    if (!visible) return
    form.username = ''
    form.permission = 'VIEW'
    loadMembers()
  },
  { immediate: true }
)

async function addMember() {
  const username = form.username.trim()
  if (!username) {
    ElMessage.warning('请输入用户名或邮箱')
    return
  }
  saving.value = true
  try {
    await deviceGroupApi.addMember(props.group.id, {
      username,
      permission: form.permission
    })
    form.username = ''
    ElMessage.success('成员已添加')
    await loadMembers()
  } catch {
    // 具体原因由 axios 拦截器提示
  } finally {
    saving.value = false
  }
}

async function changePermission(member, permission) {
  saving.value = true
  try {
    await deviceGroupApi.updateMember(props.group.id, member.id, { permission })
    member.permission = permission
    ElMessage.success('权限已更新')
  } catch {
    await loadMembers()
  } finally {
    saving.value = false
  }
}

async function removeMember(member) {
  try {
    await ElMessageBox.confirm(
      `移除后「${member.username || member.email || '该成员'}」将立即失去本分组子树的访问权限，确定继续吗？`,
      '移除共享成员',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await deviceGroupApi.removeMember(props.group.id, member.id)
    ElMessage.success('成员已移除')
    await loadMembers()
  } catch {
    // 具体原因由 axios 拦截器提示
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.member-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.member-dialog__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  line-height: 1.6;
}

.member-add {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px auto;
  gap: var(--spacing-sm);
  align-items: center;
}

.member-add__input,
.member-add__permission {
  width: 100%;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  overflow: hidden;
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-card);
}

.member-row__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.member-row__name {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
}

.member-row__email {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.member-row__permission {
  width: 150px;
}

@media (max-width: 640px) {
  .member-add {
    grid-template-columns: minmax(0, 1fr);
  }

  .member-row {
    flex-wrap: wrap;
  }
}
</style>
