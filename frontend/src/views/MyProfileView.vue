<template>
  <main id="main">
    <div class="profile-layout">
      <div class="profile-content">
        <div class="mypage-header">
          <h2 class="mypage-title">내 정보 수정</h2>
        </div>

        <!-- 프로필 이미지 -->
        <section class="edit-section">
          <h3 class="section-title">프로필 이미지</h3>
          <div class="avatar-row">
            <div class="avatar-wrap">
              <img v-if="profileImageUrl" :src="profileImageUrl" class="avatar-img" alt="프로필 이미지" />
              <div v-else class="avatar-placeholder">{{ auth.user?.nickname?.charAt(0) }}</div>
            </div>
            <div class="avatar-actions">
              <label class="edit-btn upload-label">
                변경
                <input type="file" accept="image/*" class="hidden-input" @change="onImageChange" />
              </label>
              <button v-if="profileImageUrl" class="edit-btn danger-btn" @click="removeImage" :disabled="imageLoading">
                삭제
              </button>
            </div>
          </div>
          <p v-if="imageMsg" :class="['form-msg', imageError ? 'error' : 'success']">{{ imageMsg }}</p>
        </section>

        <!-- 현재 정보 -->
        <div class="info-section">
          <div class="info-row">
            <span class="info-label">이메일</span>
            <span class="info-value">{{ auth.user?.email }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">닉네임</span>
            <span class="info-value">{{ auth.user?.nickname }}</span>
          </div>
        </div>

        <!-- 닉네임 변경 -->
        <section class="edit-section">
          <h3 class="section-title">닉네임 변경</h3>
          <form @submit.prevent="submitNickname" class="edit-form">
            <input
              v-model="nicknameForm.value"
              type="text"
              class="edit-input"
              placeholder="새 닉네임 (2~20자)"
              minlength="2"
              maxlength="20"
              required
            />
            <button type="submit" class="edit-btn" :disabled="nicknameForm.loading">
              {{ nicknameForm.loading ? '변경 중...' : '변경' }}
            </button>
          </form>
          <p v-if="nicknameForm.message" :class="['form-msg', nicknameForm.error ? 'error' : 'success']">
            {{ nicknameForm.message }}
          </p>
        </section>

        <!-- 비밀번호 변경 -->
        <section class="edit-section">
          <h3 class="section-title">비밀번호 변경</h3>
          <form @submit.prevent="submitPassword" class="edit-form vertical">
            <input
              v-model="passwordForm.current"
              type="password"
              class="edit-input"
              placeholder="현재 비밀번호"
              required
            />
            <input
              v-model="passwordForm.next"
              type="password"
              class="edit-input"
              placeholder="새 비밀번호 (8자 이상)"
              minlength="8"
              required
            />
            <input
              v-model="passwordForm.confirm"
              type="password"
              class="edit-input"
              placeholder="새 비밀번호 확인"
              required
            />
            <button type="submit" class="edit-btn" :disabled="passwordForm.loading">
              {{ passwordForm.loading ? '변경 중...' : '변경' }}
            </button>
          </form>
          <p v-if="passwordForm.message" :class="['form-msg', passwordForm.error ? 'error' : 'success']">
            {{ passwordForm.message }}
          </p>
        </section>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { memberApi } from '@/api/member'

const auth = useAuthStore()

const profileImageUrl = ref(null)
const imageLoading    = ref(false)
const imageMsg        = ref('')
const imageError      = ref(false)

const nicknameForm = reactive({ value: '', loading: false, message: '', error: false })
const passwordForm = reactive({ current: '', next: '', confirm: '', loading: false, message: '', error: false })

onMounted(async () => {
  try {
    profileImageUrl.value = await memberApi.getProfileImage()
  } catch {}
})

async function onImageChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  imageMsg.value = ''
  imageLoading.value = true
  try {
    profileImageUrl.value = await memberApi.uploadProfileImage(file)
    imageError.value = false
    imageMsg.value = '프로필 이미지가 변경되었습니다.'
  } catch (err) {
    imageError.value = true
    imageMsg.value = err.message || '이미지 업로드에 실패했습니다.'
  } finally {
    imageLoading.value = false
    e.target.value = ''
  }
}

