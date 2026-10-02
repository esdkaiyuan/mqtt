<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <el-breadcrumb-item
      v-for="(item, index) in items"
      :key="index"
      :to="item.to"
    >
      {{ item.label }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const SECTIONS = [
  { prefix: '/workbench', label: '工作台', to: '/workbench/dashboard' },
  { prefix: '/docs', label: '文档', to: '/docs' }
]

const items = computed(() => {
  const list = []
  const section = SECTIONS.find((s) => route.path.startsWith(s.prefix))
  if (section) {
    list.push({ label: section.label, to: section.to })
  }
  route.matched.forEach((record) => {
    if (record.meta?.title) {
      list.push({ label: record.meta.title, to: record.path })
    }
  })
  return list
})
</script>

<style scoped>
.app-breadcrumb {
  font-size: var(--font-size-sm);
  line-height: var(--topbar-height);
}
</style>