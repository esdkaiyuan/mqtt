import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { h, inject, provide, computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'

/** 共享替身：vi.mock 会被提升到文件顶部，用 vi.hoisted 保证工厂执行时替身已就绪。 */
const mocks = vi.hoisted(() => ({
  message: { success: vi.fn(), warning: vi.fn(), info: vi.fn(), error: vi.fn() },
  confirm: vi.fn(() => Promise.resolve()),
  listFirmwares: vi.fn(),
  downloadFirmware: vi.fn(),
  uploadFirmware: vi.fn(),
  removeFirmware: vi.fn(),
  getProducts: vi.fn()
}))

/** 页面显式 `import { ElMessage, ElMessageBox } from 'element-plus'`，直接对根模块打桩。 */
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm }
}))

/** 写权限由角色决定，测试统一放行。 */
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ hasRole: () => true })
}))

vi.mock('@/api/ota', () => ({
  otaApi: {
    listFirmwares: mocks.listFirmwares,
    downloadFirmware: mocks.downloadFirmware,
    uploadFirmware: mocks.uploadFirmware,
    removeFirmware: mocks.removeFirmware
  }
}))

vi.mock('@/api/product', () => ({
  productApi: { getList: mocks.getProducts }
}))

import FirmwareList from '../FirmwareList.vue'

/* ---------- Element Plus 最小替身 ---------- */
const ROW_KEY = Symbol('table-row')

const RowProvider = {
  name: 'RowProvider',
  props: ['row'],
  setup(props, { slots }) {
    provide(ROW_KEY, computed(() => props.row))
    return () => slots.default?.()
  }
}

const ElTableStub = {
  name: 'ElTable',
  props: ['data'],
  setup(props, { slots }) {
    return () => {
      if (!props.data || props.data.length === 0) {
        return h('div', { class: 'stub-table' }, slots.empty ? [slots.empty()] : [])
      }
      return h(
        'div',
        { class: 'stub-table' },
        props.data.map((row, index) => h(RowProvider, { row, key: index }, { default: () => slots.default?.() }))
      )
    }
  }
}

const ElTableColumnStub = {
  name: 'ElTableColumn',
  props: ['label', 'prop'],
  setup(props, { slots }) {
    const row = inject(ROW_KEY, null)
    return () => {
      if (slots.default) return h('div', { class: 'stub-cell' }, slots.default({ row: row?.value ?? null }))
      return h('div', { class: 'stub-cell' }, props.prop ? String(row?.value?.[props.prop] ?? '') : '')
    }
  }
}

const ElButtonStub = {
  name: 'ElButton',
  props: ['disabled', 'loading'],
  emits: ['click'],
  setup(props, { slots, emit }) {
    return () =>
      h('button', { class: 'stub-btn', disabled: props.disabled, onClick: () => emit('click') }, slots.default?.())
  }
}

/** v-model 驱动：测试通过 `$emit('update:modelValue', v)` 设置选中值。 */
const ElSelectStub = {
  name: 'ElSelect',
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  setup(_, { slots }) {
    return () => h('div', { class: 'stub-select' }, slots.default?.())
  }
}

/** 原生 input 承载 v-model，可用 setValue 直接输入。 */
const ElInputStub = {
  name: 'ElInput',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        class: 'stub-input',
        value: props.modelValue,
        onInput: (event) => emit('update:modelValue', event.target.value)
      })
  }
}

const ElDialogStub = {
  name: 'ElDialog',
  props: ['modelValue', 'title'],
  emits: ['update:modelValue'],
  setup(props, { slots }) {
    return () =>
      props.modelValue
        ? h('div', { class: 'stub-dialog' }, [
            h('div', { class: 'stub-dialog-title' }, props.title),
            slots.default?.(),
            slots.footer?.()
          ])
        : null
  }
}

const passthrough = (name) => ({
  name,
  setup(_, { slots }) {
    return () => h('div', { class: `stub-${name}` }, slots.default?.())
  }
})

const EmptyStateStub = {
  name: 'EmptyState',
  props: ['description', 'imageSize'],
  template: '<div class="stub-empty">{{ description }}</div>'
}

const PageHeaderStub = {
  name: 'PageHeader',
  props: ['title', 'desc'],
  template:
    '<div class="stub-page-header"><span class="stub-page-header__title">{{ title }}</span>' +
    '<span class="stub-page-header__desc">{{ desc }}</span>' +
    '<slot name="title" /><slot name="actions" /></div>'
}

const STUBS = {
  ElTable: ElTableStub,
  ElTableColumn: ElTableColumnStub,
  ElButton: ElButtonStub,
  ElSelect: ElSelectStub,
  ElOption: passthrough('ElOption'),
  ElInput: ElInputStub,
  ElDialog: ElDialogStub,
  ElForm: passthrough('ElForm'),
  ElFormItem: passthrough('ElFormItem'),
  EmptyState: EmptyStateStub,
  PageHeader: PageHeaderStub,
  SvgIcon: passthrough('SvgIcon')
}

