class DanmuControlPanel {
  constructor(danmuPlayer, options = {}) {
    this.danmuPlayer = danmuPlayer
    this.container = options.container || document.body
    this.visible = false
    this.panel = null

    this.init()
  }

  init() {
    this.createPanel()
    this.bindEvents()
  }

  createPanel() {
    const panel = document.createElement('div')
    panel.className = 'danmu-control-panel'
    panel.style.cssText = `
      position: fixed;
      bottom: 20px;
      right: 20px;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
      z-index: 1000;
      min-width: 280px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      display: none;
    `

    panel.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
        <h3 style="margin: 0; font-size: 16px; font-weight: 600; color: #333;">弹幕设置</h3>
        <button class="close-btn" style="background: none; border: none; font-size: 20px; cursor: pointer; color: #666;">×</button>
      </div>
      
      <div style="margin-bottom: 16px;">
        <label style="display: flex; justify-content: space-between; font-size: 13px; color: #666; margin-bottom: 8px;">
          <span>弹幕速度</span>
          <span class="speed-value">140 px/s</span>
        </label>
        <input type="range" class="speed-slider" min="50" max="300" value="140" 
          style="width: 100%; height: 6px; border-radius: 3px; background: #e0e0e0; outline: none; -webkit-appearance: none;">
      </div>
      
      <div style="margin-bottom: 16px;">
        <label style="display: flex; justify-content: space-between; font-size: 13px; color: #666; margin-bottom: 8px;">
          <span>透明度</span>
          <span class="opacity-value">0.95</span>
        </label>
        <input type="range" class="opacity-slider" min="0.1" max="1" step="0.05" value="0.95"
          style="width: 100%; height: 6px; border-radius: 3px; background: #e0e0e0; outline: none; -webkit-appearance: none;">
      </div>
      
      <div style="margin-bottom: 16px;">
        <label style="display: flex; justify-content: space-between; font-size: 13px; color: #666; margin-bottom: 8px;">
          <span>字体大小</span>
          <span class="font-size-value">22 px</span>
        </label>
        <input type="range" class="font-size-slider" min="14" max="40" value="22"
          style="width: 100%; height: 6px; border-radius: 3px; background: #e0e0e0; outline: none; -webkit-appearance: none;">
      </div>
      
      <div style="display: flex; gap: 8px;">
        <button class="toggle-btn" style="flex: 1; padding: 10px; border: none; border-radius: 6px; background: #00a1d6; color: white; font-size: 13px; cursor: pointer; font-weight: 500;">
          关闭弹幕
        </button>
        <button class="clear-btn" style="flex: 1; padding: 10px; border: 1px solid #ddd; border-radius: 6px; background: white; color: #666; font-size: 13px; cursor: pointer;">
          清屏
        </button>
      </div>
    `

    this.container.appendChild(panel)
    this.panel = panel

    // Create toggle button
    const toggleBtn = document.createElement('button')
    toggleBtn.className = 'danmu-panel-toggle'
    toggleBtn.innerHTML = '⚙️'
    toggleBtn.style.cssText = `
      position: fixed;
      bottom: 20px;
      right: 20px;
      width: 48px;
      height: 48px;
      border-radius: 50%;
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(10px);
      border: none;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
      font-size: 20px;
      cursor: pointer;
      z-index: 999;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: transform 0.2s;
    `
    toggleBtn.addEventListener('mouseenter', () => {
      toggleBtn.style.transform = 'scale(1.1)'
    })
    toggleBtn.addEventListener('mouseleave', () => {
      toggleBtn.style.transform = 'scale(1)'
    })

    this.container.appendChild(toggleBtn)
    this.toggleBtn = toggleBtn
  }

  bindEvents() {
    // Toggle panel visibility
    this.toggleBtn.addEventListener('click', () => {
      this.visible = !this.visible
      this.panel.style.display = this.visible ? 'block' : 'none'
      this.toggleBtn.style.display = this.visible ? 'none' : 'flex'
    })

    // Close button
    const closeBtn = this.panel.querySelector('.close-btn')
    closeBtn.addEventListener('click', () => {
      this.visible = false
      this.panel.style.display = 'none'
      this.toggleBtn.style.display = 'flex'
    })

    // Speed slider
    const speedSlider = this.panel.querySelector('.speed-slider')
    const speedValue = this.panel.querySelector('.speed-value')
    speedSlider.addEventListener('input', (e) => {
      const value = e.target.value
      speedValue.textContent = value + ' px/s'
      this.danmuPlayer.setSpeed(parseInt(value))
    })

    // Opacity slider
    const opacitySlider = this.panel.querySelector('.opacity-slider')
    const opacityValue = this.panel.querySelector('.opacity-value')
    opacitySlider.addEventListener('input', (e) => {
      const value = e.target.value
      opacityValue.textContent = value
      this.danmuPlayer.setOpacity(parseFloat(value))
    })

    // Font size slider
    const fontSizeSlider = this.panel.querySelector('.font-size-slider')
    const fontSizeValue = this.panel.querySelector('.font-size-value')
    fontSizeSlider.addEventListener('input', (e) => {
      const value = e.target.value
      fontSizeValue.textContent = value + ' px'
      this.danmuPlayer.setFontSize(parseInt(value))
    })

    // Toggle button
    const toggleBtn = this.panel.querySelector('.toggle-btn')
    toggleBtn.addEventListener('click', () => {
      const enabled = this.danmuPlayer.toggle()
      toggleBtn.textContent = enabled ? '关闭弹幕' : '开启弹幕'
      toggleBtn.style.background = enabled ? '#00a1d6' : '#999'
    })

    // Clear button
    const clearBtn = this.panel.querySelector('.clear-btn')
    clearBtn.addEventListener('click', () => {
      this.danmuPlayer.clearScreen()
    })
  }

  updateToggleState(enabled) {
    const toggleBtn = this.panel.querySelector('.toggle-btn')
    if (toggleBtn) {
      toggleBtn.textContent = enabled ? '关闭弹幕' : '开启弹幕'
      toggleBtn.style.background = enabled ? '#00a1d6' : '#999'
    }
  }

  destroy() {
    if (this.panel && this.panel.parentNode) {
      this.panel.parentNode.removeChild(this.panel)
    }
    if (this.toggleBtn && this.toggleBtn.parentNode) {
      this.toggleBtn.parentNode.removeChild(this.toggleBtn)
    }
  }
}

export default DanmuControlPanel
