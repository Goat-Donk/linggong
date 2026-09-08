<template>
  <div class="favorite-list">
    <van-nav-bar title="我的收藏" left-arrow @click-left="$router.back()" />

    <van-list
      v-model:loading="loading"
      :finished="finished"
      finished-text="没有更多了"
      @load="onLoad"
    >
      <div
        v-for="fav in favorites"
        :key="fav.id"
        class="fav-card"
        @click="$router.push(`/job/${fav.jobId}`)"
      >
        <div class="fav-card__head">
          <span class="fav-card__name">{{ fav.jobName || '岗位已删除' }}</span>
          <van-tag v-if="fav.status !== 0" type="danger">已下架</van-tag>
        </div>
        <div class="fav-card__row">
          <van-icon name="gold-coin-o" />
          <span>{{ formatSalary(fav.salary) }}</span>
        </div>
        <div class="fav-card__row">
          <van-icon name="location-o" />
          <span class="fav-card__address">{{ fav.address || '地址待定' }}</span>
          <span class="fav-card__time">{{ formatDateTime(fav.createTime) }}</span>
        </div>
      </div>

      <van-empty v-if="finished && favorites.length === 0" description="还没有收藏岗位" />
    </van-list>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { getMyFavorites } from '@/api/favorite'
import { formatSalary, formatDateTime } from '@/utils/format'

const favorites = ref([])
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

async function onLoad() {
  try {
    const res = await getMyFavorites(page.value, pageSize)
    const list = res.data || []
    favorites.value.push(...list)
    const total = res.total
    // 到底判断：不满一页，或有 total 且已累计到 total
    const noMore = list.length < pageSize || (total != null && favorites.value.length >= total)
    if (noMore) {
      finished.value = true
    } else {
      page.value++
    }
  } catch (e) {
    // 失败提示已由 request.js Toast；结束加载避免 van-list 无限重试
    finished.value = true
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.favorite-list {
  min-height: 100vh;
  padding-bottom: 24px;
}
.fav-card {
  margin: 12px 12px 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.fav-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.fav-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}
.fav-card__row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}
.fav-card__address {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fav-card__time {
  margin-left: auto;
  color: var(--text-tertiary);
}
</style>
