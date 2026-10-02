import { describe, it, expect } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { EP_DIR_ALIAS } from '../../element-plus-alias.js'

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const componentsDir = path.join(projectRoot, 'node_modules/element-plus/es/components')

/** 收集 src 下所有 Element Plus 组件标签名（kebab 形式，去重）。 */
function collectUsedComponents() {
  const names = new Set()
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full)
      } else if (/\.(vue|js)$/.test(entry.name)) {
        const source = fs.readFileSync(full, 'utf8')
        for (const match of source.matchAll(/<el-([a-z0-9-]+)/g)) {
          names.add(match[1])
        }
      }
    }
  }
  walk(path.join(projectRoot, 'src'))
  return [...names].sort()
}

const resolves = (name) => fs.existsSync(path.join(componentsDir, name, 'index.mjs'))

describe('vite.config Element Plus 按需解析', () => {
  it('src 中用到的每个 <el-*> 组件都能解析到 index.mjs 入口', () => {
    const unresolved = collectUsedComponents().filter((name) => {
      if (resolves(name)) return false
      const aliased = EP_DIR_ALIAS[name]
      return !(aliased && resolves(aliased))
    })

    expect(unresolved).toEqual([])
  })

  it('别名表中的目标目录必须存在 index.mjs', () => {
    const broken = Object.entries(EP_DIR_ALIAS)
      .filter(([, target]) => !resolves(target))
      .map(([source, target]) => `${source} -> ${target}`)

    expect(broken).toEqual([])
  })
})