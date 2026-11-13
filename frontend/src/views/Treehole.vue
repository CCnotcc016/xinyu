<template>
  <div class="page">
    <van-nav-bar title="匿名树洞" />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loading"
        v-model:error="error"
        :finished="finished"
        finished-text="没有更多了"
        error-text="加载失败，点击重新加载"
        @load="onLoad"
      >
        <div class="xinyu-cards">
          <div v-for="p in posts" :key="p.id" class="post" @click="router.push(`/treehole/${p.id}`)">
            <div class="post-head">
              <!-- 匿名帖没有 userId（后端刻意置空），只有实名的可以点进主页 -->
              <span
                class="nick"
                :class="{ 'nick--link': p.userId != null }"
                @click.stop="p.userId != null && router.push(`/u/${p.userId}`)"
              >{{ p.nickname }}</span>
              <span class="time">{{ dateLabel(p.createdAt) }}</span>
            </div>
            <p class="content">{{ p.content }}</p>
            <div v-if="p.images && p.images.length" class="thumbs">
              <img
                v-for="(img, i) in p.images.slice(0, 3)"
                :key="img"
                :src="mediaUrl(img)"
                class="thumb"
                @click.stop="preview(p.images, i)"
              />
              <span v-if="p.images.length > 3" class="thumb-more">+{{ p.images.length - 3 }}</span>
            </div>
            <div class="post-foot">
              <span :class="{ liked: p.liked }" @click.stop="toggleLike(p)">
                {{ p.liked ? '❤️' : '🤍' }} {{ p.likeCount }}
              </span>
              <span>💬 {{ p.commentCount }}</span>
            </div>
          </div>
        </div>
      </van-list>
    </van-pull-refresh>

    <CrisisDialog v-model:show="showCrisis" />

    <div class="fab">
      <van-button type="primary" round icon="plus" @click="showCreate = true">发帖</van-button>
    </div>

    <van-popup v-model:show="showCreate" position="bottom" round>
      <div class="create">
        <h3>向树洞说点什么</h3>
        <van-field
          v-model="form.content"
          type="textarea"
          rows="5"
          autosize
          maxlength="2000"
          show-word-limit
          placeholder="这里没有人知道你是谁…"
        />
        <div class="uploader">
          <van-uploader
            v-model="fileList"
            :max-count="9"
            accept="image/*"
            :after-read="afterRead"
          />
          <p class="uploader__tip">最多 9 张，单张不超过 5MB</p>
        </div>
        <van-cell title="匿名发布">
          <template #right-icon>
            <van-switch v-model="form.isAnonymous" size="20" />
          </template>
        </van-cell>
        <div class="create-btns">
          <van-button round block type="primary" :loading="posting" @click="onCreate">发布</van-button>
        </div>
      </div>
    </van-popup>

  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { showImagePreview, showToast } from 'vant'
import { listPosts, createPost, likePost, unlikePost, uploadPostImage } from '../api/treehole'
import CrisisDialog from '../components/CrisisDialog.vue'
import { mediaUrl } from '../utils/backend'

const router = useRouter()
const posts = ref([])
const page = ref(1)
const loading = ref(false)
const finished = ref(false)
const error = ref(false)
const refreshing = ref(false)
const showCreate = ref(false)
const posting = ref(false)
const form = ref({ content: '', isAnonymous: true })
const showCrisis = ref(false)
// van-uploader 的文件列表：本地预览负责「看得见」，服务端 URL 负责真正提交
const fileList = ref([])

function dateLabel(dt) {
  return dt ? dt.replace('T', ' ').slice(5, 16) : ''
}

async function onLoad() {
  try {
    const data = await listPosts({ page: page.value, size: 10 })
    posts.value.push(...data.records)
    if (data.records.length < 10 || posts.value.length >= data.total) {
      finished.value = true
    } else {
      page.value += 1
    }
  } catch {
    // 进入 error 态：van-list 显示「点击重新加载」并暂停自动加载，避免失败时无限刷接口
    error.value = true
  } finally {
    // 必须在 finally 里复位：请求失败时若停在 true，van-list 会永久卡死无法重试
    loading.value = false
  }
}

