import { AxiosError, AxiosHeaders } from 'axios'
import { describe, expect, it } from 'vitest'
import { isNetworkFailure, resolveErrorLines, resolveErrorMessage } from './apiError'

/**
 * Проверяется главное: интерфейс не должен обвинять человека в том, чего он не
 * делал. При обрыве связи «Неверный email или пароль» — это не неточность, а
 * ложь, и стоила она обращения в поддержку и полдня поисков ошибки в пароле,
 * которого не было.
 */

/** Заглушка перевода: возвращает ключ, чтобы в ожиданиях был виден именно он. */
const t = (key: string, options?: Record<string, unknown>) =>
  options ? `${key}:${JSON.stringify(options)}` : key

/** Ошибка axios без ответа — так выглядят таймаут, обрыв, отказ CORS. */
function networkError(code = 'ERR_NETWORK'): AxiosError {
  return new AxiosError('Network Error', code)
}

function timeoutError(): AxiosError {
  return new AxiosError('timeout of 10000ms exceeded', 'ECONNABORTED')
}

function serverError(status: number, data: unknown, headers: Record<string, string> = {}): AxiosError {
  const error = new AxiosError('Request failed')
  error.response = {
    status,
    statusText: '',
    data,
    headers,
    config: { headers: new AxiosHeaders() },
  }
  return error
}

describe('① ответа нет — говорим про связь, а не про пароль', () => {
  it('таймаут не превращается в ошибку учётных данных', () => {
    const lines = resolveErrorLines(timeoutError(), t, 'auth.invalidCredentials')
    expect(lines).toEqual(['errors.cannotConnect'])
    expect(lines[0]).not.toBe('auth.invalidCredentials')
  })

  it('обрыв соединения не превращается в отказ регистрации', () => {
    expect(resolveErrorLines(networkError(), t, 'auth.registrationFailed'))
      .toEqual(['errors.cannotConnect'])
  })

  /** Фолбэк экрана не должен пробиваться наружу ни при каком коде ошибки axios. */
  it('фолбэк экрана игнорируется при любом сетевом коде', () => {
    for (const code of ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT', 'ERR_CANCELED', undefined]) {
      expect(resolveErrorMessage(networkError(code), t, 'auth.invalidResetLink'))
        .toBe('errors.cannotConnect')
    }
  })

  it('isNetworkFailure отличает отсутствие ответа от ответа', () => {
    expect(isNetworkFailure(timeoutError())).toBe(true)
    expect(isNetworkFailure(networkError())).toBe(true)
    expect(isNetworkFailure(serverError(401, { message: 'Invalid email or password' }))).toBe(false)
    expect(isNetworkFailure(new Error('что угодно'))).toBe(false)
  })
})

describe('② сервер ответил — показываем то, что он сказал', () => {
  it('401 с телом — сообщение сервера, а не про связь', () => {
    const lines = resolveErrorLines(
      serverError(401, { code: 'invalid_credentials', message: 'Invalid email or password' }),
      t, 'auth.invalidCredentials')
    expect(lines).toEqual(['Invalid email or password'])
  })

  it('409 — сообщение сервера', () => {
    expect(resolveErrorMessage(
      serverError(409, { code: 'email_already_exists', message: 'Email already registered' }), t))
      .toBe('Email already registered')
  })

  /** Ответ есть, но сказать нечего — тогда и только тогда фолбэк экрана. */
  it('пустое тело при живом ответе — фолбэк экрана', () => {
    expect(resolveErrorLines(serverError(400, {}), t, 'auth.invalidResetLink'))
      .toEqual(['auth.invalidResetLink'])
  })

  it('404 и 5xx без тела — свои сообщения', () => {
    expect(resolveErrorLines(serverError(404, null), t)).toEqual(['errors.notFound'])
    expect(resolveErrorLines(serverError(500, null), t)).toEqual(['errors.server'])
    expect(resolveErrorLines(serverError(503, null), t)).toEqual(['errors.server'])
  })
})

