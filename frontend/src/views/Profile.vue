<template>
  <div class="profile">
    <van-nav-bar title="我的" />

    <div class="user-card">
      <van-image
        v-if="userState.info?.icon"
        round
        width="60"
        height="60"
        :src="userState.info.icon"
      />
      <van-icon v-else name="contact" size="60" color="#c8c9cc" />
      <div class="meta">
        <p class="nick">{{ userState.info?.nickName || '未命名' }}</p>
        <van-tag
          :type="userState.info?.role === 1 ? 'primary' : 'success'"
          round
          color="rgba(255,255,255,0.22)"
          text-color="#ffffff"
        >
          {{ userState.info?.role === 1 ? '雇主' : '打工人' }}
        </van-tag>
      </div>
    </div>

    <!-- 每日签到 -->
    <div class="sign-card">
      <div class="sign-card__info">
        <van-icon name="award-o" size="28" color="#ff976a" />
        <div>
          <p class="sign-card__title">{{ signInfo.signed ? `已连续签到 ${signInfo.count} 天` : '每日签到' }}</p>
          <p class="sign-card__sub">{{ signInfo.signed ? '坚持打卡，好习惯' : '今天还没签到，快来打卡' }}</p>
        </div>
      </div>
      <van-button
        size="small"
        round
        :type="signInfo.signed ? 'default' : 'primary'"
        :disabled="signInfo.signed"
        @click="onSign"
      >
        {{ signInfo.signed ? '已签到' : '立即签到' }}
      </van-button>
    </div>

    <van-cell-group inset class="menu">
      <van-cell
        title="动态"
        icon="fire-o"
        is-link
        @click="$router.push('/feed')"
      />
      <van-cell
        title="发布动态"
        icon="edit"
        is-link
        @click="$router.push('/publish-blog')"
      />
      <van-cell
        title="我的动态"
        icon="notes-o"
        is-link
        @click="$router.push('/my-blogs')"
      />
      <van-cell
        v-if="userState.info?.role === 1"
        title="发布岗位"
        icon="plus"
        is-link
        @click="$router.push('/publish')"
      />
      <van-cell
        v-if="userState.info?.role === 1"
        title="审核报名"
        icon="todo-list-o"
        is-link
        @click="$router.push('/employer-applications')"
      />
      <van-cell
        title="我的报名"
        icon="orders-o"
        is-link
        @click="$router.push('/my-applications')"
      />
      <van-cell
        title="编辑资料"
        icon="setting-o"
        is-link
        @click="$router.push('/edit-profile')"
      />
    </van-cell-group>

    <div class="placeholder">
      <p class="sub">Step 8 实现：全链路联调验证</p>
    </div>

    <div class="logout">
      <van-button round block type="danger" plain @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { logout as apiLogout, sign, signCount } from '@/api/user'
import { clearUser, userState } from '@/stores/user'

const router = useRouter()

// 签到状态：signed 通过「连续签到天数 > 0」推导（后端从今天往前数连续位，>0 即今天已签）
const signInfo = reactive({ signed: false, count: 0 })

onMounted(loadSignCount)

async function loadSignCount() {
  try {
    const res = await signCount()
    const count = res.data || 0
    signInfo.count = count
    signInfo.signed = count > 0
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

async function onSign() {
  if (signInfo.signed) return
  try {
    await sign()
    showToast('签到成功')
    await loadSignCount()
  } catch (e) {
    // 失败提示已由 request.js Toast
  }
}

// 退出登录：先调后端删 token，再清本地状态，跳回登录页
async function onLogout() {
  try {
    await apiLogout()
  } catch (e) {
    // 后端退出失败不影响本地退出（token 本来就有过期时间）
  }
  clearUser()
  showToast('已退出登录')
  router.replace('/login')
}
</script>

<style scoped>
.profile {
  padding-bottom: 80px;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 12px;
  padding: 24px 18px;
  background: linear-gradient(135deg, var(--brand-primary-light), var(--brand-primary));
  border-radius: var(--radius-card);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}
.nick {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 6px;
}
.sign-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 12px;
  padding: 16px;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.sign-card__info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.sign-card__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}
.sign-card__sub {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-top: 4px;
}
.placeholder {
  padding: 40px 24px;
  text-align: center;
  color: #969799;
}
.placeholder p {
  margin-top: 12px;
  font-size: 15px;
}
.placeholder .sub {
  font-size: 13px;
  color: #c8c9cc;
}
.logout {
  margin: 24px 16px;
}
</style>
