import test from 'node:test'
import assert from 'node:assert/strict'

const { validateUploadForm } = await import('../src/views/uploadValidation.js').catch(() => ({}))

function validForm() {
  return {
    videoFile: { name: 'demo.mp4' },
    title: '测试标题',
    description: '测试简介',
    categoryId: 1,
    tagIds: [1]
  }
}

test('requires video file before upload', () => {
  const form = validForm()
  form.videoFile = null

  assert.equal(validateUploadForm?.(form), '请选择视频文件')
})

test('requires title before upload', () => {
  const form = validForm()
  form.title = '   '

  assert.equal(validateUploadForm?.(form), '请输入视频标题')
})

test('requires description before upload', () => {
  const form = validForm()
  form.description = '   '

  assert.equal(validateUploadForm?.(form), '请输入视频简介')
})

test('requires category before upload', () => {
  const form = validForm()
  form.categoryId = ''

  assert.equal(validateUploadForm?.(form), '请选择视频分类')
})

test('requires tag before upload', () => {
  const form = validForm()
  form.tagIds = []

  assert.equal(validateUploadForm?.(form), '请至少选择一个标签')
})

test('accepts first-level category id when category is selected', () => {
  const form = validForm()
  form.categoryId = 5

  assert.equal(validateUploadForm?.(form), '')
})

test('passes when required fields are complete', () => {
  assert.equal(validateUploadForm?.(validForm()), '')
})
