<template>
  <router-view />
  <!-- 底部 TabBar：三个一级页共用，route 模式按当前路径自动高亮 -->
  <van-tabbar v-if="showTabbar" route>
    <van-tabbar-item replace to="/home" icon="home-o">首页</van-tabbar-item>
    <van-tabbar-item replace to="/feed" icon="fire-o">动态</van-tabbar-item>
    <van-tabbar-item replace to="/profile" icon="user-o">我的</van-tabbar-item>
  </van-tabbar>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
// 底部 TabBar 只在三个一级页显示；二级页（详情/发布/编辑等）用顶栏返回箭头
const showTabbar = computed(() => ['/home', '/feed', '/profile'].includes(route.path))
</script>

<style>
/* 全局基础样式：移动端 H5，重置边距 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Helvetica Neue', Helvetica,
    'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
  background-color: #f7f8fa;
  color: #323233;
}
</style>
