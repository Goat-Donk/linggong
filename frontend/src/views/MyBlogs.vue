<template>
  <div class="my-blogs">
    <van-nav-bar title="我的动态" left-arrow @click-left="$router.back()" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div v-for="blog in blogs" :key="blog.id" class="blog-item">
        <p v-if="blog.title" class="blog-item__title">{{ blog.title }}</p>
        <p class="blog-item__content">{{ blog.content }}</p>
        <div v-if="imagesOf(blog).length" class="blog-item__imgs">
          <van-image
            v-for="(img, i) in imagesOf(blog)"
            :key="i"
            class="blog-item__img"
            :src="img"
            fit="cover"
          />
        </div>
        <div class="blog-item__foot">
          <span class="blog-item__time">{{ formatRelativeTime(blog.createTime) }}</span>
          <span class="blog-item__like">
            <van-icon name="good-job-o" /> {{ blog.liked || 0 }}
          </span>
        </div>
      </div>

      <van-empty v-if="finished && blogs.length === 0" description="还没有发布过动态" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getMyBlogs } from '@/api/blog'
import { formatRelativeTime } from '@/utils/format'

const blogs = ref([])
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const pageSize = 10
let requestSeq = 0

function imagesOf(blog) {
  return blog.images ? blog.images.split(',').filter(Boolean) : []
}

async function onLoad() {
  const seq = ++requestSeq
  try {
    const res = await getMyBlogs(page.value, pageSize)
    if (seq !== requestSeq) return
    const list = res.data || []
    blogs.value.push(...list)
    const total = res.total
    if (list.length < pageSize || (total != null && blogs.value.length >= total)) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    // 失败提示已由 request.js Toast
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}
</script>

<style scoped>
.my-blogs {
  padding-bottom: 40px;
}
.blog-item {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: #fff;
  border-radius: 8px;
}
.blog-item__title {
  font-size: 16px;
  font-weight: 600;
  color: #323233;
}
.blog-item__content {
  font-size: 15px;
  color: #323233;
  line-height: 1.6;
  margin-top: 6px;
  word-break: break-word;
  white-space: pre-wrap;
}
.blog-item__imgs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
}
.blog-item__img {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 4px;
  overflow: hidden;
}
.blog-item__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: #969799;
}
.blog-item__like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
</style>
