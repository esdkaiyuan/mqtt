/**
 * Element Plus 按需解析的「子组件目录归并表」。
 *
 * 少数 <el-*> 组件并非独立目录，而是父组件的子导出，这里给出目录归并表。
 * 例如 ElOption 由 select 目录导出，ElDropdownItem/ElDropdownMenu 由 dropdown 目录导出。
 * 子组件目录（如 form-item/）下只有 style，没有 index.mjs，必须回指父组件目录，
 * 否则组件解析失败会退化成未注册的自定义元素，导致表单/表格/标签页结构塌陷
 * （且 `vite build` 不会报错，只在运行时页面空白）。
 *
 * 由 vite.config.js 与 src/__tests__/elementPlusOnDemand.spec.js 共用，
 * 后者扫描 src 下实际用到的 <el-*> 组件并断言均可解析，防止再次漏配。
 */
export const EP_DIR_ALIAS = {
  option: 'select',
  'option-group': 'select',
  'menu-item': 'menu',
  'menu-item-group': 'menu',
  'sub-menu': 'menu',
  'dropdown-item': 'dropdown',
  'dropdown-menu': 'dropdown',
  'form-item': 'form',
  'table-column': 'table',
  'tab-pane': 'tabs',
  'breadcrumb-item': 'breadcrumb',
  'radio-group': 'radio',
  'radio-button': 'radio'
}