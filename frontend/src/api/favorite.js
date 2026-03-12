import request from './request'

export const addFavorite = (videoId) => request.post(`/favorite/${videoId}`)
export const removeFavorite = (videoId) => request.delete(`/favorite/${videoId}`)
