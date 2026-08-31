/**
 * Куда вернуть человека после входа.
 *
 * Значение приходит из адресной строки (`/signin?next=…`), а туда попадает по
 * ссылке, которую кто-то прислал в мессенджере. Поэтому это не «параметр
 * навигации», а недоверенный ввод: без проверки `?next=http://evil.com` даёт
 * открытый редирект — заготовку для фишинга, где человек видит настоящий домен
 * GroupMatch, вводит пароль и уезжает на чужой сайт.
 *
 * Правило одно и намеренно узкое: принимаем только путь внутри приложения.
 */

/** Куда идти, если `next` не задан или не прошёл проверку. */
export const DEFAULT_NEXT = '/'

/**
 * Принимаем строку, начинающуюся с одного `/` и продолжающуюся чем угодно,
 * кроме второго слэша и обратного слэша.
 *
 * Почему именно так:
 * - `//evil.com` — protocol-relative URL, браузер уведёт на другой домен;
 * - `/\evil.com` — то же самое, часть браузеров нормализует `\` в `/`;
 * - `http://evil.com`, `javascript:alert(1)`, `data:…` — не начинаются со
 *   слэша и отсекаются первым же условием.
 */
const SAFE_PATH = /^\/(?![/\\]).*$/

export function safeNextPath(raw: string | null | undefined): string {
  if (!raw) return DEFAULT_NEXT
  const value = raw.trim()
  if (value === '') return DEFAULT_NEXT
  if (!SAFE_PATH.test(value)) return DEFAULT_NEXT
  return value
}

/**
 * Обратная операция: собрать `?next=` из текущего адреса, чтобы вернуть
 * человека туда, откуда его отправили логиниться.
 */
export function withNext(target: string, from: string): string {
  const safe = safeNextPath(from)
  if (safe === DEFAULT_NEXT) return target
  return `${target}?next=${encodeURIComponent(safe)}`
}
