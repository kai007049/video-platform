<template>
  <div class="home" style="padding: 32px 40px; max-width: 2000px; margin: 0 auto;">
    <!-- 骨架屏 -->
    <SkeletonScreen v-if="loading && videoList.length === 0" />
    
    <!-- 内容区域 -->
    <template v-else>
      <!-- 简洁的分类 Tab （下划线设计） -->
      <div style="display: flex; align-items: center; gap: 28px; margin-bottom: 32px; animation: slideUpFade 0.6s cubic-bezier(0.16, 1, 0.3, 1); animation-delay: 0.1s; overflow-x: auto; padding-bottom: 12px;">
        <button
          v-for="tab in ['推荐', '最新', '热门']"
          :key="tab"
          @click="switchTab(tab === '推荐' ? 'recommend' : tab === '最新' ? 'latest' : 'hot')"
          style="position: relative; padding-bottom: 8px; font-size: 16px; font-weight: 800; border: none; background: none; cursor: pointer; transition: color 0.3s; color: #64748b; white-space: nowrap;"
          :style="activeTab === (tab === '推荐' ? 'recommend' : tab === '最新' ? 'latest' : 'hot') ? 'color: #1f2937;' : ''"
          @mouseenter="(e) => { if(activeTab !== (tab === '推荐' ? 'recommend' : tab === '最新' ? 'latest' : 'hot')) e.currentTarget.style.color = '#475569'; }"
          @mouseleave="(e) => { if(activeTab !== (tab === '推荐' ? 'recommend' : tab === '最新' ? 'latest' : 'hot')) e.currentTarget.style.color = '#64748b'; }">
          {{ tab }}
          <span v-if="activeTab === (tab === '推荐' ? 'recommend' : tab === '最新' ? 'latest' : 'hot')"
            style="position: absolute; bottom: 0; left: 0; right: 0; height: 2.5px; background: linear-gradient(90deg, #1f2937 0%, #374151 100%); border-radius: 1px;"></span>
        </button>

        <button
          v-if="activeTab === 'recommend'"
          @click="refreshRecommendBatch"
          :disabled="refreshingRecommend"
          class="refresh-batch-btn"
        >
          {{ refreshingRecommend ? '刷新中...' : '换一批' }}
        </button>
      </div>

      <!-- Bento Grid 首屏：主推视频 + AI 推荐区块 -->
      <!-- ========== Bento Grid 首屏布局（严格12列栅格） ========== -->
      <div style="display: grid; grid-template-columns: repeat(12, 1fr); gap: 24px; margin-bottom: 48px; align-items: stretch;">
        
        <!-- 左侧：主推英雄视频 (col-span-8) -->
        <div
          v-if="heroVideo"
          :key="heroVideo.id"
          ref="heroCardRef"
          class="bento-hero-card"
          style="grid-column: span 8; aspect-ratio: 16/9; border-radius: 48px; overflow: hidden; position: relative; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12); transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1); cursor: pointer; border: 1px solid #f1f5f9; background: linear-gradient(135deg, #7c3aed 0%, #2563eb 50%, #06b6d4 100%);"
          @mouseenter="(e) => e.currentTarget.style.boxShadow = '0 20px 48px rgba(0, 0, 0, 0.20)'"
          @mouseleave="(e) => e.currentTarget.style.boxShadow = '0 8px 32px rgba(0, 0, 0, 0.12)'"
          @click="goVideo(heroVideo.id)">
          
          <!-- 📸 主视频背景 -->
          <div style="position: absolute; inset: 0; width: 100%; height: 100%; overflow: hidden;">
            <img 
              :src="resolveCover(heroVideo)"
              :alt="heroVideo.title"
              @error="onCoverError"
              style="width: 100%; height: 100%; object-fit: cover; transition: transform 0.7s cubic-bezier(0.34, 1.56, 0.64, 1);"
              class="bento-hero-img" />
          </div>
          
          <!-- 🌑 深色遮罩 -->
          <div style="position: absolute; inset: 0; background: linear-gradient(to top, rgba(31, 41, 55, 0.95) 0%, rgba(31, 41, 55, 0.3) 50%, transparent 100%);"></div>
          
          <!-- 🏷️ 标签 -->
          <div style="position: absolute; top: 20px; left: 20px; z-index: 10; will-change: auto;">
            <span style="display: inline-block; background: rgba(31, 41, 55, 0.6); color: white; font-size: 11px; font-weight: 900; padding: 8px 14px; border-radius: 24px; letter-spacing: 0.5px; white-space: nowrap;">🔥 独家首发</span>
          </div>

          <!-- 📝 底部信息 -->
          <div style="position: absolute; bottom: 0; left: 0; right: 0; padding: 32px; z-index: 10;">
            <h2 style="font-size: 28px; font-weight: 900; color: white; margin-bottom: 12px; line-height: 1.2; max-width: 520px;">{{ heroVideo.title }}</h2>
            <div style="display: flex; align-items: center; gap: 16px; color: #cbd5e1; font-size: 13px; font-weight: 600;">
              <span>👤 {{ heroVideo.authorName || '用户' }}</span>
              <span>📈 {{ formatCount(heroVideo.playCount) }} 播放</span>
            </div>
          </div>

          <!-- ▶️ 播放按钮 -->
          <div style="position: absolute; bottom: 24px; right: 24px; width: 60px; height: 60px; border-radius: 50%; background: rgba(255, 255, 255, 0.95); display: flex; align-items: center; justify-content: center; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3); transition: all 0.3s; opacity: 0; transform: scale(0.8); z-index: 10;"
            @mouseenter="(e) => {
              e.currentTarget.style.opacity = '1';
              e.currentTarget.style.transform = 'scale(1)';
            }"
            @mouseleave="(e) => {
              e.currentTarget.style.opacity = '0';
              e.currentTarget.style.transform = 'scale(0.8)';
            }">
            <span style="font-size: 24px;">▶</span>
          </div>
        </div>

        <!-- 右侧：AI 推荐面板 (col-span-4) -->
        <div :style="aiPanelStyle">
          <div style="border-radius: 48px; background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%); border: 1px solid #f1f5f9; padding: 24px; position: relative; overflow: hidden; display: flex; flex-direction: column; box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04); min-height: 0; height: 100%;">
          <!-- 装饰 -->
          <div style="position: absolute; right: -50px; top: -50px; width: 140px; height: 140px; background: #c7d2fe; filter: blur(48px); opacity: 0.4;"></div>

          <!-- 标题栏 -->
          <div style="display: flex; align-items: center; gap: 10px; margin-bottom: 20px; position: relative; z-index: 1;">
            <div style="width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, #7c3aed 0%, #06b6d4 100%); display: flex; align-items: center; justify-content: center; color: white; font-size: 16px;">✨</div>
            <h3 style="font-weight: 900; font-size: 16px; color: #1f2937;">AI 为你甄选</h3>
          </div>

          <!-- 推荐卡片列表 -->
          <div style="flex: 1; display: grid; grid-template-rows: 1.35fr 1fr 1fr; gap: 12px; position: relative; z-index: 1; min-height: 0;">
            <div
              v-for="(item, idx) in aiPanelVideos"
              :key="item.id"
              style="position: relative; border-radius: 18px; overflow: hidden; cursor: pointer; border: 1px solid rgba(226, 232, 240, 0.95); box-shadow: 0 3px 14px rgba(15, 23, 42, 0.05); min-height: 0;"
              @mouseenter="(e) => {
                e.currentTarget.style.borderColor = '#dbe3f3';
                e.currentTarget.style.boxShadow = '0 10px 24px rgba(31, 41, 55, 0.12)';
                e.currentTarget.style.transform = 'translateY(-1px)';
                const img = e.currentTarget.querySelector('img');
                if (img) img.style.transform = 'scale(1.04)';
              }"
              @mouseleave="(e) => {
                e.currentTarget.style.borderColor = 'rgba(226, 232, 240, 0.95)';
                e.currentTarget.style.boxShadow = '0 3px 14px rgba(15, 23, 42, 0.05)';
                e.currentTarget.style.transform = 'translateY(0)';
                const img = e.currentTarget.querySelector('img');
                if (img) img.style.transform = 'scale(1)';
              }"
              @click="goVideo(item.id)">
              <img
                :src="resolveCover(item)"
                :alt="item.title"
                @error="onCoverError"
                style="width: 100%; height: 100%; object-fit: cover; transition: transform 0.35s ease;" />
              <div style="position: absolute; inset: 0; background: linear-gradient(to top, rgba(15, 23, 42, 0.88) 0%, rgba(15, 23, 42, 0.24) 48%, rgba(15, 23, 42, 0.05) 100%);"></div>
              <span style="position: absolute; top: 10px; left: 10px; font-size: 10px; font-weight: 900; padding: 4px 8px; border-radius: 6px; display: inline-block; backdrop-filter: blur(8px); box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12); z-index: 1;"
                :style="{
                  color: idx === 0 ? '#16a34a' : idx === 1 ? '#2563eb' : '#a16207',
                  backgroundColor: idx === 0 ? 'rgba(220, 252, 231, 0.94)' : idx === 1 ? 'rgba(219, 234, 254, 0.94)' : 'rgba(254, 215, 170, 0.94)'
                }">
                {{ idx === 0 ? '精选' : idx === 1 ? '热门' : '进阶' }}
              </span>
              <div style="position: absolute; left: 0; right: 0; bottom: 0; padding: 12px 12px 10px; z-index: 1;">
                <p :style="idx === 0
                    ? 'font-weight: 800; font-size: 14px; color: white; line-height: 1.32; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;'
                    : 'font-weight: 700; font-size: 12px; color: white; line-height: 1.34; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;'">
                  {{ item.title }}
                </p>
              </div>
            </div>
          </div>
          </div>
        </div>
      </div>

      <!-- ========== "正在流行" 区块（12列栅格系统） ========== -->
      <div style="margin-bottom: 32px;">
        <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 20px; font-size: 18px; font-weight: 900; color: #1f2937;">
          <span>🔥</span>
          <span>正在流行</span>
        </div>

        <!-- 12列网格：每4个卡片占12列 -->
        <div style="display: grid; grid-template-columns: repeat(12, 1fr); gap: 24px;">
          <div
            v-for="item in videoList.slice(4)"
            :key="item.id"
            :data-video-id="item.id"
            style="grid-column: span 3; display: flex; flex-direction: column; gap: 12px; cursor: pointer;"
            @click="goVideo(item.id)"
            ref="videoCards">
            
            <!-- 封面 -->
            <div style="position: relative; aspect-ratio: 16/9; border-radius: 20px; overflow: hidden; background: #e5e7eb; cursor: pointer; transition: all 0.3s; box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);"
              @mouseenter="(e) => {
                e.currentTarget.style.boxShadow = '0 12px 32px rgba(0, 0, 0, 0.12)';
                const img = e.currentTarget.querySelector('img');
                if(img) img.style.transform = 'scale(1.05)';
              }"
              @mouseleave="(e) => {
                e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.08)';
                const img = e.currentTarget.querySelector('img');
                if(img) img.style.transform = 'scale(1)';
              }">
              <img 
                :src="item.isLoaded ? resolveCover(item) : ''" 
                :data-src="resolveCover(item)"
                :alt="item.title"
                @error="onCoverError" 
                style="width: 100%; height: 100%; object-fit: cover; transition: transform 0.5s;"
                class="lazy-load-image" />
              <div style="position: absolute; bottom: 8px; right: 8px; background: rgba(31, 41, 55, 0.8); color: white; font-size: 11px; font-weight: 900; padding: 4px 8px; border-radius: 6px;">
                {{ formatDuration(item.durationSeconds) }}
              </div>
            </div>

            <!-- 信息 -->
            <div style="display: flex; gap: 12px;">
              <img
                :src="resolveAvatar(item.authorAvatar)"
                alt="avatar"
                @error="onAvatarError"
                style="width: 32px; height: 32px; border-radius: 50%; object-fit: cover; flex-shrink: 0; background: linear-gradient(135deg, #a78bfa 0%, #f472b6 100%);" />
              <div style="flex: 1; min-width: 0;">
                <h4 style="font-weight: 700; font-size: 13px; color: #1f2937; line-height: 1.3; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; cursor: pointer;">
                  {{ item.title }}
                </h4>
                <p style="font-size: 12px; color: #64748b; margin-top: 4px;">{{ item.authorName || '用户' }} · {{ formatCount(item.playCount) }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载更多按钮 -->
      <div v-if="!loading && hasMore && videoList.length > 0" style="display: flex; justify-content: center; padding: 48px 0; animation: slideUpFade 0.6s cubic-bezier(0.16, 1, 0.3, 1); animation-delay: 0.5s;">
        <button @click="loadMore" 
          style="padding: 14px 48px; border-radius: 20px; background: #1f2937; color: white; font-weight: 900; font-size: 14px; border: none; cursor: pointer; transition: all 0.3s; box-shadow: 0 8px 16px rgba(31, 41, 55, 0.20);"
          @mouseenter="(e) => {
            e.currentTarget.style.backgroundColor = '#4f46e5';
            e.currentTarget.style.transform = 'translateY(-2px)';
            e.currentTarget.style.boxShadow = '0 12px 32px rgba(79, 70, 229, 0.30)';
          }"
          @mouseleave="(e) => {
            e.currentTarget.style.backgroundColor = '#1f2937';
            e.currentTarget.style.transform = 'translateY(0)';
            e.currentTarget.style.boxShadow = '0 8px 16px rgba(31, 41, 55, 0.20)';
          }">
          加载更多
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getVideoList, getRecommended, getHotList } from '../api/video'
import SkeletonScreen from '../components/SkeletonScreen.vue'
import { applyImageFallbackOnce } from '../utils/imageFallback'
import { appendSeenIds, replaceRecommendationBatch } from '../utils/recommendationRefresh'

