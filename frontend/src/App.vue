<template>
  <router-view v-slot="{ Component }">
    <transition name="fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <LoginModal v-if="showLogin" v-model="showLogin" />
  <RegisterModal v-if="showRegister" v-model="showRegister" />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import LoginModal from './components/LoginModal.vue'
import RegisterModal from './components/RegisterModal.vue'

const route = useRoute()
const showLogin = computed(() => route.query.login === '1')
const showRegister = computed(() => route.query.register === '1')
</script>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
