import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const visible = ref(false)
  let timer = null

  function show(msg, duration = 3000) {
    message.value = msg
    visible.value = true
    clearTimeout(timer)
    timer = setTimeout(() => { visible.value = false }, duration)
  }

  return { message, visible, show }
})
