import { ref, computed } from 'vue'

/**
 * 마이페이지 게시글 목록 공통 로직 (북마크 / 좋아요 / 내가 쓴 글)
 * @param {Function} apiFn - ({ page, size }) => Promise<{ items, total }>
 * @param {number} pageSize
 */
export function usePostList(apiFn, pageSize = 10) {
  const items   = ref([])
  const page    = ref(0)
  const total   = ref(0)
  const loading = ref(false)

  const totalPages = computed(() => Math.ceil(total.value / pageSize))

  async function loadPage(p = 0) {
    loading.value = true
    page.value = p
    try {
      const res = await apiFn({ page: p, size: pageSize })
      items.value = res.items
      total.value = res.total
    } catch {
      items.value = []
    } finally {
      loading.value = false
    }
  }

  return { items, page, total, loading, totalPages, loadPage }
}