const router = useRouter()
const route = useRoute()
const activeTab = ref('recommend')
const videoCards = ref([])
const heroCardRef = ref(null)
const heroCardHeight = ref(0)

// 测试数据
const mockVideoData = [
  {
    id: 1,
    title: 'AI 究竟如何重塑我们的工作流？深度解析 GPT-4 架构',
    authorName: '极客湾Geekerwan',
    coverUrl: 'https://images.unsplash.com/photo-1677442d019cecf5e5b65b7408fdef83?w=800&h=450&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1677442d019cecf5e5b65b7408fdef83?w=800&h=450&fit=crop',
    playCount: 1250000,
    durationSeconds: 1240,
    isLoaded: false
  },
  {
    id: 2,
    title: 'SpringBoot 3.0 完整教程：从零到部署争霸者',
    authorName: '技术博主',
    coverUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    playCount: 580000,
    durationSeconds: 2160,
    isLoaded: false
  },
  {
    id: 3,
    title: '2024 前端工程化演进：Vite 与 Turbopack 对比',
    authorName: '前端大师',
    coverUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    playCount: 420000,
    durationSeconds: 1680,
    isLoaded: false
  },
  {
    id: 4,
    title: '一小时掌握 React 18 并发特性与 Hooks 进阶',
    authorName: 'React讲师',
    coverUrl: 'https://images.unsplash.com/photo-1573141520381-a6fb0d95cbbb?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1573141520381-a6fb0d95cbbb?w=400&h=225&fit=crop',
    playCount: 890000,
    durationSeconds: 3600,
    isLoaded: false
  },
  {
    id: 5,
    title: 'TypeScript 5.0 新特性详解与最佳实践',
    authorName: '代码艺术',
    coverUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    playCount: 650000,
    durationSeconds: 1920,
    isLoaded: false
  },
  {
    id: 6,
    title: '微服务架构设计：从零到生产环境',
    authorName: '架构设计师',
    coverUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    playCount: 320000,
    durationSeconds: 2700,
    isLoaded: false
  },
  {
    id: 7,
    title: 'Vue 3 Composition API 完全指南',
    authorName: 'Vue专家',
    coverUrl: 'https://images.unsplash.com/photo-1598527682523-e3ec7c724ce0?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1598527682523-e3ec7c724ce0?w=400&h=225&fit=crop',
    playCount: 980000,
    durationSeconds: 2400,
    isLoaded: false
  },
  {
    id: 8,
    title: 'Kubernetes 容器编排实战：从入门到精通',
    authorName: 'DevOps工程师',
    coverUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    playCount: 450000,
    durationSeconds: 3300,
    isLoaded: false
  },
  {
    id: 9,
    title: 'GraphQL 最佳实践与优化策略',
    authorName: '后端高级',
    coverUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    playCount: 220000,
    durationSeconds: 1560,
    isLoaded: false
  },
  {
    id: 10,
    title: 'Web 安全防护：XSS、CSRF 和 SQL 注入防御',
    authorName: '安全专家',
    coverUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    playCount: 750000,
    durationSeconds: 2040,
    isLoaded: false
  },
  {
    id: 11,
    title: '深入理解 JavaScript 异步编程：Promise、Async/Await',
    authorName: 'JS大师',
    coverUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    playCount: 1100000,
    durationSeconds: 2520,
    isLoaded: false
  },
  {
    id: 12,
    title: 'Redis 分布式缓存设计与实践',
    authorName: '缓存专家',
    coverUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    playCount: 620000,
    durationSeconds: 2880,
    isLoaded: false
  },
  {
    id: 13,
    title: 'MySQL 性能优化：从查询到索引的完整指南',
    authorName: 'DB优化师',
    coverUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    playCount: 540000,
    durationSeconds: 3120,
    isLoaded: false
  },
  {
    id: 14,
    title: 'Docker 与容器化部署：一步步构建生产级应用',
    authorName: '容器工程师',
    coverUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1633356122544-f134324ef6db?w=400&h=225&fit=crop',
    playCount: 800000,
    durationSeconds: 2400,
    isLoaded: false
  },
  {
    id: 15,
    title: 'CI/CD 流水线设计：使用 GitLab CI 实现自动化部署',
    authorName: 'DevOps大神',
    coverUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1516321318423-f06f70d504f0?w=400&h=225&fit=crop',
    playCount: 380000,
    durationSeconds: 2160,
    isLoaded: false
  },
  {
    id: 16,
    title: '代码质量管理：SonarQube 与单元测试最佳实践',
    authorName: '质量可达性经理',
    coverUrl: 'https://images.unsplash.com/photo-1598527682523-e3ec7c724ce0?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1598527682523-e3ec7c724ce0?w=400&h=225&fit=crop',
    playCount: 290000,
    durationSeconds: 1800,
    isLoaded: false
  },
  {
    id: 17,
    title: '接口设计与 REST API 规范：打造高可用服务',
    authorName: 'API设计师',
    coverUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1517694712202-14dd9538aa97?w=400&h=225&fit=crop',
    playCount: 710000,
    durationSeconds: 1920,
    isLoaded: false
  },
  {
    id: 18,
    title: '并发编程深度解析：Goroutine、协程与线程模型',
    authorName: 'Go语言专家',
    coverUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    previewUrl: 'https://images.unsplash.com/photo-1558694528-5ec90d83b1b5?w=400&h=225&fit=crop',
    playCount: 520000,
    durationSeconds: 2640,
    isLoaded: false
  },
]

