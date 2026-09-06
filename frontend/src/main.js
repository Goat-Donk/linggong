import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import Vant from 'vant'
import 'vant/lib/index.css'
import './styles/theme.css'

const app = createApp(App)
app.use(router)
// 全量引入 Vant（教学项目，方便直接写 <van-xxx>，不做按需引入的额外配置）
app.use(Vant)
app.mount('#app')
