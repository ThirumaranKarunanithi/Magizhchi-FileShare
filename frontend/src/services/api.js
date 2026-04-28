import axios from 'axios';

// In dev, Vite proxies /api → localhost:8080, so baseURL stays empty.
// In production, set VITE_API_URL to your backend origin.
const BASE = import.meta.env.VITE_API_URL || '';

const api = axios.create({ baseURL: BASE });

// Attach JWT on every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Auto-refresh on 401
api.interceptors.response.use(
  r => r,
  async error => {
    const original = error.config;
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true;
      try {
        const refreshToken = localStorage.getItem('refreshToken');
        const { data } = await axios.post(`${BASE}/api/auth/refresh`, { refreshToken });
        localStorage.setItem('accessToken',  data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return api(original);
      } catch {
        localStorage.clear();
        window.location.href = '/login';
      }
    }
    return Promise.reject(error?.response?.data?.message || 'Something went wrong.');
  }
);

// ── Auth ────────────────────────────────────────────────────────────────────
export const auth = {
  registerSendOtp:  data  => api.post('/api/auth/register/send-otp', data),
  registerVerify:   data  => api.post('/api/auth/register/verify', data),
  loginSendOtp:     data  => api.post('/api/auth/login/send-otp', data),
  loginVerify:      data  => api.post('/api/auth/login/verify', data),
};

// ── Users ────────────────────────────────────────────────────────────────────
export const users = {
  getMe:        ()           => api.get('/api/users/me'),
  updateMe:     data         => api.patch('/api/users/me', data),
  uploadPhoto:  formData     => api.post('/api/users/me/photo', formData),
  search:       q            => api.get('/api/users/search', { params: { q } }),
  getById:      id           => api.get(`/api/users/${id}`),
};

// ── Conversations ────────────────────────────────────────────────────────────
export const conversations = {
  list:           ()               => api.get('/api/conversations'),
  personal:       ()               => api.get('/api/conversations/personal'),
  get:            id               => api.get(`/api/conversations/${id}`),
  openDirect:     targetUserId     => api.post(`/api/conversations/direct/${targetUserId}`),
  createGroup:    formData         => api.post('/api/conversations/group', formData),
  addMember:      (cid, uid)       => api.post(`/api/conversations/${cid}/members/${uid}`),
  removeMember:   (cid, uid)       => api.delete(`/api/conversations/${cid}/members/${uid}`),
  setMemberRole:  (cid, uid, role) => api.patch(`/api/conversations/${cid}/members/${uid}/role`, null, { params: { role } }),
  members:        (cid)            => api.get(`/api/conversations/${cid}/members`),
  fileHistory:    (cid, p = 0, size = 200) =>
                    api.get(`/api/conversations/${cid}/files`, { params: { page: p, size } }),
};

// ── Connections & Privacy ────────────────────────────────────────────────────
export const connections = {
  sendRequest:      (userId)      => api.post(`/api/connections/request/${userId}`),
  accept:           (requestId)   => api.post(`/api/connections/request/${requestId}/accept`),
  reject:           (requestId)   => api.post(`/api/connections/request/${requestId}/reject`),
  cancel:           (requestId)   => api.delete(`/api/connections/request/${requestId}`),
  receivedRequests: ()            => api.get('/api/connections/requests/received'),
  sentRequests:     ()            => api.get('/api/connections/requests/sent'),
  unfriend:         (userId)      => api.delete(`/api/connections/unfriend/${userId}`),
  block:            (userId)      => api.post(`/api/users/${userId}/block`),
  unblock:          (userId)      => api.delete(`/api/users/${userId}/block`),
  blocked:          ()            => api.get('/api/users/blocked'),
};

// ── Storage ──────────────────────────────────────────────────────────────────
export const storage = {
  usage: () => api.get('/api/storage/usage'),
};

// ── Sharing ──────────────────────────────────────────────────────────────────
export const sharing = {
  /** Share files: { resourceIds, shareType, targetId, permission } */
  create:       (body)     => api.post('/api/share', body),
  /** Files shared directly or via a group with the current user */
  sharedWithMe: ()         => api.get('/api/share/shared-with-me'),
  /** Files the current user has shared with others */
  sharedByMe:   ()         => api.get('/api/share/shared-by-me'),
  /** Revoke a share by its ID */
  revoke:       (shareId)  => api.delete(`/api/share/${shareId}`),
  /**
   * Shares visible inside a conversation.
   * shareType: 'USER' (direct) or 'GROUP'
   * targetId:  the other user's ID (USER) or the conversation ID (GROUP)
   */
  context: (shareType, targetId) =>
    api.get('/api/share/context', { params: { shareType, targetId } }),
  /**
   * All shares visible in a conversation — works for both sharer and recipient.
   * The server resolves the conversation type so no extra params are needed.
   */
  inConversation: (conversationId) =>
    api.get(`/api/share/in-conversation/${conversationId}`),
};

// ── Files ────────────────────────────────────────────────────────────────────
export const files = {
  search:        q               => api.get('/api/files/search', { params: { q } }),
  send:          (cid, formData)  => api.post(`/api/files/send/${cid}`, formData),
  sendFolder:    (cid, formData, onProgress) =>
                   api.post(`/api/files/send-folder/${cid}`, formData, {
                     onUploadProgress: onProgress,
                   }),
  downloadUrl:   id               => api.get(`/api/files/${id}/download-url`),
  previewUrl:    id               => api.get(`/api/files/${id}/preview-url`),
  thumbnailUrl:  id               => api.get(`/api/files/${id}/thumbnail-url`),
  delete:        id               => api.delete(`/api/files/${id}`),
  pin:           id               => api.post(`/api/files/${id}/pin`),
  unpin:         id               => api.delete(`/api/files/${id}/pin`),
  pinned:        conversationId   => api.get('/api/files/pinned', { params: { conversationId } }),
  setPermission: (id, permission) => api.patch(`/api/files/${id}/permissions`, { permission }),
};

// ── Folders ──────────────────────────────────────────────────────────────────
export const folders = {
  create:     (body)                        => api.post('/api/folders', body),
  list:       (conversationId, parentId)    => api.get('/api/folders', { params: { conversationId, parentFolderId: parentId } }),
  /** Flat listing — every folder in the conversation, any depth. */
  listAll:    (conversationId)              => api.get('/api/folders', { params: { conversationId, flat: true } }),
  breadcrumb: (folderId)                    => api.get(`/api/folders/${folderId}/breadcrumb`),
  rename:     (folderId, name)              => api.patch(`/api/folders/${folderId}`, { name }),
  delete:     (folderId)                    => api.delete(`/api/folders/${folderId}`),
  pin:        (folderId)                    => api.post(`/api/folders/${folderId}/pin`),
  unpin:      (folderId)                    => api.delete(`/api/folders/${folderId}/pin`),
};

// ── Activity ─────────────────────────────────────────────────────────────────
export const activity = {
  get: (conversationId, limit = 20) =>
    api.get('/api/activity', { params: { conversationId, limit } }),
};

export default api;