const videoList = ref(mockVideoData)
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
const pageSize = 16
const error = ref('')
const seenRecommendedIds = ref([])
const refreshingRecommend = ref(false)
const placeholderCover = new URL('../assets/cover-placeholder.png', import.meta.url).href
const avatarPlaceholder = new URL('../assets/avatar-placeholder.png', import.meta.url).href
const aiPanelStyle = computed(() => ({
  gridColumn: 'span 4',
  height: heroCardHeight.value > 0 ? `${heroCardHeight.value}px` : 'auto',
  marginLeft: '12px'
}))
let observer = null
let heroResizeObserver = null

const bentoVideos = computed(() => {
  const list = [...(videoList.value || [])]
  if (activeTab.value === 'recommend') {
    return list.sort((a, b) => {
      const recommendDiff = Number(Boolean(b?.isRecommended)) - Number(Boolean(a?.isRecommended))
      if (recommendDiff !== 0) return recommendDiff
      const playDiff = Number(b?.playCount || 0) - Number(a?.playCount || 0)
      if (playDiff !== 0) return playDiff
      return new Date(b?.createTime || 0).getTime() - new Date(a?.createTime || 0).getTime()
    })
  }
  return list
})

const heroVideo = computed(() => bentoVideos.value[0] || null)
const aiPanelVideos = computed(() => bentoVideos.value.slice(1, 4))

