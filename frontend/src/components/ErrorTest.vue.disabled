<template>
  <div class="error-test">
    <h2 class="test-title">错误边界测试</h2>
    <p class="test-description">点击下方按钮触发不同类型的错误，测试错误边界功能</p>
    
    <div class="test-buttons">
      <button class="test-btn error" @click="triggerRuntimeError">
        触发运行时错误
      </button>
      <button class="test-btn promise" @click="triggerPromiseError">
        触发Promise错误
      </button>
      <button class="test-btn syntax" @click="triggerSyntaxError">
        触发语法错误
      </button>
      <button class="test-btn clear" @click="clearErrors">
        清除错误记录
      </button>
    </div>
    
    <div class="test-info">
      <h3>测试说明</h3>
      <ul>
        <li>运行时错误：模拟组件渲染过程中的错误</li>
        <li>Promise错误：模拟异步操作中的错误</li>
        <li>语法错误：模拟代码语法错误</li>
        <li>清除错误记录：清除localStorage中的错误记录</li>
      </ul>
    </div>
  </div>
</template>

<script setup>
function triggerRuntimeError() {
  // 模拟运行时错误
  throw new Error('这是一个测试用的运行时错误')
}

function triggerPromiseError() {
  // 模拟Promise错误
  new Promise((resolve, reject) => {
    setTimeout(() => {
      reject(new Error('这是一个测试用的Promise错误'))
    }, 100)
  })
}

function triggerSyntaxError() {
  // 模拟语法错误（通过eval）
  try {
    eval('const x = ;') // 故意的语法错误
  } catch (e) {
    console.error('语法错误测试:', e)
  }
}

function clearErrors() {
  // 清除localStorage中的错误记录
  try {
    localStorage.removeItem('errorTrace')
    alert('错误记录已清除')
  } catch (e) {
    console.error('清除错误记录失败:', e)
  }
}
</script>

<style scoped>
.error-test {
  padding: 40px;
  max-width: 800px;
  margin: 0 auto;
  text-align: center;
}

.test-title {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin-bottom: 16px;
}

.test-description {
  font-size: 16px;
  color: #6b7280;
  margin-bottom: 32px;
  line-height: 1.5;
}

.test-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
  margin-bottom: 40px;
}

.test-btn {
  padding: 12px 24px;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  border: none;
  min-width: 150px;
}

.test-btn.error {
  background: #fee2e2;
  color: #dc2626;
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.15);
}

.test-btn.error:hover {
  background: #fecaca;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(220, 38, 38, 0.2);
}

.test-btn.promise {
  background: #dbeafe;
  color: #2563eb;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
}

.test-btn.promise:hover {
  background: #bfdbfe;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.2);
}

.test-btn.syntax {
  background: #fef3c7;
  color: #d97706;
  box-shadow: 0 4px 12px rgba(217, 119, 6, 0.15);
}

.test-btn.syntax:hover {
  background: #fde68a;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(217, 119, 6, 0.2);
}

.test-btn.clear {
  background: #d1fae5;
  color: #059669;
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.15);
}

.test-btn.clear:hover {
  background: #a7f3d0;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(5, 150, 105, 0.2);
}

.test-info {
  background: #f8fafc;
  border-radius: 16px;
  padding: 24px;
  text-align: left;
  border: 1px solid #e2e8f0;
}

.test-info h3 {
  margin: 0 0 16px 0;
  font-size: 18px;
  font-weight: 600;
  color: #374151;
}

.test-info ul {
  margin: 0;
  padding-left: 24px;
}

.test-info li {
  margin-bottom: 8px;
  color: #6b7280;
  line-height: 1.4;
}

@media (max-width: 768px) {
  .error-test {
    padding: 24px;
  }
  
  .test-buttons {
    flex-direction: column;
    align-items: center;
  }
  
  .test-btn {
    width: 100%;
    max-width: 300px;
  }
}
</style>