import { describe, expect, it } from 'vitest'
import { api, LONG_OPERATION_TIMEOUT_MS, REQUEST_TIMEOUT_MS } from './axios'
import { availabilityApi } from './availability'

/**
 * Таймаут запросов.
 *
 * Своего таймаута не было: запрос висел около тридцати секунд без единого
 * признака жизни, и всё это время человек не понимал, что происходит.
 *
 * Проверяется не значение константы, а то, что оно доезжает до конкретного
 * запроса. Для этого подменяется адаптер axios — он получает готовый конфиг,
 * уже собранный из умолчаний экземпляра и параметров вызова, то есть ровно то,
 * с чем пошёл бы настоящий запрос.
 */

/** Перехватывает конфиг запроса и отдаёт пустой успешный ответ. */
async function captureConfig(run: () => Promise<unknown>): Promise<{ timeout?: number; url?: string }> {
  const original = api.defaults.adapter
  let captured: { timeout?: number; url?: string } = {}
  api.defaults.adapter = async (config) => {
    captured = { timeout: config.timeout, url: config.url }
    return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
  }
  try {
    await run()
  } finally {
    api.defaults.adapter = original
  }
  return captured
}

describe('обычные запросы', () => {
  it('таймаут задан и равен десяти секундам', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(10_000)
    expect(api.defaults.timeout).toBe(REQUEST_TIMEOUT_MS)
  })

  it('доезжает до конкретного запроса', async () => {
    const config = await captureConfig(() => availabilityApi.mySlots('g1'))
    expect(config.timeout).toBe(REQUEST_TIMEOUT_MS)
  })

  it('обычное создание слота — тоже общий таймаут', async () => {
    const config = await captureConfig(() =>
      availabilityApi.addSlot('g1', { startsAt: '2026-09-07T10:00:00Z', endsAt: '2026-09-07T11:00:00Z' }))
    expect(config.timeout).toBe(REQUEST_TIMEOUT_MS)
  })
})

describe('операции, где ложный таймаут дороже ожидания', () => {
  /**
   * Дело не в скорости: по замерам серия на максимальные 200 слотов создаётся
   * за 0,18 с. Дело в том, что операция не идемпотентна — повтор после ложного
   * «нет связи» создал бы вторую серию.
   */
  it('создание серии — продлённый таймаут', async () => {
    const config = await captureConfig(() =>
      availabilityApi.addSeries('g1', {
        startDate: '2026-09-07', endDate: '2026-10-07',
        daysOfWeek: ['MONDAY'], startTime: '19:00', endTime: '20:00', timeZone: 'UTC',
      }))
    expect(config.timeout).toBe(LONG_OPERATION_TIMEOUT_MS)
  })

  it('перенос времени серии — продлённый таймаут', async () => {
    const config = await captureConfig(() =>
      availabilityApi.retimeSeries('s1', { startTime: '19:00', endTime: '20:00', timeZone: 'UTC' }))
    expect(config.timeout).toBe(LONG_OPERATION_TIMEOUT_MS)
  })

  it('массовая очистка — продлённый таймаут, и тело запроса не потеряно', async () => {
    const config = await captureConfig(() =>
      availabilityApi.bulkClear('g1', {
        daysOfWeek: ['MONDAY'], startTime: '19:00', endTime: '20:00',
        fromDate: '2026-09-07', toDate: '2026-10-07', timeZone: 'UTC', dryRun: true,
      }))
    expect(config.timeout).toBe(LONG_OPERATION_TIMEOUT_MS)
    expect(config.url).toContain('/availability/bulk')
  })

  it('продлённый больше общего, но не бесконечный', () => {
    expect(LONG_OPERATION_TIMEOUT_MS).toBeGreaterThan(REQUEST_TIMEOUT_MS)
    expect(LONG_OPERATION_TIMEOUT_MS).toBe(30_000)
  })
})
