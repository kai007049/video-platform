import request from './request'

export const getSearchHistory = (limit = 10) =>
  request.get('/search/history', { params: { limit } })

export const clearSearchHistory = () =>
  request.post('/search/history/clear')

export const getHotSearches = (limit = 10) =>
  request.get('/search/hot', { params: { limit } })
