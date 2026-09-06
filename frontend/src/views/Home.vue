<template>
  <div class="home">
    <van-nav-bar title="本地零工平台">
      <template #right>
        <van-icon name="user-o" size="20" @click="$router.push('/profile')" />
      </template>
    </van-nav-bar>
    <div class="placeholder">
      <van-icon name="notes-o" size="48" color="#c8c9cc" />
      <p>首页占位</p>
      <p class="sub">Step 3 实现：岗位列表 + 分类 + 附近搜索</p>

      <template v-if="userState.token">
        <p class="welcome">
          你好，{{ userState.info?.nickName || '零工人' }}
          <van-tag :type="userState.info?.role === 1 ? 'primary' : 'success'" round>
            {{ userState.info?.role === 1 ? '雇主' : '打工人' }}
          </van-tag>
        </p>
      </template>
      <van-button v-else type="primary" round block @click="$router.push('/login')">
        去登录
      </van-button>
    </div>
  </div>
</template>

<script setup>
import { userState } from '@/stores/user'
</script>

<style scoped>
.placeholder {
  padding: 60px 24px;
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
  margin-bottom: 24px;
}
.welcome {
  font-size: 15px;
  color: #323233;
  margin-bottom: 20px;
}
</style>
