import { describe, expect, it } from 'vitest'
import { validateUploadForm } from '../src/views/uploadValidation.js'

describe('validateUploadForm', () => {
  it('returns the current validation messages in order and empty string for valid input', () => {
    expect(
      validateUploadForm({
        videoFile: null,
        title: '标题',
        description: '简介',
        categoryId: 1,
        tagIds: [1]
      })
    ).toBe('请选择视频文件')

    expect(
      validateUploadForm({
        videoFile: { name: 'video.mp4' },
        title: '   ',
        description: '简介',
        categoryId: 1,
        tagIds: [1]
      })
    ).toBe('请输入视频标题')

    expect(
      validateUploadForm({
        videoFile: { name: 'video.mp4' },
        title: '标题',
        description: '   ',
        categoryId: 1,
        tagIds: [1]
      })
    ).toBe('请输入视频简介')

    expect(
      validateUploadForm({
        videoFile: { name: 'video.mp4' },
        title: '标题',
        description: '简介',
        categoryId: null,
        tagIds: [1]
      })
    ).toBe('请选择视频分类')

    expect(
      validateUploadForm({
        videoFile: { name: 'video.mp4' },
        title: '标题',
        description: '简介',
        categoryId: 1,
        tagIds: []
      })
    ).toBe('请至少选择一个标签')

    expect(
      validateUploadForm({
        videoFile: { name: 'video.mp4' },
        title: '标题',
        description: '简介',
        categoryId: 1,
        tagIds: [1, 2]
      })
    ).toBe('')
  })
})
