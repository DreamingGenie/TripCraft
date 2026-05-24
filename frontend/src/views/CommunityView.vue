<template>
  <main id="main">
  <div id="community-layout">

    <!-- 메인 콘텐츠 -->
    <div id="community-content">

      <!-- 목록 뷰 -->
      <div v-if="!selectedPost" id="view-list">
        <div class="list-toolbar">
          <span class="board-title">여행 이야기</span>
          <div class="sort-group">
            <button v-for="s in sorts" :key="s.value"
                    class="sort-btn" :class="{ active: sort === s.value }"
                    @click="sort = s.value">{{ s.label }}</button>
          </div>
          <button class="btn-primary" @click="writeModal = true">글쓰기</button>
        </div>

        <div class="posts-list">
          <div v-for="post in sortedPosts" :key="post.id"
               class="post-card" @click="selectedPost = post">
            <div class="post-card-left">
              <div class="post-meta">
                <div class="avatar avatar-sm">{{ post.author[0] }}</div>
                <span class="post-author">{{ post.author }}</span>
                <span class="meta-dot">·</span>
                <span class="post-date">{{ post.date }}</span>
              </div>
              <p class="post-title">{{ post.title }}</p>
              <p class="post-excerpt">{{ post.excerpt }}</p>
              <div class="post-tags">
                <span v-for="tag in post.tags" :key="tag" class="tag" :class="tagClass(tag)">{{ tag }}</span>
                <span class="tag tag-region">{{ post.region }}</span>
              </div>
            </div>
            <div class="post-card-right">
              <div class="post-stats">
                <span class="stat"><span class="stat-icon">♥</span> {{ post.likes }}</span>
                <span class="stat"><span class="stat-icon">💬</span> {{ post.comments.length }}</span>
                <span class="stat"><span class="stat-icon">👁</span> {{ post.views }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="pagination">
          <button class="page-btn" disabled>‹</button>
          <button class="page-btn active">1</button>
          <button class="page-btn">2</button>
          <button class="page-btn">3</button>
          <button class="page-btn">›</button>
        </div>
      </div>

      <!-- 상세 뷰 -->
      <div v-else id="view-detail">
        <button class="back-btn" @click="selectedPost = null">← 목록으로</button>

        <article class="detail-article">
          <h2 class="detail-title">{{ selectedPost.title }}</h2>
          <div class="detail-meta">
            <div class="avatar avatar-sm">{{ selectedPost.author[0] }}</div>
            <div class="detail-meta-info">
              <span class="post-author">{{ selectedPost.author }}</span>
              <span class="detail-date">{{ selectedPost.date }}</span>
            </div>
            <div class="detail-actions">
              <button class="btn-ghost btn-sm">수정</button>
              <button class="btn-ghost btn-sm btn-danger">삭제</button>
            </div>
          </div>
          <div class="detail-body">{{ selectedPost.body }}</div>
          <div class="like-section">
            <button class="like-btn" :class="{ liked: selectedPost.liked }" @click="toggleLike(selectedPost)">
              <span class="like-icon">♥</span> {{ selectedPost.likes }}
            </button>
          </div>
        </article>

        <!-- 댓글 -->
        <section class="comment-section">
          <p class="comment-title">댓글 <span class="comment-count">{{ selectedPost.comments.length }}</span></p>
          <div class="comment-write">
            <div class="avatar avatar-sm">나</div>
            <div class="comment-input-wrap">
              <textarea class="comment-input" v-model="commentText" rows="2" placeholder="댓글을 입력하세요"></textarea>
              <div class="comment-write-footer">
                <span class="comment-hint">Enter로 줄 바꿈</span>
                <button class="btn-primary btn-sm" @click="submitComment">등록</button>
              </div>
            </div>
          </div>
          <div class="comment-list">
            <div v-for="c in selectedPost.comments" :key="c.id" class="comment-item">
              <div class="avatar avatar-sm">{{ c.author[0] }}</div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-author">{{ c.author }}</span>
                  <span v-if="c.isAuthor" class="comment-badge-author">작성자</span>
                  <span class="comment-date">{{ c.date }}</span>
                </div>
                <p class="comment-text">{{ c.text }}</p>
                <div class="comment-footer">
                  <button class="comment-like-btn" :class="{ liked: c.liked }"
                          @click="c.liked = !c.liked; c.likes += c.liked ? 1 : -1">
                    ♥ {{ c.likes }}
                  </button>
                  <button class="reply-toggle-btn" @click="c.replyOpen = !c.replyOpen">답글</button>
                </div>
                <div v-if="c.replyOpen" class="reply-write" style="display:flex">
                  <div class="avatar avatar-sm">나</div>
                  <div class="comment-input-wrap">
                    <textarea class="comment-input" rows="2" placeholder="답글을 입력하세요"></textarea>
                    <div class="comment-write-footer">
                      <span></span>
                      <button class="btn-primary btn-sm" @click="c.replyOpen = false; toast.show('답글이 등록됐어요.')">등록</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div><!-- /community-content -->

    <!-- 사이드바 -->
    <aside id="community-sidebar">
      <div>
        <span class="sidebar-title">📢 공지사항</span>
        <ul class="notice-list">
          <li v-for="n in notices" :key="n.id" class="notice-item">
            <span class="notice-badge">공지</span>
            <span class="notice-text">{{ n.text }}</span>
          </li>
        </ul>
      </div>
      <div>
        <span class="sidebar-title">🔥 이번 주 인기</span>
        <ul class="hot-list">
          <li v-for="(h, i) in hotPosts" :key="h.id" class="hot-item">
            <span class="hot-rank">{{ i + 1 }}</span>
            <span class="hot-title">{{ h.title }}</span>
          </li>
        </ul>
      </div>
    </aside>

  </div>
  </main>

  <!-- 글쓰기 모달 -->
  <div v-if="writeModal" id="modal-write" class="modal-overlay" @click.self="writeModal = false">
    <div class="write-modal-box">
      <div class="modal-header">
        <span class="modal-title">새 글 작성</span>
        <button class="modal-close" @click="writeModal = false">✕</button>
      </div>
      <div class="modal-body">
        <label class="field-label"><span class="required">*</span> 제목</label>
        <input class="field-input" v-model="newPost.title" placeholder="제목을 입력하세요" style="margin-bottom:12px" />
        <label class="field-label"><span class="required">*</span> 내용</label>
        <textarea class="field-textarea" v-model="newPost.body" rows="8" placeholder="내용을 입력하세요"></textarea>
      </div>
      <div class="modal-footer">
        <button class="btn-ghost" @click="writeModal = false">취소</button>
        <button class="btn-primary" @click="submitPost">등록</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { useToastStore } from '@/stores/toast'

const toast = useToastStore()

const sort = ref('latest')
const sorts = [{ label: '최신순', value: 'latest' }, { label: '인기순', value: 'popular' }, { label: '댓글순', value: 'comments' }]

const selectedPost = ref(null)
const commentText = ref('')
const writeModal = ref(false)
const newPost = reactive({ title: '', body: '' })

const posts = ref([
  {
    id: 1, author: '여행러버', date: '2시간 전', likes: 24, views: 142, liked: false,
    title: '경복궁에서 한복 체험 후기 — 정말 추천해요!',
    excerpt: '처음으로 한복을 입고 경복궁을 거닐었는데, 완전히 다른 느낌이었어요. 특히 오후 늦게 가면 조명이 켜지면서 분위기가 최고입니다.',
    body: '처음으로 한복을 입고 경복궁을 거닐었는데, 완전히 다른 느낌이었어요. 특히 오후 늦게 가면 조명이 켜지면서 분위기가 최고입니다.\n\n한복 대여는 경복궁 서문 쪽에 여러 곳이 있고, 보통 2~3시간에 2만원 정도입니다. 헤어 세팅도 포함해주는 곳이 많아서 사진이 훨씬 잘 나와요.',
    tags: ['관광지'], region: '서울',
    comments: [
      { id: 1, author: '제주러버', isAuthor: false, date: '1시간 전', text: '저도 다음에 가보려고 했는데 좋은 정보 감사해요!', likes: 3, liked: false, replyOpen: false },
      { id: 2, author: '여행러버', isAuthor: true, date: '30분 전', text: '꼭 가보세요! 주말보다 평일이 덜 붐벼요.', likes: 1, liked: false, replyOpen: false },
    ],
  },
  {
    id: 2, author: '맛집탐험가', date: '5시간 전', likes: 18, views: 89, liked: false,
    title: '광장시장 빈대떡 골목 완전 정복',
    excerpt: '광장시장 빈대떡 골목을 처음부터 끝까지 먹어봤습니다. 가장 맛있는 집은 바로...',
    body: '광장시장 빈대떡 골목을 처음부터 끝까지 먹어봤습니다. 가장 맛있는 집은 입구에서 세 번째 집이에요. 줄이 가장 길지만 그만한 가치가 있습니다.',
    tags: ['음식점'], region: '서울',
    comments: [
      { id: 1, author: '서울토박이', isAuthor: false, date: '4시간 전', text: '저도 광장시장 자주 가는데, 빈대떡은 역시 거기죠!', likes: 5, liked: false, replyOpen: false },
    ],
  },
  {
    id: 3, author: '불국사팬', date: '1일 전', likes: 45, views: 312, liked: false,
    title: '불국사 새벽 개장, 이렇게 아름다울 줄이야',
    excerpt: '새벽 5시에 개장하는 불국사를 방문했습니다. 안개 속의 불국사는 정말 몽환적이었어요.',
    body: '새벽 5시에 개장하는 불국사를 방문했습니다. 안개 속의 불국사는 정말 몽환적이었어요. 이른 시간이라 관광객도 거의 없어서 사진도 마음껏 찍을 수 있었습니다.',
    tags: ['관광지', '문화시설'], region: '경주',
    comments: [],
  },
])

const sortedPosts = computed(() => {
  const p = [...posts.value]
  if (sort.value === 'popular') return p.sort((a, b) => b.likes - a.likes)
  if (sort.value === 'comments') return p.sort((a, b) => b.comments.length - a.comments.length)
  return p
})

const notices = ref([
  { id: 1, text: '커뮤니티 이용 규칙 안내' },
  { id: 2, text: '5월 여행 사진 공모전 개최' },
  { id: 3, text: '시스템 점검 안내 (5/25 새벽 2시)' },
])

const hotPosts = computed(() => [...posts.value].sort((a, b) => b.likes - a.likes).slice(0, 5))

function tagClass(tag) {
  return { '관광지': 'tag-attraction', '음식점': 'tag-food', '문화시설': 'tag-culture', '숙박': 'tag-stay', '레포츠': 'tag-leisure' }[tag] || ''
}

function toggleLike(post) {
  post.liked = !post.liked
  post.likes += post.liked ? 1 : -1
}

function submitComment() {
  if (!commentText.value.trim()) return
  selectedPost.value.comments.push({
    id: Date.now(), author: '나', isAuthor: false,
    date: '방금', text: commentText.value, likes: 0, liked: false, replyOpen: false,
  })
  commentText.value = ''
  toast.show('댓글이 등록됐어요.')
}

function submitPost() {
  if (!newPost.title.trim() || !newPost.body.trim()) {
    toast.show('제목과 내용을 입력해주세요.')
    return
  }
  posts.value.unshift({
    id: Date.now(), author: '나', date: '방금', likes: 0, views: 0, liked: false,
    title: newPost.title, excerpt: newPost.body.slice(0, 60),
    body: newPost.body, tags: [], region: '',
    comments: [],
  })
  newPost.title = newPost.body = ''
  writeModal.value = false
  toast.show('게시글이 등록됐어요.')
}
</script>