const fetchApi = (p, excludeIds = []) => {
  if (activeTab.value === 'hot') return getHotList(p, pageSize)
  return activeTab.value === 'recommend'
    ? getRecommended(p, pageSize, excludeIds)
    : getVideoList(p, pageSize)
}

async function fetchList(isMore = false, excludeIds = []) {
  if (loading.value) return
  loading.value = true
  if (!isMore) error.value = ''
  try {
    const res = await fetchApi(isMore ? page.value : 1, excludeIds)
    const list = res.records || []
    // 为新数据添加 isLoaded 属性
    const newList = list.map(item => ({ ...item, isLoaded: false }))
    if (isMore) videoList.value.push(...newList)
    else videoList.value = newList

    if (activeTab.value === 'recommend') {
      seenRecommendedIds.value = appendSeenIds(seenRecommendedIds.value, newList)
    }

    hasMore.value = res.current < res.pages
    page.value = isMore ? page.value + 1 : 2
  } catch (e) {
    console.error('Failed to fetch video list:', e)
    if (!isMore) {
      // 使用测试数据
      videoList.value = mockVideoData
      hasMore.value = false
      error.value = ''
    }
  } finally {
    loading.value = false
    // 重新初始化 Intersection Observer
    initObserver()
  }
}

function switchTab(tab) {
  activeTab.value = tab
  page.value = 1
  if (tab !== 'recommend') {
    seenRecommendedIds.value = []
  }
  fetchList(false)
}

