import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 平滑滚动到落地页指定锚点区块
 * @param {string} id 区块 DOM id
 */
export function scrollToSection(id) {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth' })
  }
}

/**
 * 监听页面滚动，用于导航栏在滚动后切换为白底样式
 */
export function useScrollIndicator() {
  const isScrolled = ref(false)

  function onScroll() {
    isScrolled.value = window.scrollY > 50
  }

  onMounted(() => {
    window.addEventListener('scroll', onScroll)
  })

  onUnmounted(() => {
    window.removeEventListener('scroll', onScroll)
  })

  return { isScrolled }
}
