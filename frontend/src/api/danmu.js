import request from './request'

export const getDanmus = (videoId) => request.get(`/danmu/video/${videoId}`)