function syncFromQuery() {
  const tab = route.query.tab ? String(route.query.tab) : ''
  if (tab && ['recommend', 'latest', 'hot'].includes(tab)) activeTab.value = tab
}

function loadMore() {
  if (refreshingRecommend.value) return
  const excludeIds = activeTab.value === 'recommend' ? seenRecommendedIds.value : []
  fetchList(true, excludeIds)
}

async function refreshRecommendBatch() {
  if (activeTab.value !== 'recommend' || refreshingRecommend.value || loading.value) return
  refreshingRecommend.value = true
  try {
    const res = await getRecommended(1, pageSize, seenRecommendedIds.value)
    const nextRecords = Array.isArray(res?.records) ? res.records : []
    const nextBatch = nextRecords.map(item => ({ ...item, isLoaded: false }))
    videoList.value = replaceRecommendationBatch(videoList.value, nextBatch)
    seenRecommendedIds.value = appendSeenIds(seenRecommendedIds.value, nextBatch)
    hasMore.value = res.current < res.pages
    page.value = 2
  } catch (e) {
    console.error('Failed to refresh recommendation batch:', e)
  } finally {
    refreshingRecommend.value = false
    initObserver()
  }
}

function goVideo(id) { router.push(`/video/${id}`) }
function goProfile(authorId) { if (authorId) router.push(`/user/${authorId}`) }
function resolveCover(item) {
  if (item.previewUrl) return item.previewUrl
  if (item.coverUrl) return `/api/file/cover?url=${encodeURIComponent(item.coverUrl)}`
  return placeholderCover
}
function onCoverError(event) { applyImageFallbackOnce(event, placeholderCover) }
function resolveAvatar(avatar) {
  if (!avatar) return avatarPlaceholder
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) return avatar
  if (avatar.startsWith('/api/file/avatar') || avatar.startsWith('/file/avatar')) return avatar
  return `/api/file/avatar?url=${encodeURIComponent(avatar)}`
}
function onAvatarError(event) { applyImageFallbackOnce(event, avatarPlaceholder) }
function formatCount(n) { if (!n) return '0'; if (n >= 10000) return `${(n / 10000).toFixed(1)}万`; return String(n) }
function formatDuration(sec) { if (sec == null) return '--:--'; const m = Math.floor(sec / 60); const s = sec % 60; return `${m}:${String(s).padStart(2, '0')}` }
function formatDate(value) { return value ? String(value).slice(5, 10) : '' }

