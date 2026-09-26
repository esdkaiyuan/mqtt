<template>
  <div class="three-scene" ref="container">
    <div class="scene-overlay">
      <div class="angle-display">
        <div class="angle-item">
          <span class="angle-label">俯仰角 (Pitch)</span>
          <span class="angle-value">{{ pitch.toFixed(1) }}°</span>
        </div>
        <div class="angle-item">
          <span class="angle-label">横滚角 (Roll)</span>
          <span class="angle-value">{{ roll.toFixed(1) }}°</span>
        </div>
        <div class="angle-item">
          <span class="angle-label">偏航角 (Yaw)</span>
          <span class="angle-value">{{ yaw.toFixed(1) }}°</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as THREE from 'three'
import { useMotionStore } from '../stores/motion'

const container = ref(null)
const store = useMotionStore()

const pitch = ref(0)
const roll = ref(0)
const yaw = ref(0)

let scene, camera, renderer, cube
let axesHelper, gridHelper
let animationId = null

const initScene = () => {
  if (!container.value) return

  const width = container.value.clientWidth
  const height = container.value.clientHeight

  // Scene
  scene = new THREE.Scene()
  scene.background = new THREE.Color(0xf5f5f5)

  // Camera
  camera = new THREE.PerspectiveCamera(75, width / height, 0.1, 1000)
  camera.position.set(3, 2, 3)
  camera.lookAt(0, 0, 0)

  // Renderer
  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setSize(width, height)
  renderer.setPixelRatio(window.devicePixelRatio)
  container.value.appendChild(renderer.domElement)

  // Lights
  const ambientLight = new THREE.AmbientLight(0xffffff, 0.6)
  scene.add(ambientLight)

  const directionalLight = new THREE.DirectionalLight(0xffffff, 0.8)
  directionalLight.position.set(5, 5, 5)
  scene.add(directionalLight)

  // Axes Helper
  axesHelper = new THREE.AxesHelper(5)
  scene.add(axesHelper)

  // Grid
  gridHelper = new THREE.GridHelper(10, 10, 0xcccccc, 0xeeeeee)
  scene.add(gridHelper)

  // Cube
  const geometry = new THREE.BoxGeometry(1.5, 0.5, 1)
  const materials = [
    new THREE.MeshPhongMaterial({ color: 0x3b82f6 }), // Right - Blue
    new THREE.MeshPhongMaterial({ color: 0x22c55e }), // Left - Green
    new THREE.MeshPhongMaterial({ color: 0xef4444 }), // Top - Red
    new THREE.MeshPhongMaterial({ color: 0xf59e0b }), // Bottom - Yellow
    new THREE.MeshPhongMaterial({ color: 0x8b5cf6 }), // Front - Purple
    new THREE.MeshPhongMaterial({ color: 0xec4899 })  // Back - Pink
  ]
  cube = new THREE.Mesh(geometry, materials)
  scene.add(cube)

  // Add labels
  addAxisLabels()
}

const addAxisLabels = () => {
  const labelPositions = [
    { pos: [5.5, 0, 0], text: 'X', color: '#ef4444' },
    { pos: [0, 5.5, 0], text: 'Y', color: '#22c55e' },
    { pos: [0, 0, 5.5], text: 'Z', color: '#3b82f6' }
  ]

  // Using sprite-based labels
  labelPositions.forEach(({ pos, text, color }) => {
    const canvas = document.createElement('canvas')
    canvas.width = 64
    canvas.height = 64
    const ctx = canvas.getContext('2d')
    ctx.font = 'Bold 48px Arial'
    ctx.fillStyle = color
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(text, 32, 32)

    const texture = new THREE.CanvasTexture(canvas)
    const spriteMaterial = new THREE.SpriteMaterial({ map: texture })
    const sprite = new THREE.Sprite(spriteMaterial)
    sprite.position.set(...pos)
    sprite.scale.set(0.8, 0.8, 1)
    scene.add(sprite)
  })
}

const animate = () => {
  animationId = requestAnimationFrame(animate)

  if (cube && store.latestData) {
    const { ax, ay, az, gx, gy, gz } = store.latestData

    // Convert gyro data to rotation (simplified integration)
    const dt = 0.02 // 20ms interval
    roll.value += gx * dt
    pitch.value += gy * dt
    yaw.value += gz * dt

    // Apply rotation with damping
    const targetRoll = (roll.value * Math.PI) / 180
    const targetPitch = (pitch.value * Math.PI) / 180
    const targetYaw = (yaw.value * Math.PI) / 180

    cube.rotation.x += (targetRoll - cube.rotation.x) * 0.1
    cube.rotation.y += (targetPitch - cube.rotation.y) * 0.1
    cube.rotation.z += (targetYaw - cube.rotation.z) * 0.1
  }

  if (renderer && scene && camera) {
    renderer.render(scene, camera)
  }
}

const handleResize = () => {
  if (!container.value || !camera || !renderer) return

  const width = container.value.clientWidth
  const height = container.value.clientHeight

  camera.aspect = width / height
  camera.updateProjectionMatrix()
  renderer.setSize(width, height)
}

onMounted(() => {
  initScene()
  animate()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (animationId) {
    cancelAnimationFrame(animationId)
  }
  if (renderer) {
    renderer.dispose()
  }
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.three-scene {
  width: 100%;
  height: 100%;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}

.scene-overlay {
  position: absolute;
  top: 16px;
  right: 16px;
  z-index: 10;
  pointer-events: none;
}

.angle-display {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.angle-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
}

.angle-label {
  font-size: 12px;
  color: #666;
}

.angle-value {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
  font-family: 'Monaco', 'Consolas', monospace;
}
</style>
