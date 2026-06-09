<template>
  <div class="tiptap-wrapper" :class="{ uploading }">
    <!-- 툴바 -->
    <div class="tiptap-toolbar">
      <button type="button" class="tool-btn" :class="{ active: editor?.isActive('bold') }"
              title="굵게 (Ctrl+B)" @click="editor?.chain().focus().toggleBold().run()">
        <strong>B</strong>
      </button>
      <button type="button" class="tool-btn" :class="{ active: editor?.isActive('italic') }"
              title="기울임 (Ctrl+I)" @click="editor?.chain().focus().toggleItalic().run()">
        <em>I</em>
      </button>
      <button type="button" class="tool-btn" :class="{ active: editor?.isActive('bulletList') }"
              title="목록" @click="editor?.chain().focus().toggleBulletList().run()">
        ≡
      </button>
      <span class="tool-divider"></span>
      <button type="button" class="tool-btn tool-image" title="이미지 삽입"
              :disabled="uploading" @click="fileInput?.click()">
        {{ uploading ? '업로드 중…' : '🖼 이미지' }}
      </button>
    </div>

    <!-- 에디터 본문 -->
    <editor-content :editor="editor" class="tiptap-content" />

    <!-- 숨겨진 파일 입력 -->
    <input ref="fileInput" type="file" accept="image/jpeg,image/png,image/gif,image/webp"
           style="display:none" @change="handleImageUpload" />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'

const props = defineProps({
  modelValue: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

const fileInput = ref(null)
const uploading = ref(false)

const editor = useEditor({
  content: props.modelValue,
  extensions: [
    StarterKit,
    Image.configure({ inline: false, allowBase64: false }),
  ],
  onUpdate: ({ editor }) => {
    emit('update:modelValue', editor.getHTML())
  },
})

// 외부에서 modelValue가 바뀔 때 에디터와 동기화 (초기화 등)
watch(() => props.modelValue, (val) => {
  if (!editor.value) return
  if (val !== editor.value.getHTML()) {
    editor.value.commands.setContent(val || '', false)
  }
})

onBeforeUnmount(() => editor.value?.destroy())

// ── 이미지 업로드 ──────────────────────────────────────────
async function handleImageUpload(e) {
  const file = e.target.files[0]
  if (!file) return
  e.target.value = '' // 같은 파일 재선택 허용

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)

    // http 래퍼는 JSON 전용이므로 fetch 직접 사용
    const res = await fetch('/api/images/upload', {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
    const json = await res.json()

    if (!res.ok || !json?.success) {
      throw new Error(json?.message || '업로드 실패')
    }
    // 에디터 커서 위치에 이미지 삽입
    editor.value?.chain().focus().setImage({ src: json.data }).run()
  } catch (err) {
    alert(err.message || '이미지 업로드에 실패했습니다.')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.tiptap-wrapper {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.tiptap-wrapper.uploading {
  opacity: 0.8;
  pointer-events: none;
}

/* 툴바 */
.tiptap-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px 8px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 30px;
  height: 30px;
  padding: 0 6px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: #475569;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.tool-btn:hover { background: #e2e8f0; }
.tool-btn.active { background: #dbeafe; color: #2563eb; }
.tool-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.tool-image {
  padding: 0 10px;
  font-size: 13px;
  gap: 4px;
}

.tool-divider {
  width: 1px;
  height: 18px;
  background: #e2e8f0;
  margin: 0 4px;
}

/* 에디터 본문 */
.tiptap-content {
  min-height: 180px;
  max-height: 480px;
  overflow-y: auto;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.7;
  color: #1e293b;
}

/* ProseMirror 기본 초기화 */
:deep(.ProseMirror) {
  outline: none;
  min-height: 160px;
}

:deep(.ProseMirror p) { margin: 0 0 8px; }
:deep(.ProseMirror p:last-child) { margin-bottom: 0; }
:deep(.ProseMirror strong) { font-weight: 700; }
:deep(.ProseMirror em) { font-style: italic; }
:deep(.ProseMirror ul) { padding-left: 20px; margin: 8px 0; }
:deep(.ProseMirror li) { margin-bottom: 4px; }

:deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  border-radius: 6px;
  margin: 8px 0;
  display: block;
}

/* 이미지 선택 시 강조 */
:deep(.ProseMirror img.ProseMirror-selectednode) {
  outline: 2px solid #3b82f6;
}

/* placeholder */
:deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  color: #94a3b8;
  pointer-events: none;
  float: left;
  height: 0;
}
</style>
