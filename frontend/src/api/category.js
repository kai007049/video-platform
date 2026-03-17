import request from './request'

export const getCategoryTree = () => request.get('/category/tree')
export const getCategoryList = () => request.get('/category/list')
