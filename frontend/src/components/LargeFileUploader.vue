<template>
  <div class="large-file-uploader">
    <div v-if="!isUploading" class="upload-area" :class="{ dragging: isDragging }" @drop="handleDrop" @dragover.prevent @dragenter.prevent @dragleave.prevent>
      <input type="file" ref="fileInput" class="file-input" @change="handleFileSelect" accept="video/*" />
      <div class="upload-content">
        <div class="upload-icon">📁</div>
        <h3 class="upload-title">拖拽文件到此处或点击上传</h3>
        <p class="upload-description">支持视频文件，最大支持10GB</p>
        <p class="upload-tips">支持断点续传，刷新页面后可继续上传</p>
      </div>
    </div>
    
    <div v-else class="upload-progress">
      <div class="progress-header">
        <h3 class="progress-title">上传中</h3>
        <span class="progress-file-name">{{ fileName }}</span>
      </div>
      
      <div class="progress-bar-container">
        <div class="progress-bar" :style="{ width: `${progress}%` }"></div>
        <span class="progress-text">{{ progress.toFixed(1) }}%</span>
      </div>
      
      <div class="progress-info">
        <span class="info-item">{{ formatSize(uploadedSize) }} / {{ formatSize(fileSize) }}</span>
        <span class="info-item">{{ formatSpeed(uploadSpeed) }}</span>
        <span class="info-item">{{ formatTime(remainingTime) }}</span>
      </div>
      
      <div class="progress-actions">
        <button class="btn-secondary" @click="handlePause" v-if="!isPaused">暂停</button>
        <button class="btn-primary" @click="handleResume" v-else>继续</button>
        <button class="btn-danger" @click="handleCancel">取消</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import SparkMD5 from 'spark-md5'

const props = defineProps({
  chunkSize: {
    type: Number,
    default: 1024 * 1024 * 5 // 5MB per chunk
  },
  apiUrl: {
    type: String,
    default: '/api/upload'
  }
})

const emit = defineEmits(['upload-start', 'upload-progress', 'upload-success', 'upload-error', 'upload-cancel'])

const fileInput = ref(null)
const isDragging = ref(false)
const isUploading = ref(false)
const isPaused = ref(false)
const file = ref(null)
const fileName = ref('')
const fileSize = ref(0)
const uploadedSize = ref(0)
const progress = ref(0)
const uploadSpeed = ref(0)
const remainingTime = ref(0)
const fileHash = ref('')
const chunks = ref([])
const uploadedChunks = ref(new Set())
const uploadStartTime = ref(0)
const lastUploadTime = ref(0)
const lastUploadedSize = ref(0)
const uploadInterval = ref(null)

function formatSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function formatSpeed(bytesPerSecond) {
  if (bytesPerSecond === 0) return '0 B/s'
  return formatSize(bytesPerSecond) + '/s'
}

function formatTime(seconds) {
  if (seconds === 0) return '0s'
  if (seconds < 60) return Math.ceil(seconds) + 's'
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = Math.ceil(seconds % 60)
  return minutes + 'm ' + remainingSeconds + 's'
}

function handleDragEnter() {
  isDragging.value = true
}

function handleDragLeave() {
  isDragging.value = false
}

function handleDrop(event) {
  isDragging.value = false
  const files = event.dataTransfer.files
  if (files.length > 0) {
    processFile(files[0])
  }
}

function handleFileSelect(event) {
  const files = event.target.files
  if (files.length > 0) {
    processFile(files[0])
  }
}

function calculateFileHash(file) {
  return new Promise((resolve) => {
    const spark = new SparkMD5.ArrayBuffer()
    const fileReader = new FileReader()
    
    fileReader.onload = (e) => {
      spark.append(e.target.result)
      resolve(spark.end())
    }
    
    fileReader.readAsArrayBuffer(file)
  })
}

function splitFile(file, chunkSize) {
  const chunks = []
  const totalChunks = Math.ceil(file.size / chunkSize)
  
  for (let i = 0; i < totalChunks; i++) {
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, file.size)
    const chunk = file.slice(start, end)
    
    chunks.push({
      index: i,
      start,
      end,
      size: end - start,
      chunk
    })
  }
  
  return chunks
}

async function processFile(selectedFile) {
  file.value = selectedFile
  fileName.value = selectedFile.name
  fileSize.value = selectedFile.size
  uploadedSize.value = 0
  progress.value = 0
  
  // 计算文件哈希
  fileHash.value = await calculateFileHash(selectedFile)
  
  // 切片文件
  chunks.value = splitFile(selectedFile, props.chunkSize)
  
  // 检查是否有已上传的分片
  await checkUploadedChunks()
  
  // 开始上传
  startUpload()
}

async function checkUploadedChunks() {
  // 这里应该调用后端API检查已上传的分片
  // 暂时模拟已上传的分片
  uploadedChunks.value = new Set()
}

function startUpload() {
  isUploading.value = true
  isPaused.value = false
  uploadStartTime.value = Date.now()
  lastUploadTime.value = Date.now()
  lastUploadedSize.value = 0
  
  emit('upload-start', { file: file.value, fileHash: fileHash.value })
  
  // 开始上传分片
  uploadNextChunk()
  
  // 开始计算上传速度
  uploadInterval.value = setInterval(calculateUploadSpeed, 1000)
}

