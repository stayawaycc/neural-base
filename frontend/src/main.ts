import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import 'vuetify/styles'
import '@/styles/global.scss'
import App from './App.vue'
import router from './router'

// Vuetify 4 起组件需显式注册(不再默认全量注册);主题对齐 NeuralBase 设计系统(霓虹青主色)
const vuetify = createVuetify({
  components,
  theme: {
    defaultTheme: 'light',
    themes: {
      light: {
        dark: false,
        colors: {
          primary: '#0891b2',
          secondary: '#7c3aed',
          background: '#f5f6f8',
          surface: '#ffffff',
        },
      },
      dark: {
        dark: true,
        colors: {
          primary: '#22d3ee',
          secondary: '#a78bfa',
          background: '#09090b',
          surface: '#15151f',
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
