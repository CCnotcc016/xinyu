import request from '../utils/request'

export const listFriends = () => request.get('/friends')
/** direction = RECEIVED / SENT */
export const listFriendRequests = (direction = 'RECEIVED') =>
  request.get('/friends/requests', { params: { direction } })
export const getPendingRequestCount = () => request.get('/friends/requests/pending-count')
export const sendFriendRequest = (toUserId, message) =>
  request.post('/friends/requests', { toUserId, message })
/** action = ACCEPT / REJECT */
export const handleFriendRequest = (id, action) =>
  request.put(`/friends/requests/${id}`, { action })
export const removeFriend = (friendId) => request.delete(`/friends/${friendId}`)
