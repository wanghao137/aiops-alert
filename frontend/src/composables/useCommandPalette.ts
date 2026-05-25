import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useCommandPaletteStore = defineStore('commandPalette', () => {
  const visible = ref(false)
  function open() { visible.value = true }
  function close() { visible.value = false }
  function toggle() { visible.value = !visible.value }
  return { visible, open, close, toggle }
})
