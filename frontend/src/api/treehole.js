import request from '../utils/request'

export const listPosts = (params) => request.get('/treehole/posts', { params })
export const createPost = (data) => request.post('/treehole/posts', data)
export const getPost = (id) => request.get(`/treehole/posts/${id}`)
export const likePost = (id) => request.post(`/treehole/posts/${id}/like`)
export const unlikePost = (id) => request.delete(`/treehole/posts/${id}/like`)
export const listComments = (id, params) => request.get(`/treehole/posts/${id}/comments`, { params })
export const addComment = (id, data) => request.post(`/treehole/posts/${id}/comments`, data)
/** 发帖前先把图传上去，返回的 url 放进 createPost 的 images 数组 */
export const uploadPostImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return request.post('/treehole/images', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
// 评论区置顶：贴主可置顶任意一条评论，pinned=true 置顶 / false 取消；每个帖子只允许一条
export const pinComment = (id, pinned) => request.put(`/treehole/comments/${id}/pin`, null, { params: { pinned } })
