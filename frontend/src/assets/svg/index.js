// 导入所有SVG图标
const icons = import.meta.glob('./icons/*.svg', { query: '?raw', import: 'default' })

// 把 SVG 图标注入为页面上隐藏的 <symbol> 雪碧图，供 Icon.vue 通过 <use> 引用
export function registerIcons() {
  const svgSprite = document.createElement('div')
  svgSprite.style.display = 'none'
  svgSprite.id = 'svg-sprite'
  document.body.appendChild(svgSprite)

  Object.keys(icons).forEach(path => {
    const name = path.match(/\/([^/]+)\.svg$/)?.[1]
    if (name) {
      icons[path]().then(svgContent => {
        const symbol = document.createElementNS('http://www.w3.org/2000/svg', 'symbol')
        symbol.id = `icon-${name}`
        symbol.setAttribute('viewBox', '0 0 24 24')
        symbol.innerHTML = svgContent.trim().replace(/<svg[^>]*>/, '').replace(/<\/svg>/, '')
        svgSprite.appendChild(symbol)
      })
    }
  })
}
