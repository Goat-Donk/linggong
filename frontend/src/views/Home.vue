<template>
  <div class="home">
    <van-nav-bar title="本地零工平台" />
    <div class="placeholder">
      <van-icon name="notes-o" size="48" color="#c8c9cc" />
      <p>首页占位</p>
      <p class="sub">Step 3 实现：岗位列表 + 分类 + 附近搜索</p>
      <van-button type="primary" round block @click="$router.push('/login')">
        去登录
      </van-button>
    </div>
  </div>
</template>

<script setup>
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
</style>