function calculateUploadSpeed() {
  const currentTime = Date.now()
  const timeElapsed = (currentTime - lastUploadTime.value) / 1000
  const sizeUploaded = uploadedSize.value - lastUploadedSize.value
  
  uploadSpeed.value = sizeUploaded / timeElapsed
  remainingTime.value = (fileSize.value - uploadedSize.value) / uploadSpeed.value
  
  lastUploadTime.value = currentTime
  lastUploadedSize.value = uploadedSize.value
}

async function uploadNextChunk() {
  if (isPaused.value) return
  
  const nextChunk = chunks.value.find(chunk => !uploadedChunks.value.has(chunk.index))
  
  if (!nextChunk) {
    // 所有分片上传完成
    completeUpload()
    return
  }
  
  try {
    await uploadChunk(nextChunk)
    uploadedChunks.value.add(nextChunk.index)
    uploadedSize.value += nextChunk.size
    progress.value = (uploadedSize.value / fileSize.value) * 100
    
    emit('upload-progress', {
      progress: progress.value,
      uploadedSize: uploadedSize.value,
      totalSize: fileSize.value
    })
    
    // 继续上传下一个分片
    uploadNextChunk()
  } catch (error) {
    handleUploadError(error)
  }
}

async function uploadChunk(chunk) {
  const formData = new FormData()
  formData.append('file', chunk.chunk)
  formData.append('fileName', fileName.value)
  formData.append('fileHash', fileHash.value)
  formData.append('chunkIndex', chunk.index)
  formData.append('totalChunks', chunks.value.length)
  formData.append('chunkSize', chunk.size)
  
  // 这里应该调用后端API上传分片
  // 暂时模拟上传
  return new Promise((resolve) => {
    setTimeout(resolve, 500) // 模拟网络延迟
  })
}

function completeUpload() {
  clearInterval(uploadInterval.value)
  isUploading.value = false
  
  emit('upload-success', {
    file: file.value,
    fileHash: fileHash.value,
    fileName: fileName.value
  })
}

function handleUploadError(error) {
  clearInterval(uploadInterval.value)
  isUploading.value = false
  
  emit('upload-error', error)
}

function handlePause() {
  isPaused.value = true
}

function handleResume() {
  isPaused.value = false
  uploadNextChunk()
}

function handleCancel() {
  clearInterval(uploadInterval.value)
  isUploading.value = false
  isPaused.value = false
  
  emit('upload-cancel')
}

onUnmounted(() => {
  if (uploadInterval.value) {
    clearInterval(uploadInterval.value)
  }
})
</script>

<style scoped>
.large-file-uploader {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}

.upload-area {
  border: 2px dashed #e2e8f0;
  border-radius: 16px;
  padding: 60px 20px;
  text-align: center;
  transition: all 0.3s;
  cursor: pointer;
  background: #f8fafc;
}

.upload-area.dragging {
  border-color: #3b82f6;
  background: #eff6ff;
  transform: scale(1.02);
}

.file-input {
  display: none;
}

.upload-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.upload-icon {
  font-size: 48px;
  opacity: 0.6;
}

.upload-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.upload-description {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  line-height: 1.5;
}

.upload-tips {
  margin: 0;
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
}

.upload-progress {
  background: #f8fafc;
  border-radius: 16px;
  padding: 24px;
  border: 1px solid #e2e8f0;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.progress-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.progress-file-name {
  font-size: 14px;
  color: #6b7280;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-bar-container {
  position: relative;
  height: 12px;
  background: #e2e8f0;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 16px;
}

.progress-bar {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  border-radius: 6px;
  transition: width 0.3s ease;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 10px;
  font-weight: 600;
  color: white;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.progress-info {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  font-size: 12px;
  color: #6b7280;
}

.info-item {
  flex: 1;
  text-align: center;
}

.progress-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-primary,
.btn-secondary,
.btn-danger {
  padding: 10px 20px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(59, 130, 246, 0.4);
}

.btn-secondary {
  background: #f3f4f6;
  color: #4b5563;
  border: 1px solid #e5e7eb;
}

.btn-secondary:hover {
  background: #e5e7eb;
  transform: translateY(-2px);
}

.btn-danger {
  background: #fee2e2;
  color: #dc2626;
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.15);
}

.btn-danger:hover {
  background: #fecaca;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(220, 38, 38, 0.2);
}

@media (max-width: 768px) {
  .upload-area {
    padding: 40px 16px;
  }
  
  .upload-title {
    font-size: 18px;
  }
  
  .progress-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
  
  .progress-file-name {
    max-width: 100%;
  }
  
  .progress-info {
    flex-direction: column;
    gap: 4px;
  }
  
  .info-item {
    text-align: left;
  }
  
  .progress-actions {
    flex-direction: column;
  }
  
  .btn-primary,
  .btn-secondary,
  .btn-danger {
    width: 100%;
  }
}
</style>