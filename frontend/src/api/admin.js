import request from './request'

export const getAdminVideos = (page = 1, size = 10) =>
  request.get('/admin/videos', { params: { page, size } })

export const setVideoRecommend = (id, recommend) =>
  request.put(`/admin/video/${id}/recommend`, null, { params: { recommend } })

export const getAdminUsers = (page = 1, size = 10) =>
  request.get('/admin/users', { params: { page, size } })

export const uploadDefaultAvatar = (formData) =>
  request.post('/admin/avatar/default', formData)

export const getAdminOpsSummary = () =>
  request.get('/admin/ops/summary')

export const listProducerDeadLetters = (page = 1, size = 10) =>
  request.get('/admin/ops/mq/producer-dead-letters', { params: { page, size } })

export const listConsumerFailures = (page = 1, size = 10) =>
  request.get('/admin/ops/mq/consumer-failures', { params: { page, size } })

export const replayProducerDeadLetters = (eventIds) =>
  request.post('/admin/ops/mq/producer-dead-letters/replay', eventIds)

export const replayConsumerFailures = (recordIds) =>
  request.post('/admin/ops/mq/consumer-failures/replay', recordIds)
