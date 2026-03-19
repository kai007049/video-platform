import request from './request'

export const getTagList = () => request.get('/tag/list')

export const recommendTags = (payload) => request.post('/tag/recommend', payload)
