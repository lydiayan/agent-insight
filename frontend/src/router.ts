import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from './views/Dashboard.vue'
import CaseManagement from './views/CaseManagement.vue'
import Evaluation from './views/Evaluation.vue'
import OnlineQuality from './views/OnlineQuality.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/online-quality', name: 'online-quality', component: OnlineQuality },
    { path: '/cases', name: 'cases', component: CaseManagement },
    { path: '/eval', name: 'eval', component: Evaluation },
  ],
})

export default router
