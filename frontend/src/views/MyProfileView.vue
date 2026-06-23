<template>
  <div class="profile-view">
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

        <!-- 회원 탈퇴 -->
        <section class="edit-section danger-zone">
          <h3 class="section-title danger-title">회원 탈퇴</h3>
          <p class="danger-desc">
            탈퇴하면 내 일정, 즐겨찾기, 좋아요가 영구 삭제되며 복구할 수 없습니다.<br />
            작성한 게시글·댓글은 <strong>탈퇴한 사용자</strong>로 표시되어 유지됩니다.
          </p>
          <template v-if="!withdrawForm.open">
            <button class="edit-btn withdraw-btn" @click="withdrawForm.open = true">탈퇴하기</button>
          </template>
          <template v-else>
            <div class="edit-form vertical">
              <p class="danger-warn">
                {{ isSocial
                  ? '소셜 계정은 비밀번호 확인 없이 즉시 탈퇴됩니다. 계속하시겠어요?'
                  : '계속하려면 현재 비밀번호를 입력하세요.' }}
              </p>
              <input
                v-if="!isSocial"
                v-model="withdrawForm.password"
                type="password"
                class="edit-input"
                placeholder="현재 비밀번호"
                autocomplete="current-password"
              />
              <div class="withdraw-actions">
                <button
                  class="edit-btn cancel-btn"
                  @click="withdrawForm.open = false; withdrawForm.password = ''; withdrawForm.message = ''"
                >
                  취소
                </button>
                <button
                  class="edit-btn withdraw-btn"
                  :disabled="withdrawForm.loading || (!isSocial && !withdrawForm.password)"
                  @click="submitWithdraw"
                >
                  {{ withdrawForm.loading ? '처리 중...' : '탈퇴 확인' }}
                </button>
              </div>
            </div>
            <p v-if="withdrawForm.message" class="form-msg error">{{ withdrawForm.message }}</p>
          </template>
        </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { memberApi } from '@/api/member'

const auth   = useAuthStore()
const router = useRouter()

// 소셜 전용 계정(카카오 등)은 비밀번호가 없어 탈퇴 시 비번 확인을 생략한다.
const isSocial = computed(() => !!auth.user?.socialProvider)

const profileImageUrl = ref(null)
const imageLoading    = ref(false)
const imageMsg        = ref('')
const imageError      = ref(false)

const nicknameForm = reactive({ value: '', loading: false, message: '', error: false })
const passwordForm = reactive({ current: '', next: '', confirm: '', loading: false, message: '', error: false })
const withdrawForm = reactive({ open: false, password: '', loading: false, message: '' })

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

async function submitWithdraw() {
  if (!isSocial.value && !withdrawForm.password) return
  withdrawForm.loading = true
  withdrawForm.message = ''
  try {
    await memberApi.withdraw(isSocial.value ? undefined : withdrawForm.password)
    await auth.logout()
    router.push('/')
  } catch (e) {
    withdrawForm.message = e.message || '탈퇴 처리에 실패했습니다.'
  } finally {
    withdrawForm.loading = false
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

.profile-view {
  max-width: 620px;
  margin: 0 auto;
}
.profile-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.profile-content .mypage-header {
  border-bottom: none;
  padding: 4px 0 0;
  margin-bottom: 0;
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
  padding: 18px 20px;
  background: var(--purple-50);
  border: 1px solid var(--purple-100);
  border-radius: var(--radius-xl);
}
.info-row {
  display: flex;
  gap: 12px;
  font-size: var(--text-base);
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

/* 폼 공통 — 섹션 카드 */
.edit-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: var(--bg-surface);
  border: 1px solid var(--gray-border);
  border-radius: var(--radius-2xl);
  box-shadow: var(--shadow-sm);
  padding: 22px 24px;
}
.section-title {
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--color-text);
  letter-spacing: -0.01em;
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
  padding: 10px 13px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  font-size: var(--text-base);
  background: var(--bg-page);
  color: var(--color-text);
  transition: border-color .14s, background .14s, box-shadow .14s;
}
.edit-input:focus {
  outline: none;
  border-color: var(--purple-900);
  background: var(--bg-surface);
  box-shadow: 0 0 0 3px rgba(83, 74, 183, .1);
}
.edit-btn {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-lg);
  background: var(--color-primary);
  color: #fff;
  font-size: var(--text-base);
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  text-align: center;
  transition: opacity .14s, transform .1s, box-shadow .14s;
  box-shadow: 0 2px 8px rgba(83, 74, 183, .18);
}
.edit-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(83, 74, 183, .26);
}
.edit-btn:disabled {
  opacity: 0.5;
  cursor: default;
  box-shadow: none;
}
.form-msg {
  font-size: var(--text-sm);
  margin: 0;
}
.form-msg.success { color: var(--color-success, #16a34a); }
.form-msg.error   { color: var(--color-danger, #e53e3e); }

/* 회원 탈퇴 영역 */
.danger-zone {
  margin-top: 8px;
  background: var(--color-danger-soft);
  border-color: #f0c9c4;
  box-shadow: none;
}
.danger-title {
  color: var(--color-danger);
}
.danger-desc {
  font-size: var(--text-sm);
  color: var(--color-text-muted);
  line-height: 1.6;
  margin: 0;
}
.danger-warn {
  font-size: var(--text-sm);
  color: var(--color-danger, #e53e3e);
  margin: 0;
}
.withdraw-btn {
  background: var(--color-danger, #e53e3e) !important;
}
.cancel-btn {
  background: var(--color-bg-subtle, #f1f5f9) !important;
  color: var(--color-text) !important;
  border: 1px solid var(--color-border) !important;
}
.withdraw-actions {
  display: flex;
  gap: 8px;
}
</style>
