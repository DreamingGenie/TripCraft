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
}