async function removeImage() {
  imageMsg.value = ''
  imageLoading.value = true
  try {
    await memberApi.deleteProfileImage()
    profileImageUrl.value = null
    imageError.value = false
    imageMsg.value = '프로필 이미지가 삭제되었습니다.'
  } catch (err) {
    imageError.value = true
    imageMsg.value = err.message || '삭제에 실패했습니다.'
  } finally {
    imageLoading.value = false
  }
}

async function submitNickname() {
  nicknameForm.message = ''
  nicknameForm.loading = true
  try {
    await memberApi.updateNickname(nicknameForm.value)
    await auth.fetchMe()
    nicknameForm.value = ''
    nicknameForm.error = false
    nicknameForm.message = '닉네임이 변경되었습니다.'
  } catch (e) {
    nicknameForm.error = true
    nicknameForm.message = e.message || '닉네임 변경에 실패했습니다.'
  } finally {
    nicknameForm.loading = false
  }
}

async function submitPassword() {
  passwordForm.message = ''
  if (passwordForm.next !== passwordForm.confirm) {
    passwordForm.error = true
    passwordForm.message = '새 비밀번호가 일치하지 않습니다.'
    return
  }
  passwordForm.loading = true
  try {
    await memberApi.updatePassword(passwordForm.current, passwordForm.next)
    passwordForm.current = ''
    passwordForm.next = ''
    passwordForm.confirm = ''
    passwordForm.error = false
    passwordForm.message = '비밀번호가 변경되었습니다.'
  } catch (e) {
    passwordForm.error = true
    passwordForm.message = e.message || '비밀번호 변경에 실패했습니다.'
  } finally {
    passwordForm.loading = false
  }
}
</script>

<style scoped>
@import '@/assets/css/community.css';

.profile-layout {
  max-width: 560px;
  margin: 0 auto;
  padding: 0 16px 40px;
}
.profile-content {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

/* 프로필 이미지 */
.avatar-row {
  display: flex;
  align-items: center;
  gap: 20px;
}
.avatar-wrap {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid var(--color-border);
}
.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--purple-50, #f3f0ff);
  color: var(--purple-900, #3b0764);
  font-size: 2rem;
  font-weight: 700;
  text-transform: uppercase;
}
.avatar-actions {
  display: flex;
  gap: 8px;
}
.upload-label {
  cursor: pointer;
}
.hidden-input {
  display: none;
}
.danger-btn {
  background: var(--color-danger, #e53e3e) !important;
}

/* 현재 정보 */
.info-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background: var(--color-bg-subtle, #f7f8fa);
  border-radius: 8px;
}
.info-row {
  display: flex;
  gap: 12px;
  font-size: 0.95rem;
}
.info-label {
  width: 60px;
  color: var(--color-text-muted);
  flex-shrink: 0;
}
.info-value {
  color: var(--color-text);
  font-weight: 500;
}

/* 폼 공통 */
.edit-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.section-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-text);
}
.edit-form {
  display: flex;
  gap: 8px;
  align-items: center;
}
.edit-form.vertical {
  flex-direction: column;
  align-items: stretch;
}
.edit-input {
  flex: 1;
  padding: 9px 12px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  font-size: 0.9rem;
  background: var(--color-bg);
  color: var(--color-text);
}
.edit-btn {
  padding: 9px 18px;
  border: none;
  border-radius: 6px;
  background: var(--color-primary, #3b82f6);
  color: #fff;
  font-size: 0.9rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  text-align: center;
}
.edit-btn:disabled {
  opacity: 0.6;
  cursor: default;
}
.form-msg {
  font-size: 0.85rem;
  margin: 0;
}
.form-msg.success { color: var(--color-success, #16a34a); }
.form-msg.error   { color: var(--color-danger, #e53e3e); }
</style>
