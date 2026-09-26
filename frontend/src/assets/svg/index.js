// 导入所有SVG图标
const icons = import.meta.glob('./icons/*.svg', { query: '?raw', import: 'default' })

// 注册为Vue组件（在App.vue中使用）
export function registerIcons(app) {
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
