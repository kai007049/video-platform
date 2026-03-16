import request from './request'

export const getAdminVideos = (page = 1, size = 10) =>
  request.get('/admin/videos', { params: { page, size } })

export const setVideoRecommend = (id, recommend) =>
  request.put(`/admin/video/${id}/recommend`, null, { params: { recommend } })

export const getAdminUsers = (page = 1, size = 10) =>
  request.get('/admin/users', { params: { page, size } })

export const uploadDefaultAvatar = (formData) =>
  request.post('/admin/avatar/default', formData)
