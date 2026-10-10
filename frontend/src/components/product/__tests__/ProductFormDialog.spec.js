import { describe, expect, it } from 'vitest'
import { h } from 'vue'
import { mount } from '@vue/test-utils'
import ProductFormDialog from '../ProductFormDialog.vue'

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog', 'data-title': props.title }, [
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const ElFormStub = { name: 'ElForm', template: '<form class="stub-form"><slot /></form>' }

const ElFormItemStub = {
  name: 'ElFormItem',
  props: ['label'],
  template: '<div class="stub-form-item" :data-label="label"><slot /></div>'
}

const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template:
    '<input class="stub-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
}

const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div class="stub-select"><slot /></div>'
}

const ElOptionStub = {
  name: 'ElOption',
  props: ['label', 'value'],
  template: '<span class="stub-option" :data-value="value">{{ label }}</span>'
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['type', 'loading'],
  emits: ['click'],
  template: '<button class="stub-button" @click="$emit(\'click\')"><slot /></button>'
}

const STUBS = {
  ElDialog: ElDialogStub,
  ElForm: ElFormStub,
  ElFormItem: ElFormItemStub,
  ElInput: ElInputStub,
  ElSelect: ElSelectStub,
  ElOption: ElOptionStub,
  ElButton: ElButtonStub
}

async function mountDialog(props = {}) {
  // 弹层打开时才回填表单，故先以关闭态挂载再切到打开态，触发 watch(visible)
  const wrapper = mount(ProductFormDialog, {
    props: { visible: false, product: null, loading: false, ...props },
    global: { stubs: STUBS }
  })
  await wrapper.setProps({ visible: true })
  return wrapper
}

/** 通过 el-input stub 写入表单字段。 */
async function fillInput(wrapper, index, value) {
  await wrapper.findAll('.stub-input').at(index).setValue(value)
}

const selectValue = (wrapper) => wrapper.findComponent({ name: 'ElSelect' }).props('modelValue')

describe('components/product/ProductFormDialog 认证方式', () => {
  it('新建时默认一机一密且不显示刷机提示', async () => {
    const wrapper = await mountDialog()

    expect(selectValue(wrapper)).toBe('SECRET')
    expect(wrapper.find('.form-hint--warn').exists()).toBe(false)
  })

  it('认证方式下拉渲染三个与后端枚举对齐的选项', async () => {
    const wrapper = await mountDialog()

    const options = wrapper.findAll('.stub-option').map((item) => item.attributes('data-value'))
    expect(options).toEqual(['SECRET', 'X509', 'JWT'])
  })

  it('选择非一机一密时提示存量设备需刷机', async () => {
    const wrapper = await mountDialog()

    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'X509')

    expect(wrapper.find('.form-hint--warn').text()).toContain('存量设备需重新刷机')
  })

  it('编辑时回填产品认证方式并显示刷机提示', async () => {
    const wrapper = await mountDialog({
      product: { id: 1, productKey: 'env-sensor', productName: '环境传感器', authMode: 'JWT' }
    })

    expect(selectValue(wrapper)).toBe('JWT')
    expect(wrapper.find('.form-hint--warn').exists()).toBe(true)
  })

  it('产品缺少 authMode 时回填为 SECRET', async () => {
    const wrapper = await mountDialog({
      product: { id: 1, productKey: 'legacy', productName: '旧产品' }
    })

    expect(selectValue(wrapper)).toBe('SECRET')
  })

  it('提交时一并带出 authMode', async () => {
    const wrapper = await mountDialog()
    await fillInput(wrapper, 0, 'env-sensor')
    await fillInput(wrapper, 1, '环境传感器')
    await wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('update:modelValue', 'X509')

    await wrapper.findAll('.stub-button').at(-1).trigger('click')

    const payload = wrapper.emitted('submit')[0][0]
    expect(payload.productKey).toBe('env-sensor')
    expect(payload.productName).toBe('环境传感器')
    expect(payload.authMode).toBe('X509')
  })
})
