import { describe, expect, it } from 'vitest'
import { DEFAULT_NEXT, safeNextPath, withNext } from './nextPath'

/**
 * Валидатор адреса возврата — единственное место, отделяющее «вернуть человека
 * к приглашению» от открытого редиректа. Значение приходит из ссылки, которую
 * прислали в мессенджере, поэтому проверять надо не на опечатки, а на злой
 * умысел.
 */
describe('safeNextPath', () => {
  it('принимает относительный путь внутри приложения', () => {
    expect(safeNextPath('/join/abc')).toBe('/join/abc')
    expect(safeNextPath('/groups/9f1c/settings')).toBe('/groups/9f1c/settings')
    expect(safeNextPath('/join/abc?from=tg#top')).toBe('/join/abc?from=tg#top')
  })

  it('отклоняет protocol-relative URL', () => {
    // Браузер уведёт на evil.com, хотя строка выглядит как путь.
    expect(safeNextPath('//evil.com')).toBe(DEFAULT_NEXT)
    expect(safeNextPath('//evil.com/join/abc')).toBe(DEFAULT_NEXT)
  })

  it('отклоняет абсолютный URL', () => {
    expect(safeNextPath('http://evil.com')).toBe(DEFAULT_NEXT)
    expect(safeNextPath('https://evil.com/join/abc')).toBe(DEFAULT_NEXT)
  })

  it('отклоняет javascript: и data:', () => {
    expect(safeNextPath('javascript:alert(1)')).toBe(DEFAULT_NEXT)
    expect(safeNextPath('data:text/html,<script>alert(1)</script>')).toBe(DEFAULT_NEXT)
  })

  /** Часть браузеров нормализует обратный слэш в прямой. */
  it('отклоняет обратный слэш сразу после первого', () => {
    expect(safeNextPath('/\\evil.com')).toBe(DEFAULT_NEXT)
  })

  it('отклоняет пустое, пробельное и отсутствующее значение', () => {
    expect(safeNextPath('')).toBe(DEFAULT_NEXT)
    expect(safeNextPath('   ')).toBe(DEFAULT_NEXT)
    expect(safeNextPath(null)).toBe(DEFAULT_NEXT)
    expect(safeNextPath(undefined)).toBe(DEFAULT_NEXT)
  })

  it('отклоняет путь без ведущего слэша', () => {
    expect(safeNextPath('join/abc')).toBe(DEFAULT_NEXT)
    expect(safeNextPath('evil.com')).toBe(DEFAULT_NEXT)
  })
})

describe('withNext', () => {
  it('добавляет параметр и кодирует значение', () => {
    expect(withNext('/signin', '/join/abc')).toBe('/signin?next=%2Fjoin%2Fabc')
  })

  it('не добавляет параметр для корня — возвращать туда и так дефолт', () => {
    expect(withNext('/signin', '/')).toBe('/signin')
  })

  it('не переносит небезопасный адрес дальше', () => {
    expect(withNext('/signin', '//evil.com')).toBe('/signin')
    expect(withNext('/signin', 'http://evil.com')).toBe('/signin')
  })
})
