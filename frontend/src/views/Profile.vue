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
        <van-tag :type="userState.info?.role === 1 ? 'primary' : 'success'" round>
          {{ userState.info?.role === 1 ? '雇主' : '打工人' }}
        </van-tag>
      </div>
    </div>

    <div class="placeholder">
      <p>个人中心占位</p>
      <p class="sub">Step 7 实现：资料编辑 / 我的报名 / 我的动态 / 签到</p>
    </div>

    <div class="logout">
      <van-button round block type="danger" plain @click="onLogout">退出登录</van-button>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { logout as apiLogout } from '@/api/user'
import { clearUser, userState } from '@/stores/user'

const router = useRouter()

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
.user-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 16px;
  padding: 20px 16px;
  background: #fff;
  border-radius: 8px;
}
.nick {
  font-size: 17px;
  font-weight: 600;
  color: #323233;
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
