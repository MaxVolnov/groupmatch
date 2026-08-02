import { afterEach, describe, expect, it } from 'vitest'
import { headerHtml, isAuthenticated } from './siteChrome'

/**
 * Статические страницы не тянут Zustand, поэтому читают ключ persist напрямую.
 * Тест фиксирует формат ключа: если он поменяется, шапка молча перестанет
 * узнавать авторизованного пользователя.
 */
function setAuthStorage(value: unknown) {
  const store = new Map<string, string>()
  if (value !== undefined) store.set('groupmatch-auth', JSON.stringify(value))
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: (k: string) => store.get(k) ?? null,
      setItem: (k: string, v: string) => store.set(k, v),
      removeItem: (k: string) => store.delete(k),
    },
  })
}

function setRawAuthStorage(raw: string) {
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: { getItem: () => raw },
  })
}

afterEach(() => {
  Reflect.deleteProperty(globalThis, 'localStorage')
})

describe('isAuthenticated', () => {
  it('распознаёт живую сессию', () => {
    setAuthStorage({ state: { isAuthenticated: true, refreshToken: 'rt-123' }, version: 0 })
    expect(isAuthenticated()).toBe(true)
  })

  it('гость с refresh-токеном тоже авторизован', () => {
    setAuthStorage({ state: { isAuthenticated: true, isGuest: true, refreshToken: 'rt-9' }, version: 0 })
    expect(isAuthenticated()).toBe(true)
  })

  it('нет ключа — аноним', () => {
    setAuthStorage(undefined)
    expect(isAuthenticated()).toBe(false)
  })

  it('после logout — аноним', () => {
    setAuthStorage({ state: { isAuthenticated: false, refreshToken: null }, version: 0 })
    expect(isAuthenticated()).toBe(false)
  })

  it('флаг без refresh-токена не считается сессией', () => {
    setAuthStorage({ state: { isAuthenticated: true, refreshToken: '' }, version: 0 })
    expect(isAuthenticated()).toBe(false)
  })

  it('битый JSON не роняет страницу', () => {
    setRawAuthStorage('{not json')
    expect(isAuthenticated()).toBe(false)
  })

  it('отсутствие localStorage не роняет страницу', () => {
    Reflect.deleteProperty(globalThis, 'localStorage')
    expect(isAuthenticated()).toBe(false)
  })
})

describe('headerHtml', () => {
  it('анониму предлагает войти и зарегистрироваться', () => {
    const html = headerHtml(false)
    expect(html).toContain('Войти')
    expect(html).toContain('Начать бесплатно')
    expect(html).not.toContain('Открыть приложение')
  })

  it('авторизованному — только вход в приложение', () => {
    const html = headerHtml(true)
    expect(html).toContain('Открыть приложение')
    expect(html).not.toContain('Войти')
    expect(html).not.toContain('Начать бесплатно')
  })

  it('вордмарк с пометкой beta есть в обоих состояниях', () => {
    for (const html of [headerHtml(true), headerHtml(false)]) {
      expect(html).toContain('GroupMatch')
      expect(html).toContain('beta')
    }
  })
})