// 初始化 Intersection Observer
function initObserver() {
  // 清理旧的 observer
  if (observer) {
    observer.disconnect()
  }

  // 创建新的 observer
  observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        const img = entry.target.querySelector('.lazy-load-image')
        if (img && img.dataset.src) {
          const videoId = entry.target.dataset.videoId
          const videoIndex = videoList.value.findIndex(item => item.id === parseInt(videoId))
          if (videoIndex !== -1) {
            videoList.value[videoIndex].isLoaded = true
          }
          // 停止观察当前元素
          observer.unobserve(entry.target)
        }
      }
    })
  }, {
    root: null,
    rootMargin: '200px', // 提前200px开始加载
    threshold: 0.1
  })

  // 观察所有视频卡片
  setTimeout(() => {
    videoCards.value.forEach((card) => {
      if (card) {
        observer.observe(card)
      }
    })
  }, 100)
}

function syncHeroCardHeight() {
  const el = heroCardRef.value
  heroCardHeight.value = el ? el.getBoundingClientRect().height : 0
}

function initHeroResizeObserver() {
  if (heroResizeObserver) {
    heroResizeObserver.disconnect()
  }
  if (!heroCardRef.value || typeof ResizeObserver === 'undefined') {
    return
  }
  heroResizeObserver = new ResizeObserver(() => {
    syncHeroCardHeight()
  })
  heroResizeObserver.observe(heroCardRef.value)
}

