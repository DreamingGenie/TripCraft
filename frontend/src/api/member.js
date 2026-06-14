import { http } from './http'

export const memberApi = {
  updateNickname: (nickname) => http.patch('/api/members/me/nickname', { nickname }),
  updatePassword: (currentPassword, newPassword) =>
    http.patch('/api/members/me/password', { currentPassword, newPassword }),
}
