import request from './request'

export const getMessageSummary = () => request.get('/message-center/summary')

export const getConversations = () => request.get('/message/conversations')

export const getMessageList = (targetId, page = 1, size = 20) =>
  request.get('/message', { params: { targetId, page, size } })

export const sendMessageApi = (data) => request.post('/message', data)

export const readMessageApi = (targetId) => request.post('/message/read', null, { params: { targetId } })

export const revokeMessageApi = (id) => request.post('/message/revoke', null, { params: { id } })

export const clearConversationApi = (targetId) => request.post('/message/clear', null, { params: { targetId } })

export const getNotifications = (page = 1, size = 20) =>
  request.get('/notification', { params: { page, size } })

export const readNotificationApi = (id) => request.post('/notification/read', null, { params: { id } })
