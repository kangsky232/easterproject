import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { initTheme } from './theme'
import './style.css'

initTheme()

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
