<template>
  <el-empty
    class="app-empty"
    :description="description"
    :image-size="imageSize"
  >
    <slot />
  </el-empty>
</template>

<script setup>
defineProps({
  description: {
    type: String,
    default: '暂无数据'
  },
  imageSize: {
    type: Number,
    default: 100
  }
})
</script>

<style scoped>
.app-empty {
  padding: var(--spacing-2xl) 0;
}
</style>