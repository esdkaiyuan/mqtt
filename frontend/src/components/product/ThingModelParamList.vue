<template>
  <div class="param-list">
    <div class="param-list__head">
      <span class="param-list__title">{{ title }}</span>
      <el-button link type="primary" @click="addParam">新增参数</el-button>
    </div>

    <p v-if="list.length === 0" class="param-list__hint">暂无参数</p>

    <div v-for="(param, index) in list" :key="index" class="param-list__item">
      <div class="param-list__row">
        <el-input v-model="param.identifier" placeholder="标识符" class="param-list__id" />
        <el-input v-model="param.name" placeholder="参数名称" class="param-list__name" />
        <el-checkbox v-if="allowRequired" v-model="param.required" label="必填" />
        <el-button link type="danger" @click="removeParam(index)">删除</el-button>
      </div>
      <ThingModelTypeEditor v-model="param.dataType" />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ThingModelTypeEditor from './ThingModelTypeEditor.vue'
import { createParam } from '@/utils/thingModel'

const props = defineProps({
  title: {
    type: String,
    default: '参数'
  },
  allowRequired: {
    type: Boolean,
    default: false
  }
})

const model = defineModel({ type: Array, required: true })

const list = computed(() => model.value || [])

function addParam() {
  model.value = [...list.value, createParam(props.allowRequired)]
}

function removeParam(index) {
  model.value = list.value.filter((_, i) => i !== index)
}
</script>

<style scoped>
.param-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.param-list__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.param-list__title {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}

.param-list__hint {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.param-list__item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius-sm);
  background: var(--color-card);
}

.param-list__row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.param-list__id {
  width: 160px;
}

.param-list__name {
  flex: 1;
}
</style>