async function onRefresh() {
  page.value = 1
  const data = await listPosts({ page: 1, size: 10 })
  posts.value = data.records
  finished.value = data.records.length < 10
  error.value = false
  refreshing.value = false
}

async function toggleLike(p) {
  if (p.liked) {
    const res = await unlikePost(p.id)
    p.liked = res.liked
    p.likeCount = res.likeCount
  } else {
    const res = await likePost(p.id)
    p.liked = res.liked
    p.likeCount = res.likeCount
  }
}

function preview(images, index) {
  showImagePreview({ images: images.map(mediaUrl), startPosition: index })
}

async function afterRead(item) {
  // 一次选多张时 van 传数组，统一成数组处理
  for (const file of Array.isArray(item) ? item : [item]) {
    file.status = 'uploading'
    file.message = '上传中'
    try {
      const res = await uploadPostImage(file.file)
      // 换成服务端地址：本地 blob 预览在发帖后会失效，后面还要拿它当提交值
      file.url = res.url
      file.status = 'done'
      file.message = ''
    } catch {
      file.status = 'failed'
      file.message = '上传失败'
    }
  }
}

/** 只提交上传成功的本站图片地址 */
function uploadedImages() {
  return fileList.value
    .filter((f) => f.status === 'done' && f.url && f.url.startsWith('/uploads/'))
    .map((f) => f.url)
}

async function onCreate() {
  if (!form.value.content.trim()) {
    showToast('说点什么吧')
    return
  }
  if (fileList.value.some((f) => f.status === 'uploading')) {
    showToast('图片还在上传，稍等一下')
    return
  }
  posting.value = true
  try {
    const res = await createPost({ ...form.value, images: uploadedImages() })
    showCreate.value = false
    form.value.content = ''
    fileList.value = []
    onRefresh()
    if (res && res.crisisSupport) {
      showCrisis.value = true
    } else {
      showToast('发布成功')
    }
  } finally {
    posting.value = false
  }
}
</script>

<style scoped>
.post {
  border-radius: 16px;
  padding: 14px 16px;
}
/* 手机上是列表间距，PC 排成网格后由 grid gap 负责，margin 会顶歪对齐 */
@media (max-width: 1023px) {
  .post {
    margin: 8px 0;
  }
}
.post-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.nick {
  font-weight: 600;
  color: var(--xinyu-accent);
}
.nick--link {
  /* 能点进主页的昵称给个下划线暗示，匿名的只是普通文字 */
  text-decoration: underline;
  text-decoration-style: dotted;
  cursor: pointer;
}
.time {
  color: var(--van-text-color-2);
  font-size: 12px;
}
.content {
  margin: 10px 0;
  line-height: 1.6;
}
.post-foot {
  display: flex;
  gap: 20px;
  color: var(--van-text-color-2);
  font-size: 14px;
}
.liked {
  color: #ee0a24;
}
@media (min-width: 1024px) {
  /* 网格本身已经有 gap，卡片自带的外边距会叠成双倍间距 */
  .xinyu-cards .post {
    margin: 0;
  }
}
.fab {
  position: fixed;
  /* 宽屏时外壳居中，按钮要贴着外壳内侧，不能贴着浏览器右边 */
  right: max(20px, calc((100vw - var(--xinyu-shell)) / 2 + 20px));
  bottom: 80px;
  z-index: 9;
}
.create {
  padding: 20px 16px 24px;
}
.create h3 {
  margin-bottom: 12px;
}
.create-btns {
  margin-top: 16px;
}
.thumbs {
  display: flex;
  gap: 6px;
  margin: 8px 0;
  align-items: center;
}
.thumb {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--van-gray-2);
}
.thumb-more {
  color: var(--van-text-color-2);
  font-size: 13px;
}
.uploader {
  margin: 4px 16px 8px;
}
.uploader__tip {
  margin-top: 6px;
  color: var(--van-text-color-2);
  font-size: 12px;
}
</style>
