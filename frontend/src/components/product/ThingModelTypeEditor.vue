<template>
  <div class="type-editor">
    <div class="type-editor__row">
      <el-select
        :model-value="dt.type"
        class="type-editor__type"
        @change="handleTypeChange"
      >
        <el-option
          v-for="option in typeOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <span class="type-editor__summary">{{ dataTypeSummary(dt) }}</span>
    </div>

    <!-- 数值类型：min / max / step / unit -->
    <div v-if="isNumeric" class="type-editor__grid">
      <el-form-item label="最小值">
        <el-input-number
          v-model="dt.min"
          :precision="integerOnly ? 0 : undefined"
          :controls="false"
          class="type-editor__number"
        />
      </el-form-item>
      <el-form-item label="最大值">
        <el-input-number
          v-model="dt.max"
          :precision="integerOnly ? 0 : undefined"
          :controls="false"
          class="type-editor__number"
        />
      </el-form-item>
      <el-form-item label="步长">
        <el-input-number
          v-model="dt.step"
          :precision="integerOnly ? 0 : undefined"
          :controls="false"
          class="type-editor__number"
        />
      </el-form-item>
      <el-form-item label="单位">
        <el-input v-model="dt.unit" placeholder="可空，如 ℃" />
      </el-form-item>
    </div>

    <!-- 文本：length -->
    <el-form-item v-else-if="dt.type === 'text'" label="最大长度">
      <el-input-number v-model="dt.length" :min="1" :max="10240" :precision="0" />
    </el-form-item>

    <!-- 枚举：specs 键值对 -->
    <div v-else-if="dt.type === 'enum'" class="type-editor__specs">
      <div class="type-editor__specs-head">
        <span>枚举项</span>
        <el-button link type="primary" @click="addEnumEntry">新增枚举项</el-button>
      </div>
      <div
        v-for="entry in enumEntries"
        :key="entry.key"
        class="type-editor__spec-row"
      >
        <el-input
          :model-value="entry.key"
          placeholder="键"
          class="type-editor__spec-key"
          @change="(value) => setEnumKey(entry.key, value)"
        />
        <el-input
          :model-value="entry.value"
          placeholder="展示值"
          class="type-editor__spec-value"
          @change="(value) => setEnumValue(entry.key, value)"
        />
        <el-button link type="danger" @click="removeEnumEntry(entry.key)">删除</el-button>
      </div>
      <p v-if="enumEntries.length === 0" class="type-editor__hint">至少需要一个枚举项</p>
    </div>

    <!-- 结构体：specs 成员数组，递归 -->
    <div v-else-if="dt.type === 'struct'" class="type-editor__specs">
      <div class="type-editor__specs-head">
        <span>成员</span>
        <el-button link type="primary" @click="addStructMember">新增成员</el-button>
      </div>
      <div
        v-for="(member, index) in dt.specs"
        :key="index"
        class="type-editor__member"
      >
        <div class="type-editor__member-head">
          <el-input v-model="member.identifier" placeholder="标识符" class="type-editor__member-id" />
          <el-input v-model="member.name" placeholder="成员名称" class="type-editor__member-name" />
          <el-button link type="danger" @click="removeStructMember(index)">删除</el-button>
        </div>
        <ThingModelTypeEditor v-model="member.dataType" :depth="depth + 1" />
      </div>
      <p v-if="(dt.specs || []).length === 0" class="type-editor__hint">至少需要一个成员</p>
    </div>

    <!-- 数组：item 递归 -->
    <div v-else-if="dt.type === 'array'" class="type-editor__nested">
      <span class="type-editor__nested-label">元素类型</span>
      <ThingModelTypeEditor v-model="dt.item" :depth="depth + 1" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { DATA_TYPES, createDataType, dataTypeSummary } from '@/utils/thingModel'

const props = defineProps({
  depth: {
    type: Number,
    default: 1
  }
})

const dt = defineModel({ type: Object, required: true })

// struct / array 递归深度上限 5（服务端 V11 / V12），到顶后不再允许继续嵌套
const canNest = computed(() => props.depth < 5)

const typeOptions = computed(() =>
  DATA_TYPES.filter((option) => canNest.value || !['struct', 'array'].includes(option.value))
)

const isNumeric = computed(() => ['int', 'float', 'double'].includes(dt.value.type))
const integerOnly = computed(() => dt.value.type === 'int')

const enumEntries = computed(() =>
  Object.entries(dt.value.specs || {}).map(([key, value]) => ({ key, value }))
)

function handleTypeChange(type) {
  dt.value = createDataType(type)
}

function addEnumEntry() {
  const specs = { ...(dt.value.specs || {}) }
  let index = Object.keys(specs).length
  let key = String(index)
  while (key in specs) {
    index += 1
    key = String(index)
  }
  specs[key] = ''
  dt.value.specs = specs
}

function setEnumKey(oldKey, newKey) {
  const specs = {}
  for (const [key, value] of Object.entries(dt.value.specs || {})) {
    specs[key === oldKey ? newKey : key] = value
  }
  dt.value.specs = specs
}

function setEnumValue(key, value) {
  dt.value.specs = { ...(dt.value.specs || {}), [key]: value }
}

function removeEnumEntry(key) {
  const specs = { ...(dt.value.specs || {}) }
  delete specs[key]
  dt.value.specs = specs
}

function addStructMember() {
  dt.value.specs = [
    ...(dt.value.specs || []),
    { identifier: '', name: '', dataType: createDataType('int') }
  ]
}

function removeStructMember(index) {
  dt.value.specs = (dt.value.specs || []).filter((_, i) => i !== index)
}
</script>

<style scoped>
.type-editor {
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  padding: var(--spacing-sm);
  background: var(--color-bg);
}

.type-editor__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.type-editor__type {
  width: 180px;
}

.type-editor__summary {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
  font-family: var(--font-family-mono);
}

.type-editor__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-sm) var(--spacing-md);
}

.type-editor__grid :deep(.el-form-item) {
  margin-bottom: 0;
}

.type-editor__number {
  width: 100%;
}

.type-editor__specs {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.type-editor__specs-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.type-editor__spec-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.type-editor__spec-key {
  width: 120px;
}

.type-editor__spec-value {
  flex: 1;
}

.type-editor__member {
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  padding: var(--spacing-sm);
  background: var(--color-card);
}

.type-editor__member-head {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.type-editor__member-id {
  width: 140px;
}

.type-editor__member-name {
  flex: 1;
}

.type-editor__nested {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.type-editor__nested-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-regular);
}

.type-editor__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}
</style>