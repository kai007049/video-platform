import request from './request'

export const getComments = (videoId) => request.get(`/comment/video/${videoId}`)

export const addComment = (data) => request.post('/comment', data)
