<template>
  <router-view v-slot="{ Component }">
    <transition name="slide-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
  <LoginModal v-if="showLogin" v-model="showLogin" />
  <RegisterModal v-if="showRegister" v-model="showRegister" />
  <PwaInstallPrompt />
  <PwaStatus />
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import LoginModal from './components/LoginModal.vue'
import RegisterModal from './components/RegisterModal.vue'
import PwaInstallPrompt from './components/PwaInstallPrompt.vue'
import PwaStatus from './components/PwaStatus.vue'

const route = useRoute()
const showLogin = computed(() => route.query.login === '1')
const showRegister = computed(() => route.query.register === '1')
</script>

<style scoped>
.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-fade-enter-from {
  opacity: 0;
  transform: translateY(20px);
}
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}
</style>
