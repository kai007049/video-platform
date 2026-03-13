import request from './request'

export const follow = (userId) => request.post(`/follow/${userId}`)
export const unfollow = (userId) => request.delete(`/follow/${userId}`)
export const checkFollow = (userId) => request.get(`/follow/check/${userId}`)
export const getFollowCount = (userId) => request.get(`/follow/count/${userId}`)

export const getFollowingList = (userId) => request.get(`/follow/following/${userId}`)
export const getFanList = (userId) => request.get(`/follow/fans/${userId}`)
