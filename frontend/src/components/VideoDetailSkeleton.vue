<template>
  <div class="video-detail-skeleton" style="max-width: 960px; margin: 0 auto; padding-bottom: 40px;">
    <!-- 视频播放器骨架 -->
    <div class="player-skeleton" style="position: relative; background: #e5e7eb; border-radius: 8px; overflow: hidden; aspect-ratio: 16 / 9; margin-bottom: 20px;">
      <div class="skeleton-loading"></div>
      <div style="position: absolute; right: 14px; bottom: 14px; padding: 6px 12px; border-radius: 14px; background: #e5e7eb;"></div>
    </div>

    <!-- 内容区域骨架 -->
    <div style="padding: 20px; background: #fff; border-radius: 8px;">
      <!-- 标题骨架 -->
      <div class="skeleton-title" style="font-size: 20px; font-weight: 600; margin-bottom: 12px; height: 28px; border-radius: 4px;"></div>

      <!-- 统计信息骨架 -->
      <div style="display: flex; gap: 24px; margin-bottom: 16px;">
        <div class="skeleton-stat" style="font-size: 14px; height: 18px; border-radius: 4px; min-width: 80px;"></div>
        <div class="skeleton-stat" style="font-size: 14px; height: 18px; border-radius: 4px; min-width: 80px;"></div>
        <div class="skeleton-stat" style="font-size: 14px; height: 18px; border-radius: 4px; min-width: 80px;"></div>
      </div>

      <!-- 作者信息骨架 -->
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 16px;">
        <div class="skeleton-avatar" style="width: 40px; height: 40px; border-radius: 50%;"></div>
        <div class="skeleton-author" style="font-size: 15px; height: 20px; border-radius: 4px; min-width: 100px;"></div>
      </div>

      <!-- 描述骨架 -->
      <div class="skeleton-desc" style="font-size: 14px; line-height: 1.6; margin-bottom: 24px;">
        <div class="skeleton-line" style="height: 16px; border-radius: 4px; margin-bottom: 8px;"></div>
        <div class="skeleton-line" style="height: 16px; border-radius: 4px; margin-bottom: 8px; width: 80%;"></div>
        <div class="skeleton-line" style="height: 16px; border-radius: 4px; width: 60%;"></div>
      </div>

      <!-- 弹幕输入区域骨架 -->
      <div style="display: flex; gap: 12px; margin-bottom: 12px;">
        <div class="skeleton-input" style="flex: 1; padding: 10px 16px; border-radius: 8px; height: 40px;"></div>
        <div class="skeleton-button" style="padding: 10px 20px; border-radius: 8px; min-width: 80px;"></div>
        <div class="skeleton-button" style="padding: 10px 20px; border-radius: 8px; min-width: 80px;"></div>
      </div>

      <!-- 弹幕状态骨架 -->
      <div class="skeleton-status" style="margin-bottom: 24px; font-size: 13px; height: 16px; border-radius: 4px; min-width: 120px;"></div>

      <!-- 评论区骨架 -->
      <div>
        <div class="skeleton-comment-title" style="font-size: 16px; margin-bottom: 16px; height: 20px; border-radius: 4px; min-width: 100px;"></div>
        
        <!-- 评论输入骨架 -->
        <div style="display: flex; gap: 12px; margin-bottom: 20px;">
          <div class="skeleton-avatar-sm" style="width: 36px; height: 36px; border-radius: 50%;"></div>
          <div style="flex: 1;">
            <div class="skeleton-textarea" style="width: 100%; padding: 12px; border-radius: 8px; height: 80px; margin-bottom: 8px;"></div>
            <div class="skeleton-comment-button" style="padding: 8px 20px; border-radius: 6px; min-width: 80px; display: inline-block;"></div>
          </div>
        </div>

        <!-- 评论列表骨架 -->
        <div v-for="i in 3" :key="i" style="display: flex; gap: 12px; margin-bottom: 20px;">
          <div class="skeleton-avatar-sm" style="width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;"></div>
          <div style="flex: 1;">
            <div class="skeleton-username" style="font-size: 14px; height: 16px; border-radius: 4px; min-width: 80px; margin-bottom: 4px;"></div>
            <div class="skeleton-comment-content" style="font-size: 14px; line-height: 1.6; height: 40px; border-radius: 4px; margin-bottom: 4px;"></div>
            <div class="skeleton-comment-time" style="font-size: 12px; height: 14px; border-radius: 4px; min-width: 60px;"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 骨架屏动画 */
.skeleton-loading,
.skeleton-title,
.skeleton-stat,
.skeleton-avatar,
.skeleton-author,
.skeleton-desc .skeleton-line,
.skeleton-input,
.skeleton-button,
.skeleton-status,
.skeleton-comment-title,
.skeleton-avatar-sm,
.skeleton-textarea,
.skeleton-comment-button,
.skeleton-username,
.skeleton-comment-content,
.skeleton-comment-time {
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

/* 播放器骨架特殊样式 */
.player-skeleton {
  position: relative;
}

.skeleton-loading {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
</style>