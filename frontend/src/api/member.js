import { http } from './http'

export const memberApi = {
  updateNickname: (nickname) => http.patch('/api/members/me/nickname', { nickname }),
  updatePassword: (currentPassword, newPassword) =>
    http.patch('/api/members/me/password', { currentPassword, newPassword }),

  getProfileImage: () => http.get('/api/members/me/profile-image'),
  uploadProfileImage: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.postForm('/api/members/me/profile-image', form)
  },
  deleteProfileImage: () => http.del('/api/members/me/profile-image'),
  withdraw: (password) => http.del('/api/members/me', password ? { password } : {}),
  getVisitedRegions: () => http.get('/api/members/me/visited-regions'),

  // 방문 지도 (후기 사진 기반)
  getMyMap: () => http.get('/api/members/me/map'),
  getRegionStories: (sidoCode) => http.get(`/api/members/me/map/regions/${sidoCode}/stories`),
  getPostImages: (sidoCode, postId) =>
    http.get(`/api/members/me/map/regions/${sidoCode}/posts/${postId}/images`),
  setRegionCover: (regionCode, imageUrl, crop = {}) =>
    http.put('/api/members/me/map/cover', {
      regionLevel: 'SIDO', regionCode, imageUrl,
      focusX: crop.focusX, focusY: crop.focusY, zoom: crop.zoom,
    }),
  uploadRegionCover: (regionCode, file) => {
    const form = new FormData()
    form.append('regionCode', regionCode)
    form.append('file', file)
    return http.postForm('/api/members/me/map/cover/upload', form)
  },
  updateRegionCrop: (regionCode, crop) =>
    http.patch('/api/members/me/map/cover/crop', {
      regionLevel: 'SIDO', regionCode,
      focusX: crop.focusX, focusY: crop.focusY, zoom: crop.zoom,
    }),
  resetRegionCover: (sidoCode) => http.del(`/api/members/me/map/cover/${sidoCode}`),
}
