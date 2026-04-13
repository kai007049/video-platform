<template>
  <div class="empty-state">
    <div class="empty-content">
      <div class="icon">{{ icon }}</div>
      <h3 class="title">{{ title }}</h3>
      <p class="description">{{ description }}</p>
      <div class="actions" v-if="actions.length > 0">
        <button 
          v-for="(action, index) in actions" 
          :key="index"
          class="action-btn"
          :class="action.type"
          @click="action.handler"
        >
          {{ action.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  icon: {
    type: String,
    default: '⚠️'
  },
  title: {
    type: String,
    default: '操作失败'
  },
  description: {
    type: String,
    default: '服务器似乎在开小差，请稍后再试'
  },
  actions: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  padding: 40px 20px;
}

.empty-content {
  text-align: center;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(12px);
  border-radius: 24px;
  padding: 48px 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.6);
  max-width: 480px;
  width: 100%;
}

.icon {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.8;
}

.title {
  font-size: 20px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 8px;
}

.description {
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 24px;
}

.actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.action-btn {
  padding: 10px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}

.action-btn.primary {
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.action-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.action-btn.secondary {
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
}

.action-btn.secondary:hover {
  background: #e2e8f0;
  transform: translateY(-2px);
}
</style>