<template>
  <div class="skeleton-container" style="padding: 32px 40px; max-width: 2000px; margin: 0 auto;">
    <!-- 分类 Tab 骨架 -->
    <div style="display: flex; align-items: center; gap: 28px; margin-bottom: 32px; overflow-x: auto; padding-bottom: 12px;">
      <div v-for="i in 3" :key="i" class="skeleton-tab" style="position: relative; padding-bottom: 8px; font-size: 16px; font-weight: 800;">
        <div class="skeleton-line" style="width: 60px; height: 20px; border-radius: 4px;"></div>
        <div v-if="i === 1" class="skeleton-tab-active" style="position: absolute; bottom: 0; left: 0; right: 0; height: 2.5px; border-radius: 1px;"></div>
      </div>
    </div>

    <!-- Bento Grid 首屏骨架 -->
    <div style="display: grid; grid-template-columns: repeat(12, 1fr); gap: 24px; margin-bottom: 48px;">
      <!-- 左侧：主推英雄视频 -->
      <div class="skeleton-hero" style="grid-column: span 8; aspect-ratio: 16/9; border-radius: 48px; overflow: hidden; position: relative; border: 1px solid #f1f5f9;">
        <div class="skeleton-hero-bg"></div>
        <div style="position: absolute; top: 20px; left: 20px;">
          <div class="skeleton-tag" style="padding: 8px 14px; border-radius: 24px; font-size: 11px;"></div>
        </div>
        <div style="position: absolute; bottom: 0; left: 0; right: 0; padding: 32px;">
          <div class="skeleton-title" style="font-size: 28px; font-weight: 900; margin-bottom: 12px; max-width: 520px;"></div>
          <div style="display: flex; align-items: center; gap: 16px;">
            <div class="skeleton-author" style="font-size: 13px; font-weight: 600;"></div>
            <div class="skeleton-playcount" style="font-size: 13px; font-weight: 600;"></div>
          </div>
        </div>
        <div style="position: absolute; bottom: 24px; right: 24px; width: 60px; height: 60px; border-radius: 50%; display: flex; align-items: center; justify-content: center;">
          <div class="skeleton-play-btn"></div>
        </div>
      </div>

      <!-- 右侧：AI 推荐面板 -->
      <div style="grid-column: span 4; border-radius: 48px; border: 1px solid #f1f5f9; padding: 24px; position: relative; overflow: hidden; display: flex; flex-direction: column; min-height: 0;">
        <div style="position: absolute; right: -50px; top: -50px; width: 140px; height: 140px; background: #c7d2fe; filter: blur(48px); opacity: 0.4;"></div>
        <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 20px; position: relative; z-index: 1;">
          <div style="width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center;">
            <div class="skeleton-ai-icon"></div>
          </div>
          <div class="skeleton-ai-title" style="font-weight: 900; font-size: 16px;"></div>
        </div>
        <div style="flex: 1; display: grid; grid-template-rows: 1.35fr 1fr 1fr; gap: 12px; position: relative; z-index: 1; min-height: 0;">
          <div v-for="i in 3" :key="i" style="position: relative; border-radius: 18px; overflow: hidden; border: 1px solid rgba(226, 232, 240, 0.95); min-height: 0; box-shadow: 0 3px 14px rgba(15, 23, 42, 0.05);">
            <div class="skeleton-cover"></div>
            <div class="skeleton-overlay"></div>
            <div class="skeleton-ai-tag" style="position: absolute; top: 10px; left: 10px; font-size: 10px; font-weight: 900; padding: 4px 8px; border-radius: 6px; display: inline-block; width: fit-content; z-index: 1;"></div>
            <div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 12px 12px 10px; z-index: 1;">
              <div class="skeleton-ai-item-title" :style="i === 1 ? 'height: 38px; width: 100%;' : 'height: 32px; width: 100%;'"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 正在流行区块骨架 -->
    <div style="margin-bottom: 32px;">
      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 20px; font-size: 18px; font-weight: 900;">
        <span>🔥</span>
        <div class="skeleton-section-title"></div>
      </div>
      <div style="display: grid; grid-template-columns: repeat(12, 1fr); gap: 24px;">
        <div v-for="i in 8" :key="i" style="grid-column: span 3; display: flex; flex-direction: column; gap: 12px;">
          <!-- 封面 -->
          <div style="position: relative; aspect-ratio: 16/9; border-radius: 20px; overflow: hidden; background: #e5e7eb;">
            <div class="skeleton-cover"></div>
            <div style="position: absolute; bottom: 8px; right: 8px; padding: 4px 8px; border-radius: 6px;">
              <div class="skeleton-duration"></div>
            </div>
          </div>
          <!-- 信息 -->
          <div style="display: flex; gap: 12px;">
            <div class="skeleton-avatar" style="width: 32px; height: 32px; border-radius: 50%;"></div>
            <div style="flex: 1; min-width: 0;">
              <div class="skeleton-video-title" style="font-weight: 700; font-size: 13px; line-height: 1.3; height: 32px;"></div>
              <div class="skeleton-video-meta" style="font-size: 12px; margin-top: 4px;"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 加载更多按钮骨架 -->
    <div style="display: flex; justify-content: center; padding: 48px 0;">
      <div class="skeleton-load-more" style="padding: 14px 48px; border-radius: 20px; font-weight: 900; font-size: 14px;"></div>
    </div>
  </div>