onMounted(() => {
  initObserver()
  nextTick(() => {
    syncHeroCardHeight()
    initHeroResizeObserver()
  })
})

onUnmounted(() => {
  if (observer) {
    observer.disconnect()
  }
  if (heroResizeObserver) {
    heroResizeObserver.disconnect()
  }
})

watch(videoList, async () => {
  await nextTick()
  syncHeroCardHeight()
  initHeroResizeObserver()
}, { deep: true })

watch(() => route.query, () => {
  syncFromQuery()
  page.value = 1
  if (activeTab.value !== 'recommend') {
    seenRecommendedIds.value = []
  }
  fetchList(false)
}, { immediate: true })
</script>

<style scoped>
@keyframes slideUpFade {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes load {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-8px); }
}

/* ==================== Bento Grid 便当盒布局 ==================== */

/* 主卡片容器 */
.bento-hero-card {
  position: relative;
  transition: all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.bento-hero-card:hover .bento-hero-img {
  transform: scale(1.05);
}

/* 主卡片封面缩放 */
.bento-hero-cover {
  will-change: transform;
  backface-visibility: hidden;
}

.bento-hero-img {
  transition: transform 0.7s cubic-bezier(0.34, 1.56, 0.64, 1);
  will-change: transform;
}

/* ==================== 通用样式 ==================== */
.refresh-batch-btn {
  padding: 10px 18px;
  border: none;
  border-radius: 999px;
  background: #fb7299;
  color: white;
  font-weight: 700;
  cursor: pointer;
  flex-shrink: 0;
}
.refresh-batch-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.animate-enter {
  animation: slideUpFade 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards;
  opacity: 0;
}
.home { padding-bottom: 40px; }
.ai-answer.error { background: #fff5f5; border-color: #ffd7d7; color: #c0392b; }
.tabs { display: flex; align-items: center; gap: 4px; margin-bottom: 20px; border-bottom: 1px solid #e3e5e7; }
.tab { position: relative; padding: 10px 16px; font-size: 15px; color: #61666d; background: transparent; border: none; cursor: pointer; }
.tab.active { color: #fb7299; font-weight: 700; }
.tab.active::after { content: ''; position: absolute; left: 8px; right: 8px; bottom: -1px; height: 3px; background: #fb7299; }
.video-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.video-card { cursor: pointer; border-radius: 8px; overflow: hidden; background: #fff; }
.cover-wrap { position: relative; aspect-ratio: 16/9; background: #f4f5f7; overflow: hidden; }
.cover { width: 100%; height: 100%; object-fit: cover; }
.cover-overlay { position: absolute; left: 0; right: 0; bottom: 0; padding: 12px 8px 6px; background: linear-gradient(transparent, rgba(0,0,0,.6)); display: flex; justify-content: space-between; }
.stat { font-size: 12px; color: #fff; }
.duration-badge { position: absolute; right: 6px; bottom: 6px; padding: 2px 6px; background: rgba(0,0,0,.8); color: #fff; font-size: 12px; border-radius: 4px; }
.card-info { padding: 10px; }
.card-title { font-size: 14px; line-height: 1.4; color: #18191c; margin-bottom: 6px; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.author-name { font-size: 12px; color: #61666d; }
.card-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #9499a0; }
.loading { display: flex; justify-content: center; padding: 32px 0; }
.loading-dots { display: flex; gap: 6px; }
.loading-dots span { width: 8px; height: 8px; background: #fb7299; border-radius: 50%; animation: bounce 1.2s infinite; }
.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,80%,100% { transform: scale(.8); opacity: .5; } 40% { transform: scale(1.2); opacity: 1; } }
.load-more { text-align: center; padding: 28px 0 0; }
.load-more button { padding: 9px 32px; font-size: 14px; color: #fb7299; background: #fff; border: 1px solid #fb7299; border-radius: 20px; }
.empty { text-align: center; padding: 60px 0; color: #9499a0; font-size: 15px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
</style>
