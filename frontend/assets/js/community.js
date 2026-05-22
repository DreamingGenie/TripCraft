/* TripCraft — community.js */

function openDetail(postId) {
  document.getElementById('view-list').style.display = 'none';
  document.getElementById('view-detail').style.display = 'block';
  document.getElementById('community-content').scrollTop = 0;
}

function closeDetail() {
  document.getElementById('view-detail').style.display = 'none';
  document.getElementById('view-list').style.display = 'block';
}

function changeSort(btn, type) {
  document.querySelectorAll('.sort-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
}

function toggleLike(btn) {
  const isLiked = btn.classList.toggle('liked');
  const countEl = btn.querySelector('.like-count');
  countEl.textContent = isLiked
    ? parseInt(countEl.textContent) + 1
    : parseInt(countEl.textContent) - 1;
}

function toggleCommentLike(btn) {
  const isLiked = btn.classList.toggle('liked');
  const match = btn.textContent.match(/\d+/);
  if (!match) return;
  const count = parseInt(match[0]);
  btn.textContent = `♥ ${isLiked ? count + 1 : count - 1}`;
}

function toggleReply(commentId) {
  const el = document.getElementById(`reply-write-${commentId}`);
  el.style.display = el.style.display === 'none' ? 'flex' : 'none';
  if (el.style.display === 'flex') el.querySelector('textarea').focus();
}

function submitComment() {
  const textarea = document.querySelector('.comment-write .comment-input');
  if (!textarea.value.trim()) return;
  showToast('댓글이 등록됐어요.');
  textarea.value = '';
}

function openWriteModal() {
  document.getElementById('modal-write').style.display = 'flex';
}

function closeWriteModal(e) {
  if (e && e.target !== document.getElementById('modal-write')) return;
  document.getElementById('modal-write').style.display = 'none';
}

function submitPost() {
  const title = document.querySelector('.write-modal-box .field-input').value.trim();
  const content = document.querySelector('.write-modal-box .field-textarea').value.trim();
  if (!title || !content) { showToast('제목과 내용을 입력해주세요.'); return; }
  showToast('게시글이 등록됐어요.');
  document.getElementById('modal-write').style.display = 'none';
}
