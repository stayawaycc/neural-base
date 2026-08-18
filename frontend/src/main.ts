import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import 'vuetify/styles'
import '@/styles/global.scss'
import App from './App.vue'
import router from './router'

// Vuetify 4 起组件需显式注册(不再默认全量注册);主题对齐 NeuralBase 设计系统(电光蓝 + 警告橙)
const vuetify = createVuetify({
  components,
  theme: {
    defaultTheme: 'light',
    themes: {
      light: {
        dark: false,
        colors: {
          primary: '#0087a0',
          secondary: '#d95f00',
          background: '#e8edf3',
          surface: '#ffffff',
        },
      },
      dark: {
        dark: true,
        colors: {
          primary: '#00f0ff',
          secondary: '#ff6b00',
          background: '#050508',
          surface: '#0f1522',
        },
      },
    },
  },
})

const app = createApp(App)
app.use(createPinia())
app.use(vuetify)
app.use(router)
app.mount('#app')
