import { describe, expect, it } from 'vitest'
import { isInAppBrowser } from './inAppBrowser'

/**
 * Цена ошибки здесь несимметрична: лишняя подсказка стоит одной строки текста,
 * пропущенная — гостевого аккаунта, в который человек больше не войдёт.
 * Поэтому проверяем и срабатывание, и отсутствие ложных на обычных браузерах —
 * второе важно, чтобы подсказка не превратилась в фон, который перестают читать.
 */
describe('isInAppBrowser', () => {
  const IN_APP = {
    Telegram: 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/119 Mobile Safari/537.36 Telegram-Android/10.2.0',
    WhatsApp: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 WhatsApp/2.23',
    Instagram: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605.1.15 Instagram 302.0.0.23.109',
    'Facebook iOS': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) AppleWebKit/605.1.15 [FBAN/FBIOS;FBAV/440.0]',
    VK: 'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/119 Mobile Safari/537.36 VKAndroidApp/8.30',
  }

  it.each(Object.entries(IN_APP))('распознаёт встроенный браузер: %s', (_name, ua) => {
    expect(isInAppBrowser(ua)).toBe(true)
  })

  const REGULAR = {
    'Chrome Android': 'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/119.0.0.0 Mobile Safari/537.36',
    'Safari iOS': 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Version/17.0 Mobile/15E148 Safari/604.1',
    'Firefox desktop': 'Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0',
    'Chrome desktop': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/119.0.0.0 Safari/537.36',
  }

  it.each(Object.entries(REGULAR))('не срабатывает на обычном браузере: %s', (_name, ua) => {
    expect(isInAppBrowser(ua)).toBe(false)
  })

  it('пустой User-Agent не считается встроенным браузером', () => {
    expect(isInAppBrowser('')).toBe(false)
  })
})
