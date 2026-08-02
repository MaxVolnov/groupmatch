import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import i18n from '@/i18n'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/store/auth'
import type { Language } from '@/types'

interface LanguageStore {
  language: Language
  setLanguage: (lang: Language) => void
}

/**
 * Сохраняет выбранный язык на сервере, чтобы им же уходили письма.
 * Оптимистично: UI переключается сразу, ответ не ждём и на ошибке не
 * откатываем — локальный выбор всё равно остаётся в localStorage.
 */
function syncLocaleToServer(language: Language) {
  if (!useAuthStore.getState().isAuthenticated) return
  void meApi.update({ locale: language }).catch(() => {
    // Сеть/сервер недоступны — язык уже переключён локально, повторим при
    // следующем переключении. Ронять переключатель из-за этого не за что.
  })
}

export const useLanguageStore = create<LanguageStore>()(
  persist(
    (set) => ({
      language: 'ru',
      setLanguage: (language) => {
        i18n.changeLanguage(language)
        set({ language })
        syncLocaleToServer(language)
      },
    }),
    { name: 'groupmatch-language' }
  )
)