const FIRMWARE = {
  id: 11,
  productId: 3,
  productName: '温湿度传感器',
  version: '1.0.0',
  fileName: 'fw-1.0.0.bin',
  fileSize: 2097152,
  md5: 'abc123',
  downloadUrl: '/api/ota/firmwares/11/download',
  description: '修复上报异常',
  createdAt: '2026-10-01T08:00:00.000'
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

describe('views/workbench/ota/FirmwareList', () => {
  let queryClient
  let wrapper

  function mountPage() {
    wrapper = mount(FirmwareList, {
      global: {
        plugins: [[VueQueryPlugin, { queryClient }]],
        stubs: STUBS,
        directives: { loading: {} }
      }
    })
    return wrapper
  }

  function buttonByText(text) {
    return wrapper.findAll('button').find((button) => button.text().includes(text))
  }

  /** 对话框底部按钮：避免与页头同名按钮（如「上传固件」）混淆。 */
  function dialogButton(text) {
    return wrapper
      .find('.stub-dialog')
      .findAll('button')
      .find((button) => button.text().trim() === text)
  }

  async function openDialog() {
    await buttonByText('上传固件').trigger('click')
    await flushPromises()
  }

  /** 选中对话框内的产品：索引 0 为筛选下拉，索引 1 为对话框下拉。 */
  function chooseDialogProduct(productId) {
    wrapper.findAllComponents({ name: 'ElSelect' })[1].vm.$emit('update:modelValue', productId)
  }

  async function attachFile(name = 'fw.bin') {
    const fileInput = wrapper.find('input[type="file"]')
    const file = new File(['firmware'], name, { type: 'application/octet-stream' })
    Object.defineProperty(fileInput.element, 'files', { value: [file], configurable: true })
    await fileInput.trigger('change')
    return file
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: Infinity, gcTime: Infinity } }
    })
    mocks.message.success.mockClear()
    mocks.message.warning.mockClear()
    mocks.confirm.mockReset().mockResolvedValue(undefined)
    mocks.listFirmwares.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: [FIRMWARE] })
    mocks.getProducts.mockReset().mockResolvedValue({
      code: 200,
      message: 'ok',
      data: [{ id: 3, productName: '温湿度传感器' }]
    })
    mocks.uploadFirmware.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: null })
    mocks.removeFirmware.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: null })
    mocks.downloadFirmware.mockReset().mockResolvedValue({ code: 200, message: 'ok', data: new Blob(['x']) })
    URL.createObjectURL = vi.fn(() => 'blob:mock')
    URL.revokeObjectURL = vi.fn()
  })

  afterEach(() => {
    wrapper?.unmount()
    queryClient.clear()
    vi.clearAllMocks()
  })

  it('无固件时渲染空态提示', async () => {
    mocks.listFirmwares.mockResolvedValue({ code: 200, data: [] })
    mountPage()
    await settle()

    expect(wrapper.find('.stub-empty').exists()).toBe(true)
    expect(wrapper.find('.stub-empty').text()).toContain('暂无固件包')
  })

  it('渲染固件行并格式化文件大小', async () => {
    mountPage()
    await settle()

    const text = wrapper.text()
    expect(text).toContain('温湿度传感器')
    expect(text).toContain('1.0.0')
    expect(text).toContain('fw-1.0.0.bin')
    expect(text).toContain('2.00 MB')
    expect(text).toContain('abc123')
    expect(text).toContain('修复上报异常')
  })

  it('切换产品筛选后按 productId 重新查询', async () => {
    mountPage()
    await settle()
    expect(mocks.listFirmwares).toHaveBeenCalledWith(undefined)

    wrapper.findAllComponents({ name: 'ElSelect' })[0].vm.$emit('update:modelValue', 3)
    await settle()

    expect(mocks.listFirmwares).toHaveBeenLastCalledWith({ productId: 3 })
  })

  it('点击「上传固件」打开上传对话框', async () => {
    mountPage()
    await settle()
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)

    await openDialog()

    expect(wrapper.find('.stub-dialog-title').text()).toBe('上传固件')
  })

  it('填写完整后提交 multipart 表单并提示成功', async () => {
    mountPage()
    await settle()
    await openDialog()

    chooseDialogProduct(3)
    await wrapper.find('input.stub-input').setValue('1.0.0')
    const file = await attachFile()

    await dialogButton('上传').trigger('click')
    await settle()

    expect(mocks.uploadFirmware).toHaveBeenCalledTimes(1)
    const formData = mocks.uploadFirmware.mock.calls[0][0]
    expect(formData).toBeInstanceOf(FormData)
    expect(formData.get('productId')).toBe('3')
    expect(formData.get('version')).toBe('1.0.0')
    expect(formData.get('file')).toBe(file)
    expect(mocks.message.success).toHaveBeenCalledWith('上传成功')
    expect(wrapper.find('.stub-dialog').exists()).toBe(false)
  })

  it('版本号缺失时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    chooseDialogProduct(3)
    await attachFile()

    await dialogButton('上传').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('版本号仅支持字母、数字与 . _ -，长度 1~64')
    expect(mocks.uploadFirmware).not.toHaveBeenCalled()
  })

  it('未选择产品时前端拦截并提示', async () => {
    mountPage()
    await settle()
    await openDialog()

    await wrapper.find('input.stub-input').setValue('1.0.0')
    await attachFile()

    await dialogButton('上传').trigger('click')
    await settle()

    expect(mocks.message.warning).toHaveBeenCalledWith('请选择所属产品')
    expect(mocks.uploadFirmware).not.toHaveBeenCalled()
  })

  it('删除固件需二次确认后调用接口', async () => {
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.removeFirmware).toHaveBeenCalledWith(11)
    expect(mocks.message.success).toHaveBeenCalledWith('固件已删除')
  })

  it('取消删除时不调用接口', async () => {
    mocks.confirm.mockRejectedValueOnce(new Error('cancel'))
    mountPage()
    await settle()

    await buttonByText('删除').trigger('click')
    await settle()

    expect(mocks.removeFirmware).not.toHaveBeenCalled()
  })

  it('点击「下载」拉取附件流并触发浏览器下载', async () => {
    mountPage()
    await settle()

    await buttonByText('下载').trigger('click')
    await settle()

    expect(mocks.downloadFirmware).toHaveBeenCalledWith(11)
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock')
  })
})
