import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { Button } from './Button'
import { Modal } from './Modal'

/**
 * Подписка на календарь группы.
 *
 * webcal:// — основное действие: ОС отдаёт такую ссылку календарю, и клиент
 * подписывается на живой фид, а не скачивает разовый файл. https-адрес рядом
 * для копирования — им живут Google Calendar и веб-клиенты, куда ссылку
 * вставляют руками.
 */
export function CalendarSubscribeButton({ groupId }: { groupId: string }) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [copied, setCopied] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['calendar-subscription', groupId],
    queryFn: () => groupsApi.calendarSubscription(groupId),
    enabled: open,
  })

  const copy = async () => {
    if (!data) return
    try {
      await navigator.clipboard.writeText(data.url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // Буфер недоступен (http без localhost, отказ в разрешении) — ссылка
      // всё равно на экране и выделяется руками.
    }
  }

  return (
    <>
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
        {t('group.calendar.subscribe')}
      </Button>

      <Modal open={open} onClose={() => setOpen(false)} title={t('group.calendar.title')}>
        <div className="flex flex-col gap-4">
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {t('group.calendar.description')}
          </p>

          {isLoading && (
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('group.calendar.loading')}</p>
          )}
          {error && (
            <p className="text-sm text-red-600 dark:text-red-400">{t('group.calendar.error')}</p>
          )}

          {data && (
            <>
              <a
                href={data.webcalUrl}
                className="inline-flex min-h-[44px] items-center justify-center rounded-lg bg-indigo-600 px-4 text-sm font-semibold text-white transition-colors hover:bg-indigo-500"
              >
                {t('group.calendar.openInApp')}
              </a>

              <div className="flex flex-col gap-1.5">
                <span className="text-xs font-medium uppercase tracking-wide text-gray-500 dark:text-gray-400">
                  {t('group.calendar.orCopyLink')}
                </span>
                <div className="flex items-center gap-2">
                  <input
                    readOnly
                    value={data.url}
                    onFocus={(e) => e.currentTarget.select()}
                    className="min-w-0 flex-1 rounded-lg border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-900 px-3 py-2 text-xs text-gray-700 dark:text-gray-300"
                  />
                  <Button size="sm" variant="secondary" onClick={copy}>
                    {copied ? t('group.calendar.copied') : t('group.calendar.copy')}
                  </Button>
                </div>
              </div>

              <p className="text-xs text-gray-500 dark:text-gray-400">
                {t('group.calendar.privacyNote')}
              </p>
            </>
          )}
        </div>
      </Modal>
    </>
  )
}
