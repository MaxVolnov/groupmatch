import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import i18n from '@/i18n'

interface LanguageStore {
  language: 'ru' | 'en'
  setLanguage: (lang: 'ru' | 'en') => void
}

export const useLanguageStore = create<LanguageStore>()(
  persist(
    (set) => ({
      language: 'ru',
      setLanguage: (language) => {
        i18n.changeLanguage(language)
        set({ language })
      },
    }),
    { name: 'groupmatch-language' }
  )
)
