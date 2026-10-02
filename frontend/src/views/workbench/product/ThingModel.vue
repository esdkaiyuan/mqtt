<template>
  <div class="thing-model-page page">
    <PageHeader :title="pageTitle" :desc="pageDesc">
      <template #actions>
        <input
          ref="fileInputRef"
          type="file"
          accept="application/json,.json"
          class="thing-model__file"
          @change="handleFileChange"
        />
        <el-button :disabled="!canWrite" @click="triggerImport">导入</el-button>
        <el-button :disabled="version === 0" @click="exportModel">导出</el-button>
        <el-button :disabled="!canWrite || version === 0" @click="clearModel">清空</el-button>
        <el-button
          type="primary"
          :disabled="!canWrite || !dirty"
          :loading="saving"
          @click="save"
        >
          保存
        </el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="thing-model">
      <div class="thing-model__meta">
        <el-tag :type="version > 0 ? 'success' : 'info'" size="small">
          {{ version > 0 ? `物模型 v${version}` : '未建模' }}
        </el-tag>
        <span v-if="dirty" class="thing-model__dirty">有未保存的修改</span>
        <span v-if="updatedAt" class="thing-model__time">最后保存：{{ updatedAt }}</span>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="属性" name="property">
          <ThingModelPropertyTable
            :properties="draft.properties"
            :readonly="!canWrite"
            @add="openAdd('property')"
            @edit="openEdit('property', $event)"
            @remove="removeItem('property', $event)"
          />
        </el-tab-pane>
        <el-tab-pane label="事件" name="event">
          <ThingModelEventTable
            :events="draft.events"
            :readonly="!canWrite"
            @add="openAdd('event')"
            @edit="openEdit('event', $event)"
            @remove="removeItem('event', $event)"
          />
        </el-tab-pane>
        <el-tab-pane label="服务" name="service">
          <ThingModelServiceTable
            :services="draft.services"
            :readonly="!canWrite"
            @add="openAdd('service')"
            @edit="openEdit('service', $event)"
            @remove="removeItem('service', $event)"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <ThingModelEditorDrawer
      v-model:visible="drawer.visible"
      :kind="drawer.kind"
      :draft="drawer.draft"
      @submit="handleDrawerSubmit"
    />
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import ThingModelPropertyTable from '@/components/product/ThingModelPropertyTable.vue'
import ThingModelEventTable from '@/components/product/ThingModelEventTable.vue'
import ThingModelServiceTable from '@/components/product/ThingModelServiceTable.vue'
import ThingModelEditorDrawer from '@/components/product/ThingModelEditorDrawer.vue'
import { useProductList } from '@/composables/useProductList'
import { useThingModel } from '@/composables/useThingModel'

const route = useRoute()
const productId = computed(() => route.params.id)

const { products, canWrite } = useProductList()
const currentProduct = computed(() =>
  products.value.find((product) => String(product.id) === String(productId.value))
)
const productKey = computed(() => currentProduct.value?.productKey)

const {
  draft,
  version,
  updatedAt,
  loading,
  dirty,
  saving,
  save,
  clearModel,
  exportModel,
  importModel,
  confirmLeave
} = useThingModel(productId, productKey)

const pageTitle = computed(() =>
  currentProduct.value ? `${currentProduct.value.productName} · 物模型` : '物模型'
)
const pageDesc = computed(() =>
  productKey.value ? `产品标识：${productKey.value}` : '定义设备的属性、事件与服务'
)

const activeTab = ref('property')

const LISTS = {
  property: () => draft.value.properties,
  event: () => draft.value.events,
  service: () => draft.value.services
}

const drawer = reactive({ visible: false, kind: 'property', index: -1, draft: null })

function openAdd(kind) {
  drawer.kind = kind
  drawer.index = -1
  drawer.draft = null
  drawer.visible = true
}

function openEdit(kind, index) {
  drawer.kind = kind
  drawer.index = index
  drawer.draft = LISTS[kind]()[index]
  drawer.visible = true
}

function removeItem(kind, index) {
  LISTS[kind]().splice(index, 1)
}

function handleDrawerSubmit(form) {
  const list = LISTS[drawer.kind]()
  if (drawer.index >= 0) {
    list.splice(drawer.index, 1, form)
  } else {
    list.push(form)
  }
}

const fileInputRef = ref(null)

function triggerImport() {
  fileInputRef.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  await importModel(file)
  event.target.value = ''
}

onBeforeRouteLeave(async () => {
  if (await confirmLeave()) return true
  return false
})
</script>

<style scoped>
.thing-model {
  padding: var(--spacing-md);
  background: var(--color-card);
  border: 1px solid var(--color-border-light);
  border-radius: var(--border-radius);
}

.thing-model__meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-sm);
}

.thing-model__dirty {
  font-size: var(--font-size-xs);
  color: var(--color-warning);
}

.thing-model__time {
  font-size: var(--font-size-xs);
  color: var(--color-text-tertiary);
}

.thing-model__file {
  display: none;
}
</style>