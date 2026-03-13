import request from './request'

export const register = (data) => request.post('/user/register', data)
export const login = (data) => request.post('/user/login', data)
export const getCaptcha = () => request.get('/captcha')
export const getUserInfo = () => request.get('/user/info')
export const getUpProfile = (id) => request.get(`/user/profile/${id}`)
export const getCreatorStats = () => request.get('/user/creator/stats')
export const updateAvatar = (formData) => request.post('/user/avatar', formData)
