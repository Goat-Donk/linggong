<template>
  <div class="profile-view">
    <van-nav-bar title="求职主页" left-arrow @click-left="$router.back()" />

    <!-- 加载失败 -->
    <van-empty v-if="loadFailed" description="用户不存在或加载失败" />

    <template v-else-if="data">
      <!-- 用户信息卡 -->
      <div class="user-card">
        <van-image v-if="data.icon" round width="64" height="64" :src="data.icon" />
        <van-icon v-else name="contact" size="64" color="#ffffff" />
        <div class="user-meta">
          <div class="user-line">
            <span class="nick">{{ data.nickName || '未命名' }}</span>
            <van-tag
              :type="data.role === 1 ? 'primary' : 'success'"
              round
              color="rgba(255,255,255,0.22)"
              text-color="#ffffff"
            >
              {{ data.role === 1 ? '雇主' : '打工人' }}
            </van-tag>
            <span v-if="data.isSelf" class="self-tag">我</span>
          </div>
          <p class="bio">
            {{ bioParts }}
          </p>
          <p class="phone">📞 {{ data.phone || '未留手机号' }}</p>
        </div>
      </div>

      <!-- 登记内容 或 空态 -->
      <div v-if="data.hasProfile" class="content">
        <div class="card">
          <div class="card__row">
            <span class="card__label">求职意向</span>
            <span class="card__value title">{{ data.title || '未填写' }}</span>
          </div>

          <div class="card__row">
            <span class="card__label">期望岗位</span>
            <div class="tags">
              <van-tag
                v-for="n in data.categoryNames"
                :key="n"
                type="primary"
                plain
                class="tag"
              >
                {{ n }}
              </van-tag>
              <span v-if="!data.categoryNames || data.categoryNames.length === 0" class="muted">
                未选择
              </span>
            </div>
          </div>

          <div class="card__row">
            <span class="card__label">期望日薪</span>
            <span class="card__value">{{ salaryText }}</span>
          </div>

          <div class="card__row">
            <span class="card__label">可出勤时段</span>
            <div class="tags">
              <van-tag
                v-for="t in data.workTime"
                :key="t"
                type="warning"
                plain
                class="tag"
              >
                {{ t }}
              </van-tag>
              <span v-if="!data.workTime || data.workTime.length === 0" class="muted">未填写</span>
            </div>
          </div>

          <div v-if="data.location" class="card__row">
            <span class="card__label">常驻区域</span>
            <span class="card__value">{{ data.location }}</span>
          </div>

          <div class="card__row">
            <span class="card__label">技能</span>
            <div class="tags">
              <van-tag
                v-for="s in data.skillTags"
                :key="s"
                type="default"
                plain
                class="tag"
              >
                #{{ s }}
              </van-tag>
              <span v-if="!data.skillTags || data.skillTags.length === 0" class="muted">未填写</span>
            </div>
          </div>

          <div v-if="data.introduce" class="card__row">
            <span class="card__label">自我介绍</span>
            <span class="card__value">{{ data.introduce }}</span>
          </div>
        </div>

        <p v-if="data.updateTime" class="updated">更新于 {{ formatDateTime(data.updateTime) }}</p>
      </div>

      <van-empty v-else description="TA 还没有填写求职登记" class="empty">
        <van-button v-if="data.isSelf" type="primary" round size="small" @click="goEdit">
          去填写
        </van-button>
      </van-empty>

      <div v-if="data.isSelf" class="edit-btn">
        <van-button round block type="primary" @click="goEdit">
          {{ data.hasProfile ? '编辑求职登记' : '填写求职登记' }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getWorkerProfile } from '@/api/workerProfile'
import { formatDateTime, formatGender } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const data = ref(null)
const loadFailed = ref(false)

// 个人简介行：年龄/性别 + 简介（credit 展示用评价里没有，简介可能为空）
const bioParts = computed(() => {
  if (!data.value) return ''
  const parts = []
  if (data.value.age != null) parts.push(`${data.value.age}岁`)
  parts.push(formatGender(data.value.gender))
  return parts.join(' · ')
})

const salaryText = computed(() => {
  if (!data.value) return ''
  const min = data.value.salaryMin
  const max = data.value.salaryMax
  if (min == null && max == null) return '面议'
  if (min != null && max != null) return `¥${min} ~ ¥${max}/天`
  return `¥${min ?? max}/天`
})

async function load() {
  const id = route.params.id
  try {
    const res = await getWorkerProfile(id)
    data.value = res.data
  } catch (e) {
    loadFailed.value = true
  }
}
load()

function goEdit() {
  router.push('/worker-profile/edit')
}
</script>

<style scoped>
.profile-view {
  min-height: 100vh;
  padding-bottom: 32px;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 12px;
  padding: 20px 18px;
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary-deep));
  border-radius: var(--radius-card);
  box-shadow: 0 4px 12px rgba(29, 78, 216, 0.25);
}
.user-meta {
  flex: 1;
  min-width: 0;
}
.user-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.nick {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}
.self-tag {
  font-size: 11px;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  padding: 1px 6px;
}
.bio {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 6px;
}
.phone {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.95);
  margin-top: 4px;
}
.content {
  margin: 12px;
}
.card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: 4px 16px;
}
.card__row {
  display: flex;
  padding: 12px 0;
  gap: 12px;
}
.card__row + .card__row {
  border-top: 1px solid var(--border-color);
}
.card__label {
  width: 72px;
  flex-shrink: 0;
  font-size: 14px;
  color: var(--text-tertiary);
}
.card__value {
  flex: 1;
  font-size: 14px;
  color: var(--text-primary);
  word-break: break-all;
}
.card__value.title {
  font-weight: 600;
}
.tags {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.tag {
  font-size: 12px;
}
.muted {
  font-size: 13px;
  color: var(--text-tertiary);
}
.updated {
  margin: 10px 4px 0;
  font-size: 12px;
  color: var(--text-tertiary);
}
.empty {
  margin-top: 20px;
}
.edit-btn {
  margin: 24px 16px;
}
</style>
