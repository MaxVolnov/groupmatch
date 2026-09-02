/**
 * Разбор JWT на стороне клиента.
 *
 * Подпись здесь не проверяется и проверяться не должна: решение о доступе
 * принимает сервер, а фронтенду содержимое токена нужно только чтобы не
 * посылать заведомо протухший запрос и знать имя и план для интерфейса.
 */

/** Полезная нагрузка токена. Пустой объект, если разобрать не удалось. */
export function decodeJwt(token: string): Record<string, unknown> {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return {}
  }
}

/**
 * Запас перед истечением. Тридцати секунд хватает и на дорогу запроса до
 * сервера, и на расхождение часов браузера с сервером — а расходятся они
 * регулярно и в обе стороны.
 */
export const EXPIRY_SKEW_SECONDS = 30

/**
 * Токен уже протух или протухнет в ближайшие {@link EXPIRY_SKEW_SECONDS}.
 *
 * Токен без разбираемого `exp` протухшим **не** считается: мы про него ничего
 * не знаем, и отправить его серверу — единственный способ выяснить правду.
 * Ответить здесь «протух» значило бы уйти в обновление на каждом запросе.
 */
export function isTokenExpiring(token: string, nowMs: number = Date.now()): boolean {
  const exp = decodeJwt(token).exp
  if (typeof exp !== 'number') return false
  return exp * 1000 - nowMs <= EXPIRY_SKEW_SECONDS * 1000
}
