#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const templateDir = path.join(repoRoot, 'qt-hmi-template')

function usage() {
  console.log(`用法：
  node scripts/create-hmi.mjs --out <目录> --device <deviceKey> [选项]

必填：
  --out <目录>        输出目录，例如 ../line1-hmi
  --device <key>      设备唯一标识

可选：
  --title <标题>      窗口标题，默认 "<deviceKey> 上位机"
  --base-url <URL>    Open API 地址，默认 http://localhost/api/external/v1
  --poll-ms <毫秒>    轮询周期，默认 2000，范围 500~60000
  --model <文件>      自定义 model.json；不传则复制 model.example.json
  --tsl <文件>        从产品页导出的物模型 TSL 自动生成 model.json
  --help              显示帮助

示例：
  node scripts/create-hmi.mjs --out ../line1-hmi --device sensor-temp-001 \\
    --title "1号线温度监控" --base-url https://demo.example.com/api/external/v1 \\
    --tsl ./product-tsl.json
`)
}

function readArg(name) {
  const inline = process.argv.find((arg) => arg.startsWith(`${name}=`))
  if (inline) {
    const value = inline.slice(name.length + 1)
    if (!value) {
      console.error(`${name} 缺少值。`)
      process.exit(1)
    }
    return value
  }
  const index = process.argv.indexOf(name)
  if (index < 0) {
    return undefined
  }
  const value = process.argv[index + 1]
  if (!value || value.startsWith('--')) {
    console.error(`${name} 缺少值。`)
    process.exit(1)
  }
  return value
}

if (process.argv.includes('--help') || process.argv.includes('-h')) {
  usage()
  process.exit(0)
}

const outArg = readArg('--out')
const deviceKey = readArg('--device')
if (!outArg || !deviceKey) {
  usage()
  process.exit(1)
}

const outDir = path.resolve(process.cwd(), outArg)
const baseUrl = readArg('--base-url') || 'http://localhost/api/external/v1'
const title = readArg('--title') || `${deviceKey} 上位机`
const pollMs = Number(readArg('--poll-ms') || 2000)
const modelArg = readArg('--model')
const tslArg = readArg('--tsl')

if (modelArg && tslArg) {
  console.error('--model 与 --tsl 只能选择一种。')
  process.exit(1)
}

if (outDir === templateDir || outDir.startsWith(templateDir + path.sep)) {
  console.error('输出目录不能位于模板目录内。')
  process.exit(1)
}
if (fs.existsSync(outDir)) {
  console.error(`目标目录已存在，请换一个目录：${outDir}`)
  process.exit(1)
}
if (!Number.isFinite(pollMs) || pollMs < 500 || pollMs > 60000) {
  console.error('--poll-ms 需在 500~60000 之间。')
  process.exit(1)
}

function hmiType(dataType) {
  const type = dataType?.type
  if (type === 'int' || type === 'float' || type === 'double') {
    return 'number'
  }
  if (type === 'bool') {
    return 'bool'
  }
  return 'string'
}

function modelFromTsl(tsl) {
  if (!tsl || typeof tsl !== 'object' || Array.isArray(tsl)) {
    throw new Error('TSL 根节点必须是对象')
  }
  if (!Array.isArray(tsl.properties)) {
    throw new Error('TSL.properties 必须是数组')
  }

  const properties = tsl.properties.map((property, index) => {
    if (!property || typeof property.identifier !== 'string' || !property.identifier.trim()) {
      throw new Error(`TSL.properties[${index}] 缺少 identifier`)
    }
    return {
      identifier: property.identifier.trim(),
      label: property.name || property.identifier,
      type: hmiType(property.dataType),
      unit: property.dataType?.unit || '',
      writable: property.accessMode === 'rw'
    }
  })

  const services = []
  const skippedServices = []
  if (Array.isArray(tsl.services)) {
    tsl.services.forEach((service, index) => {
      if (!service || typeof service.identifier !== 'string' || !service.identifier.trim()) {
        throw new Error(`TSL.services[${index}] 缺少 identifier`)
      }
      const requiredInput = Array.isArray(service.inputData)
        && service.inputData.some((item) => item?.required === true)
      if (requiredInput) {
        skippedServices.push(service.identifier.trim())
        return
      }
      services.push({
        identifier: service.identifier.trim(),
        label: service.name || service.identifier
      })
    })
  }

  return {
    model: { properties, services },
    skippedServices
  }
}

let modelSource = path.join(templateDir, 'model.example.json')
let modelData = null
let skippedServices = []
if (tslArg) {
  const tslPath = path.resolve(process.cwd(), tslArg)
  if (!fs.existsSync(tslPath)) {
    console.error(`TSL 文件不存在：${tslPath}`)
    process.exit(1)
  }
  try {
    const converted = modelFromTsl(JSON.parse(fs.readFileSync(tslPath, 'utf8')))
    modelData = converted.model
    skippedServices = converted.skippedServices
  } catch (error) {
    console.error(`TSL 解析失败：${error.message}`)
    process.exit(1)
  }
} else if (modelArg) {
  modelSource = path.resolve(process.cwd(), modelArg)
  if (!fs.existsSync(modelSource)) {
    console.error(`模型文件不存在：${modelSource}`)
    process.exit(1)
  }
}

if (!modelData) {
  try {
    modelData = JSON.parse(fs.readFileSync(modelSource, 'utf8'))
    if (!Array.isArray(modelData.properties)) {
      throw new Error('model.properties 必须是数组')
    }
  } catch (error) {
    console.error(`模型文件不合法：${error.message}`)
    process.exit(1)
  }
}

fs.cpSync(templateDir, outDir, {
  recursive: true,
  filter: (source) => {
    const relative = path.relative(templateDir, source)
    return !relative.split(path.sep).includes('.git') && !relative.split(path.sep).includes('build')
  }
})

fs.writeFileSync(
  path.join(outDir, 'model.json'),
  `${JSON.stringify(modelData, null, 2)}\n`,
  'utf8'
)
fs.writeFileSync(
  path.join(outDir, 'config.json'),
  `${JSON.stringify(
    {
      title,
      baseUrl,
      deviceKey,
      pollMs,
      model: 'model.json'
    },
    null,
    2
  )}\n`,
  'utf8'
)

console.log(`已创建 Qt 上位机工程：${outDir}`)
if (skippedServices.length > 0) {
  console.log(
    `注意：已跳过 ${skippedServices.length} 个带必填入参的服务：${skippedServices.join(', ')}`
  )
  console.log('      模板的服务按钮只发送空 params，需按业务自行补参数控件。')
}
console.log('')
console.log('下一步：')
console.log(
  tslArg
    ? '  1. 已按 TSL 生成 model.json；检查不确定的类型映射（enum/struct/array 按 string 处理）'
    : '  1. 按设备物模型编辑 model.json 中的属性与服务'
)
console.log('  2. 设置 API Key（不要写入源码）：export PLATFORM_API_KEY=...')
console.log('  3. 用 Qt Creator 打开 qt-hmi.pro，或执行：')
console.log('     cmake -S . -B build && cmake --build build -j')
console.log('  4. 运行程序；config.json 不在源码仓库中提交')
