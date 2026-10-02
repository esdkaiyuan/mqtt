<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="520px"
    :close-on-click-modal="false"
  >
    <el-form label-position="top" class="tm-form" @submit.prevent>
      <el-form-item label="标识符" required>
        <el-input v-model="form.identifier" placeholder="如 temperature，字母开头" />
      </el-form-item>
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="展示名称，如 环境温度" />
      </el-form-item>

      <!-- 属性 -->
      <template v-if="kind === 'property'">
        <el-form-item label="读写权限">
          <el-select v-model="form.accessMode">
            <el-option
              v-for="option in ACCESS_MODES"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="必选属性">
          <el-switch v-model="form.required" />
          <span class="tm-form__hint">开启后影子中该属性缺失即视为未收敛</span>
        </el-form-item>
        <el-form-item label="数据类型">
          <ThingModelTypeEditor v-model="form.dataType" />
        </el-form-item>
      </template>

      <!-- 事件 -->
      <template v-else-if="kind === 'event'">
        <el-form-item label="事件类型">
          <el-select v-model="form.type">
            <el-option
              v-for="option in EVENT_TYPES"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="输出参数">
          <ThingModelParamList v-model="form.outputData" title="输出参数" />
        </el-form-item>
      </template>

      <!-- 服务 -->
      <template v-else>
        <el-form-item label="调用方式">
          <el-select v-model="form.callType">
            <el-option
              v-for="option in CALL_TYPES"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="入参">
          <ThingModelParamList v-model="form.inputData" title="入参" allow-required />
        </el-form-item>
        <el-form-item label="出参">
          <ThingModelParamList v-model="form.outputData" title="出参" />
        </el-form-item>
      </template>

      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import ThingModelTypeEditor from './ThingModelTypeEditor.vue'
import ThingModelParamList from './ThingModelParamList.vue'
import {
  ACCESS_MODES,
  CALL_TYPES,
  EVENT_TYPES,
  clone,
  createEvent,
  createProperty,
  createService
} from '@/utils/thingModel'

const props = defineProps({
  kind: {
    type: String,
    default: 'property'
  },
  draft: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['submit'])

const visible = defineModel('visible', { type: Boolean })

const TITLES = { property: '属性', event: '事件', service: '服务' }

const title = computed(() => `${props.draft ? '编辑' : '新增'}${TITLES[props.kind] || ''}`)

function emptyOf(kind) {
  if (kind === 'event') return createEvent()
  if (kind === 'service') return createService()
  return createProperty()
}

const form = ref(createProperty())

// 打开时把草稿深拷贝到本地，取消不影响列表中的原对象
watch(
  () => visible.value,
  (open) => {
    if (open) {
      form.value = clone(props.draft || emptyOf(props.kind))
    }
  }
)

function handleSubmit() {
  if (!form.value.identifier?.trim()) {
    ElMessage.warning('请填写标识符')
    return
  }
  if (!form.value.name?.trim()) {
    ElMessage.warning('请填写名称')
    return
  }
  emit('submit', clone(form.value))
  visible.value = false
}
</script>

<style scoped>
.tm-form :deep(.el-form-item) {
  margin-bottom: var(--spacing-md);
}

.tm-form :deep(.el-select) {
  width: 100%;
}

.tm-form__hint {
  margin-left: var(--spacing-sm);
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>