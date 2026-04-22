export function validateUploadForm({ videoFile, title, description, categoryId, tagIds }) {
  if (!videoFile) return '请选择视频文件'
  if (!String(title || '').trim()) return '请输入视频标题'
  if (!String(description || '').trim()) return '请输入视频简介'
  if (!categoryId) return '请选择视频分类'
  if (!Array.isArray(tagIds) || tagIds.length === 0) return '请至少选择一个标签'
  return ''
}
