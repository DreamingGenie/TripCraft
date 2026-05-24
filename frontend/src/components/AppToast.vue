<template>
  <div id="toast" :class="{ show: toast.visible }">⭐ {{ toast.message }}</div>
</template>

<script setup>
import { useToastStore } from '@/stores/toast'
const toast = useToastStore()
</script>
