import { describe, expect, it, vi } from 'vitest'
import { attachAuthorization, type AuthPort } from './axios'

/**
 * Подстановка `Authorization` в исходящий запрос.
 *
 * Проверяется здесь одна вещь: протухший токен не должен уезжать на сервер.
 * Раньше он уезжал, ловил 401, и только после этого интерцептор ответа
 * обновлял его и повторял запрос. Данные в итоге приходили — но каждые
 * пятнадцать минут страница группы выдавала три красных 401 в консоли, и
 * отличить их от настоящей поломки авторизации было нечем.
 */

function token(expiresInSeconds: number): string {
  const payload = { sub: 'u1', exp: Math.floor(Date.now() / 1000) + expiresInSeconds }
  const b64 = Buffer.from(JSON.stringify(payload)).toString('base64url')
  return `header.${b64}.signature`
}

function port(over: Partial<AuthPort> & { token?: string | null } = {}) {
  const refresh = vi.fn(async () => token(900))
  return {
    getAccessToken: () => (over.token === undefined ? token(900) : over.token),
    refresh,
    ...over,
  } as AuthPort & { refresh: typeof refresh }
}

const request = (url: string) => ({ url, headers: {} as Record<string, unknown> })

describe('attachAuthorization', () => {
  it('живой токен уходит как есть, обновление не запускается', async () => {
    const auth = port()
    const req = request('/groups/abc')

    await attachAuthorization(req, auth)

    expect(req.headers.Authorization).toMatch(/^Bearer /)
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  /** Тот самый случай, что давал три 401 подряд на странице группы. */
  it('протухший токен обновляется ДО отправки, а не после 401', async () => {
    const fresh = token(900)
    const auth = port({ token: token(-60), refresh: vi.fn(async () => fresh) })
    const req = request('/groups/abc/availability/my')

    await attachAuthorization(req, auth)

    expect(auth.refresh).toHaveBeenCalledTimes(1)
    expect(req.headers.Authorization).toBe(`Bearer ${fresh}`)
  })

  /**
   * Запас перед истечением. Токен, живущий ещё десять секунд, до сервера может
   * не долететь живым — особенно если часы браузера спешат.
   */
  it('токен, догорающий в пределах запаса, тоже обновляется', async () => {
    const auth = port({ token: token(10) })
    await attachAuthorization(request('/groups/abc'), auth)
    expect(auth.refresh).toHaveBeenCalledTimes(1)
  })

  it('токен, живущий дольше запаса, не обновляется', async () => {
    const auth = port({ token: token(120) })
    await attachAuthorization(request('/groups/abc'), auth)
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  /**
   * На /auth/** заголовок не ставится вовсе: refresh с протухшим Authorization
   * — лишний повод серверу ответить 401 на запрос, который и должен ходить без
   * токена.
   */
  it('на /auth/ токен не ставится и обновление не запускается', async () => {
    const auth = port({ token: token(-60) })
    const req = request('/auth/refresh')

    await attachAuthorization(req, auth)

    expect(req.headers.Authorization).toBeUndefined()
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  it('токена нет вовсе — заголовка нет, обновлять нечего', async () => {
    const auth = port({ token: null })
    const req = request('/groups/abc')

    await attachAuthorization(req, auth)

    expect(req.headers.Authorization).toBeUndefined()
    expect(auth.refresh).not.toHaveBeenCalled()
  })

  /**
   * Обновление упало — запрос всё равно уходит со старым токеном и получает
   * 401, который разберёт интерцептор ответа. Проактивное обновление снимает
   * штатный случай, но не отменяет аварийный путь.
   */
  it('падение обновления не роняет запрос', async () => {
    const dead = token(-60)
    const auth = port({ token: dead, refresh: vi.fn(async () => { throw new Error('нет сети') }) })
    const req = request('/groups/abc')

    await expect(attachAuthorization(req, auth)).resolves.toBeUndefined()
    expect(req.headers.Authorization).toBe(`Bearer ${dead}`)
  })

  /**
   * Три параллельных запроса страницы группы обязаны дождаться одного и того
   * же обновления, а не разъехаться: два с новым токеном, один со старым.
   */
  it('параллельные запросы получают один и тот же обновлённый токен', async () => {
    const fresh = token(900)
    let calls = 0
    let shared: Promise<string> | null = null
    const auth: AuthPort = {
      getAccessToken: () => token(-60),
      // Мьютекс здесь имитирует тот, что живёт в сторе.
      refresh: () => (shared ??= (async () => { calls++; return fresh })()),
    }

    const reqs = ['/groups/a', '/notifications', '/groups/a/availability/my'].map(request)
    await Promise.all(reqs.map((r) => attachAuthorization(r, auth)))

    expect(calls).toBe(1)
    for (const r of reqs) expect(r.headers.Authorization).toBe(`Bearer ${fresh}`)
  })
})
