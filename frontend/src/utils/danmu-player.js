class DanmuPlayer {
  constructor(options = {}) {
    this.video = options.video
    this.container = options.container
    this.danmuData = options.danmuData || []

    this.enabled = options.enabled !== false
    this.speed = options.speed || 140
    this.opacity = options.opacity || 0.95
    this.fontSize = options.fontSize || 22

    this.trackCount = options.trackCount || 8
    this.trackHeight = options.trackHeight || 32
    this.danmuGap = options.danmuGap || 32

    this.trackNextAvailable = Array.from({ length: this.trackCount }, () => 0)
    this.activeDanmus = new Map()
    this.danmuBySecond = new Map()

    this.lastTickSecond = -1
    this.isPaused = true
    this.animationFrameId = null

    this.init()
  }

  init() {
    if (!this.video || !this.container) {
      console.error('DanmuPlayer: video and container are required')
      return
    }

    this.rebuildDanmuIndex()
    this.bindVideoEvents()
    this.setupContainer()
    console.log('DanmuPlayer initialized with', this.danmuData.length, 'danmus')
  }

  setupContainer() {
    this.container.style.position = 'absolute'
    this.container.style.inset = '0'
    this.container.style.pointerEvents = 'none'
    this.container.style.overflow = 'hidden'
    this.container.style.zIndex = '10'
  }

  rebuildDanmuIndex() {
    const map = new Map()
    for (const d of this.danmuData) {
      const sec = Math.max(0, Math.floor(d.time))
      if (!map.has(sec)) map.set(sec, [])
      map.get(sec).push(d)
    }
    this.danmuBySecond = map
    console.log('Rebuilt danmu index:', map.size, 'seconds')
  }

  bindVideoEvents() {
    this.video.addEventListener('play', this.onPlay.bind(this))
    this.video.addEventListener('pause', this.onPause.bind(this))
    this.video.addEventListener('seeking', this.onSeeking.bind(this))
    this.video.addEventListener('timeupdate', this.onTimeUpdate.bind(this))
    this.video.addEventListener('ended', this.onEnded.bind(this))
  }

  onPlay() {
    this.isPaused = false
    this.startAnimation()
    console.log('Video play, animation started')
  }

  onPause() {
    this.isPaused = true
    this.stopAnimation()
    console.log('Video pause, animation stopped')
  }

  onSeeking() {
    this.clearScreen()
    this.lastTickSecond = Math.floor(this.video.currentTime) - 1
    console.log('Video seeking, screen cleared')
  }

  onTimeUpdate() {
    if (!this.enabled || this.isPaused) return

    const currentTime = this.video.currentTime
    const currentSecond = Math.floor(currentTime)

    if (currentSecond !== this.lastTickSecond) {
      const danmus = this.danmuBySecond.get(currentSecond) || []
      console.log('Time:', currentTime.toFixed(2), 's, Second:', currentSecond, 'Danmus:', danmus.length)

      for (const d of danmus) {
        this.createDanmu(d)
      }
      this.lastTickSecond = currentSecond
    }
  }

  onEnded() {
    this.isPaused = true
    this.stopAnimation()
    console.log('Video ended')
  }

  findAvailableTrack() {
    const now = performance.now()
    let bestTrack = 0
    let bestTime = Infinity

    for (let i = 0; i < this.trackCount; i++) {
      const availableTime = this.trackNextAvailable[i]
      if (availableTime <= now) {
        return i
      }
      if (availableTime < bestTime) {
        bestTime = availableTime
        bestTrack = i
      }
    }

    return bestTrack
  }

  createDanmu(danmu) {
    if (!this.enabled) return

    const track = this.findAvailableTrack()
    const now = performance.now()

    const danmuEl = document.createElement('span')
    danmuEl.className = 'danmu-item'
    danmuEl.textContent = danmu.content
    danmuEl.style.cssText = `
      position: absolute;
      top: ${track * this.trackHeight + 8}px;
      font-size: ${this.fontSize}px;
      opacity: ${this.opacity};
      color: ${danmu.color || '#ffffff'};
      white-space: nowrap;
      font-weight: 600;
      text-shadow: 0 0 2px rgba(0, 0, 0, 0.8);
      pointer-events: none;
      will-change: transform;
    `

    if (danmu.type === 'top') {
      danmuEl.classList.add('danmu-top')
      danmuEl.style.left = '50%'
      danmuEl.style.transform = 'translateX(-50%)'
    }

    this.container.appendChild(danmuEl)

    // Force reflow
    danmuEl.offsetHeight

    const width = danmuEl.getBoundingClientRect().width || 0
    const containerWidth = this.container.clientWidth || 960
    const travel = containerWidth + width
    const duration = Math.max(6, travel / this.speed)

    console.log('Creating danmu:', danmu.content.substring(0, 20), 'track:', track, 'width:', width.toFixed(0), 'duration:', duration.toFixed(1))

    const danmuId = Date.now() + Math.random()
    const danmuData = {
      id: danmuId,
      element: danmuEl,
      x: containerWidth,
      y: track * this.trackHeight + 8,
      width: width,
      speed: this.speed,
      duration: duration,
      startTime: now,
      track: track,
      type: danmu.type || 'scroll'
    }

    this.activeDanmus.set(danmuId, danmuData)

    const gapTime = ((width + this.danmuGap) / this.speed) * 1000
    this.trackNextAvailable[track] = now + gapTime

    if (danmu.type === 'top') {
      setTimeout(() => {
        this.removeDanmu(danmuId)
      }, duration * 1000)
    }
  }

  startAnimation() {
    if (this.animationFrameId) return

    const animate = () => {
      if (this.isPaused) return

      const now = performance.now()
      const containerWidth = this.container.clientWidth || 960

      for (const [id, danmu] of this.activeDanmus) {
        if (danmu.type === 'top') continue

        const elapsed = (now - danmu.startTime) / 1000
        const progress = elapsed / danmu.duration

        if (progress >= 1) {
          this.removeDanmu(id)
        } else {
          danmu.x = containerWidth - (progress * (containerWidth + danmu.width))
          danmu.element.style.transform = `translateX(${danmu.x}px)`
        }
      }

      this.animationFrameId = requestAnimationFrame(animate)
    }

    this.animationFrameId = requestAnimationFrame(animate)
  }

  stopAnimation() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId)
      this.animationFrameId = null
    }
  }

  removeDanmu(id) {
    const danmu = this.activeDanmus.get(id)
    if (danmu) {
      if (danmu.element && danmu.element.parentNode) {
        danmu.element.parentNode.removeChild(danmu.element)
      }
      this.activeDanmus.delete(id)
    }
  }

  clearScreen() {
    for (const [id] of this.activeDanmus) {
      this.removeDanmu(id)
    }
    this.activeDanmus.clear()
  }

  addDanmu(danmu) {
    this.danmuData.push(danmu)
    const sec = Math.floor(danmu.time)
    if (!this.danmuBySecond.has(sec)) {
      this.danmuBySecond.set(sec, [])
    }
    this.danmuBySecond.get(sec).push(danmu)

    // Show immediately if enabled
    if (this.enabled) {
      this.createDanmu(danmu)
    }
  }

  setSpeed(speed) {
    this.speed = speed
  }

  setOpacity(opacity) {
    this.opacity = opacity
    for (const danmu of this.activeDanmus.values()) {
      danmu.element.style.opacity = opacity
    }
  }

  setFontSize(size) {
    this.fontSize = size
    for (const danmu of this.activeDanmus.values()) {
      danmu.element.style.fontSize = size + 'px'
    }
  }

  toggle() {
    this.enabled = !this.enabled
    if (!this.enabled) {
      this.clearScreen()
    }
    return this.enabled
  }

  destroy() {
    this.stopAnimation()
    this.clearScreen()
  }
}

export default DanmuPlayer
