<template>
  <div class="feed">
    <van-nav-bar title="动态" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多动态了"
      @load="onLoad"
    >
      <div v-for="blog in blogs" :key="blog.id" class="blog-card">
        <!-- 作者：头像 + 昵称 + 时间 + 关注按钮 -->
        <div class="blog-card__head">
          <van-image v-if="blog.icon" round width="40" height="40" :src="blog.icon" />
          <van-icon v-else name="contact" size="40" color="#c8c9cc" />
          <div class="blog-card__meta">
            <p class="blog-card__name">{{ blog.nickName || '用户' }}</p>
            <p class="blog-card__time">{{ formatRelativeTime(blog.createTime) }}</p>
          </div>
          <van-button
            size="mini"
            round
            :type="blog.isFollow ? 'default' : 'primary'"
            :plain="!blog.isFollow"
            @click="onFollow(blog)"
          >
            {{ blog.isFollow ? '已关注' : '+ 关注' }}
          </van-button>
        </div>

        <p v-if="blog.title" class="blog-card__title">{{ blog.title }}</p>
        <p class="blog-card__content">{{ blog.content }}</p>

        <div v-if="imagesOf(blog).length" class="blog-card__imgs">
          <van-image
            v-for="(img, i) in imagesOf(blog)"
            :key="i"
            class="blog-card__img"
            :src="img"
            fit="cover"
          />
        </div>

        <div class="blog-card__foot">
          <van-icon
            :name="blog.isLike ? 'good-job' : 'good-job-o'"
            :color="blog.isLike ? '#ee0a24' : '#969799'"
            size="20"
            @click="onLike(blog)"
          />
          <span class="blog-card__like-count" :style="{ color: blog.isLike ? '#ee0a24' : '#969799' }">
            {{ blog.liked || 0 }}
          </span>
        </div>
      </div>

      <van-empty v-if="finished && blogs.length === 0" description="还没有关注动态，去关注更多打工人吧" />
    </van-list>

    <!-- 发布动态入口 -->
    <div class="fab" @click="$router.push('/publish-blog')">
      <van-icon name="edit" size="20" color="#fff" />
      <span class="fab__text">发布</span>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import { queryBlogOfFollow, likeBlog } from '@/api/blog'
import { follow } from '@/api/follow'
import { formatRelativeTime } from '@/utils/format'

const blogs = ref([])
const loading = ref(false)
const finished = ref(false)

// 滚动分页游标：首次 lastId=null、offset=0；后续回传上一页返回的 minTime / offset
let lastId = null
let offset = 0
let requestSeq = 0
const pageSize = 5

// 图片字段是逗号分隔字符串，拆成数组供九宫格展示
function imagesOf(blog) {
  return blog.images ? blog.images.split(',').filter(Boolean) : []
}

async function onLoad() {
  const seq = ++requestSeq
  try {
    const res = await queryBlogOfFollow({ lastId, offset, pageSize })
    if (seq !== requestSeq) return // 旧请求，丢弃
    const data = res.data // ScrollResult { list, minTime, offset }
    if (!data || !data.list || data.list.length === 0) {
      finished.value = true
      return
    }
    blogs.value.push(...data.list)
    lastId = data.minTime
    offset = data.offset
    if (data.list.length < pageSize) {
      finished.value = true
    }
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

// 关注 / 取关：切换后本地更新状态，避免整页刷新
async function onFollow(blog) {
  const target = !blog.isFollow
  try {
    await follow(blog.userId, target)
    blog.isFollow = target
    showToast(target ? '已关注' : '已取消关注')
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

// 点赞 / 取消点赞：幂等切换，成功后本地更新点赞数与状态
async function onLike(blog) {
  try {
    await likeBlog(blog.id)
    blog.isLike = !blog.isLike
    blog.liked = (blog.liked || 0) + (blog.isLike ? 1 : -1)
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}
</script>

<style scoped>
.feed {
  padding-bottom: 80px;
}
.fab {
  position: fixed;
  right: 16px;
  bottom: 70px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 18px;
  background: var(--brand-primary);
  color: #fff;
  border-radius: 24px;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.4);
  z-index: 10;
}
.fab__text {
  font-size: 14px;
  font-weight: 600;
}
.blog-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.blog-card__head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.blog-card__meta {
  flex: 1;
  min-width: 0;
}
.blog-card__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.blog-card__time {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 2px;
}
.blog-card__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-top: 12px;
}
.blog-card__content {
  font-size: 15px;
  color: var(--text-primary);
  line-height: 1.6;
  margin-top: 8px;
  word-break: break-word;
  white-space: pre-wrap;
}
.blog-card__imgs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
}
.blog-card__img {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 4px;
  overflow: hidden;
}
.blog-card__foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
}
.blog-card__like-count {
  font-size: 14px;
}
</style>