</template>

<style scoped>
/* 骨架屏动画 */
.skeleton-line,
.skeleton-tab-active,
.skeleton-hero-bg,
.skeleton-tag,
.skeleton-title,
.skeleton-author,
.skeleton-playcount,
.skeleton-play-btn,
.skeleton-ai-icon,
.skeleton-ai-title,
.skeleton-ai-tag,
.skeleton-ai-item-title,
.skeleton-section-title,
.skeleton-cover,
.skeleton-overlay,
.skeleton-duration,
.skeleton-avatar,
.skeleton-video-title,
.skeleton-video-meta,
.skeleton-load-more {
  background: linear-gradient(90deg, #f1f5f9 25%, #e2e8f0 50%, #f1f5f9 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 具体元素样式 */
.skeleton-tab {
  min-width: 80px;
}

.skeleton-hero-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.skeleton-tag {
  width: 80px;
  height: 16px;
}

.skeleton-title {
  height: 60px;
  border-radius: 4px;
}

.skeleton-author {
  width: 100px;
  height: 16px;
  border-radius: 4px;
}

.skeleton-playcount {
  width: 80px;
  height: 16px;
  border-radius: 4px;
}

.skeleton-play-btn {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: white;
}

.skeleton-ai-icon {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: white;
}

.skeleton-ai-title {
  width: 100px;
  height: 20px;
  border-radius: 4px;
}

.skeleton-ai-tag {
  width: 40px;
  height: 14px;
  border-radius: 4px;
}

.skeleton-ai-item-title {
  border-radius: 4px;
}

.skeleton-section-title {
  width: 80px;
  height: 24px;
  border-radius: 4px;
}

.skeleton-cover {
  width: 100%;
  height: 100%;
}

.skeleton-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(to top, rgba(15, 23, 42, 0.88) 0%, rgba(15, 23, 42, 0.24) 48%, rgba(15, 23, 42, 0.05) 100%);
  animation: none;
}

.skeleton-duration {
  width: 40px;
  height: 14px;
  border-radius: 4px;
  background: rgba(31, 41, 55, 0.8);
  animation: none;
}

.skeleton-avatar {
  background: linear-gradient(135deg, #a78bfa 0%, #f472b6 100%);
  animation: none;
}

.skeleton-video-title {
  border-radius: 4px;
}

.skeleton-video-meta {
  height: 14px;
  border-radius: 4px;
}

.skeleton-load-more {
  height: 40px;
  min-width: 120px;
}
</style>