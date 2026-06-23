import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'

import './assets/css/variables.css'
import './assets/css/common.css'
import './assets/css/auth.css'
import './assets/css/explore.css'
import './assets/css/plan.css'
import './assets/css/schedule.css'
import './assets/css/community.css'
import './assets/css/calendar.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
