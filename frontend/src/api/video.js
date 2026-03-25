import request from './request'

export const getVideoList = (page = 1, size = 12) =>
  request.get('/video/list', { params: { page, size } })

export const getRecommended = (page = 1, size = 12) =>
  request.get('/video/recommended', { params: { page, size } })

export const getHotList = (page = 1, size = 12) =>
  request.get('/video/hot', { params: { page, size } })

export const getVideoByAuthor = (authorId, page = 1, size = 12) =>
  request.get(`/video/author/${authorId}`, { params: { page, size } })

export const getVideoDetail = (id) => request.get(`/video/${id}`)

export const recordPlay = (id) => request.post(`/video/${id}/play`)

export const saveProgress = (id, seconds) =>
  request.post(`/video/${id}/progress`, null, { params: { seconds } })

export const uploadVideo = (formData) =>
  request.post('/video/upload', formData, { timeout: 300000 })

export const getCreatorVideos = (page = 1, size = 12) =>
  request.get('/video/creator', { params: { page, size } })

export const getLikedVideos = (page = 1, size = 12) =>
  request.get('/video/liked', { params: { page, size } })

export const getFavoriteVideos = (page = 1, size = 12) =>
  request.get('/video/favorite', { params: { page, size } })

export const getHistoryVideos = (page = 1, size = 12) =>
  request.get('/video/history', { params: { page, size } })

export const deleteVideo = (id) => request.delete(`/video/${id}`)

export const searchVideos = (keyword, page = 1, size = 12, sortBy = 'comprehensive') =>
  request.get('/search', { params: { keyword, page, size, sortBy } })

export const searchUsers = (keyword, page = 1, size = 12) =>
  request.get('/search/users', { params: { keyword, page, size } })