describe('③ подробности валидации доходят до человека', () => {
  /**
   * Ровно тот случай, ради которого пункт и делался: раньше человек видел
   * «Invalid input» и не узнавал, что пароль короткий.
   */
  it('короткий пароль — видно требование, а не общее «Invalid input»', () => {
    const lines = resolveErrorLines(serverError(400, {
      code: 'validation_failed',
      message: 'Invalid input',
      details: { password: 'Password must be at least 8 characters' },
    }), t, 'auth.registrationFailed')

    expect(lines).toEqual(['Invalid input', 'Password must be at least 8 characters'])
  })

  it('подробностей несколько — показаны все, а не первая', () => {
    const lines = resolveErrorLines(serverError(400, {
      code: 'validation_failed',
      message: 'Invalid input',
      details: {
        password: 'Password must be at least 8 characters',
        displayName: 'Display name must be between 2 and 50 characters',
        email: 'Email must be valid',
      },
    }), t)

    expect(lines).toHaveLength(4)
    expect(lines.slice(1)).toEqual([
      // порядок по имени поля: у HashMap на сервере он произвольный
      'Display name must be between 2 and 50 characters',
      'Email must be valid',
      'Password must be at least 8 characters',
    ])
  })

  it('порядок не зависит от порядка ключей в ответе', () => {
    const first = resolveErrorLines(serverError(400, {
      message: 'Invalid input', details: { b: 'вторая', a: 'первая' },
    }), t)
    const second = resolveErrorLines(serverError(400, {
      message: 'Invalid input', details: { a: 'первая', b: 'вторая' },
    }), t)
    expect(first).toEqual(second)
  })

  /** ④ Без details показывается message — и ничего лишнего. */
  it('без details — только message', () => {
    expect(resolveErrorLines(serverError(400, {
      code: 'bad_request', message: 'Cannot transfer ownership to yourself', details: null,
    }), t)).toEqual(['Cannot transfer ownership to yourself'])
  })

  it('пустые и мусорные details не добавляют пустых строк', () => {
    expect(resolveErrorLines(serverError(400, {
      message: 'Invalid input', details: { a: '   ', b: '' },
    }), t)).toEqual(['Invalid input'])

    expect(resolveErrorLines(serverError(400, {
      message: 'Invalid input', details: 'не карта, а строка',
    }), t)).toEqual(['Invalid input'])
  })

  it('однострочная форма склеивает переводами строки', () => {
    expect(resolveErrorMessage(serverError(400, {
      message: 'Invalid input', details: { password: 'too short' },
    }), t)).toBe('Invalid input\ntoo short')
  })
})

describe('⑤ 429 остаётся сообщением про лимит', () => {
  it('с Retry-After — минуты до повтора', () => {
    expect(resolveErrorLines(
      serverError(429, { message: 'Too many requests' }, { 'retry-after': '180' }), t))
      .toEqual(['errors.tooManyRequestsMinutes:{"minutes":3}'])
  })

  it('без Retry-After — общее сообщение про лимит', () => {
    expect(resolveErrorLines(serverError(429, { message: 'Too many requests' }), t))
      .toEqual(['errors.tooManyRequestsGeneric'])
  })

  /** Лимит не должен подменяться ни сетевым сообщением, ни фолбэком экрана. */
  it('не подменяется фолбэком экрана', () => {
    const lines = resolveErrorLines(
      serverError(429, { message: 'Too many requests' }), t, 'auth.invalidCredentials')
    expect(lines[0]).not.toBe('auth.invalidCredentials')
    expect(lines[0]).not.toBe('errors.cannotConnect')
  })
})

describe('прочее', () => {
  it('обычная ошибка не от axios — своё сообщение', () => {
    expect(resolveErrorLines(new Error('boom'), t)).toEqual(['boom'])
  })

  it('вообще не ошибка — общий фолбэк', () => {
    expect(resolveErrorLines('строка', t)).toEqual(['errors.somethingWrong'])
    expect(resolveErrorLines(undefined, t)).toEqual(['errors.somethingWrong'])
  })
})
