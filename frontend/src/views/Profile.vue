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

    <van-cell-group inset class="menu">
      <van-cell icon="bell" is-link @click="$router.push('/notifications')">
        <template #title>
          消息通知
          <van-badge v-if="unreadCount > 0" :content="unreadCount > 99 ? '99+' : unreadCount" />
        </template>
      </van-cell>
      <van-cell icon="chat-o" is-link @click="$router.push('/chat')">
        <template #title>
          我的消息
          <van-badge v-if="chatUnreadCount > 0" :content="chatUnreadCount > 99 ? '99+' : chatUnreadCount" />
        </template>
      </van-cell>
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
        title="我的钱包"
        icon="gold-coin-o"
        is-link
        @click="$router.push('/wallet')"
      />
      <van-cell
        title="信用详情"
        icon="medal-o"
        is-link
        @click="$router.push('/credit')"
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
        title="我的岗位"
        icon="orders-o"
        is-link
        @click="$router.push('/my-jobs')"
      />
      <van-cell
        v-if="userState.info?.role === 1"
        title="审核报名"
        icon="todo-list-o"
        is-link
        @click="$router.push('/employer-applications')"
      />
      <van-cell
        v-if="userState.info?.role === 1"
        title="考勤核销"
        icon="clock-o"
        is-link
        @click="$router.push('/attendance/manage')"
      />
      <van-cell
        v-if="userState.info?.role === 0"
        title="我的报名"
        icon="orders-o"
        is-link
        @click="$router.push('/my-applications')"
      />
      <van-cell
        v-if="userState.info?.role === 0"
        title="我的收藏"
        icon="star-o"
        is-link
        @click="$router.push('/favorites')"
      />
      <van-cell
        v-if="userState.info?.role === 0"
        title="求职登记"
        icon="contact"
        is-link
        @click="$router.push('/worker-profile/edit')"
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { logout as apiLogout } from '@/api/user'
import { getUnreadCount } from '@/api/notification'
import { getUnreadCount as getChatUnreadCount } from '@/api/chat'
import { clearUser, userState } from '@/stores/user'

const router = useRouter()
const unreadCount = ref(0)
const chatUnreadCount = ref(0)

// 进入「我的」页拉取未读通知数 + 未读消息数，显示在菜单红点
onMounted(async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = res.data ?? 0
  } catch (e) {
    // 拉不到未读数不影响页面
  }
  try {
    const res = await getChatUnreadCount()
    chatUnreadCount.value = res.data ?? 0
  } catch (e) {
    // 拉不到未读数不影响页面
  }
})

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
