import request from './request'

export const like = (videoId) => request.post(`/like/${videoId}`)
export const unlike = (videoId) => request.delete(`/like/${videoId}`)
export const isLiked = (videoId) => request.get(`/like/${videoId}`)
