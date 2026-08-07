import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { feedbackApi } from '@/api/feedback'
import { Button } from './Button'
import { Modal } from './Modal'
import { ErrorMessage } from './ErrorMessage'
import type { FeedbackCategory } from '@/types'

interface Props {
  open: boolean
  onClose: () => void
}

const CATEGORIES: { value: FeedbackCategory; labelKey: string }[] = [
  { value: 'BUG', labelKey: 'feedback.categoryBug' },
  { value: 'FEATURE_REQUEST', labelKey: 'feedback.categoryFeature' },
  { value: 'OTHER', labelKey: 'feedback.categoryOther' },
]

export function FeedbackModal({ open, onClose }: Props) {
  const { t } = useTranslation()
  const [category, setCategory] = useState<FeedbackCategory>('OTHER')
  const [message, setMessage] = useState('')
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const submit = useMutation({
    mutationFn: () => feedbackApi.create({ category, message }),
    onSuccess: () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
      timeoutRef.current = setTimeout(() => {
        onClose()
        setCategory('OTHER')
        setMessage('')
        submit.reset()
      }, 2000)
    },
  })

  const { reset } = submit

  useEffect(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    if (!open) return
    setCategory('OTHER')
    setMessage('')
    reset()
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [open, reset])

  const isSuccess = submit.isSuccess

  return (
    <Modal
      title={t('feedback.title')}
      open={open}
      onClose={onClose}
      footer={
        isSuccess ? undefined : (
          <>
            <Button variant="secondary" onClick={onClose}>{t('feedback.cancel')}</Button>
            <Button
              loading={submit.isPending}
              disabled={message.trim().length < 10}
              onClick={() => submit.mutate()}
            >
              {t('feedback.send')}
            </Button>
          </>
        )
      }
    >
      {isSuccess ? (
        <div className="py-6 text-center">
          <p className="text-2xl mb-3">✅</p>
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
            {t('feedback.thanks')}
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">{t('feedback.category')}</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as FeedbackCategory)}
              className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-gm-500"
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>{t(c.labelKey)}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">{t('feedback.message')}</label>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={5}
              minLength={10}
              maxLength={2000}
              required
              placeholder={t('feedback.messagePlaceholder')}
              className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-gm-500 dark:focus:ring-gm-400 resize-none"
            />
            <p className="text-xs text-gray-400 dark:text-gray-500 text-right">
              {message.length} / 2000
            </p>
          </div>

          {submit.error && <ErrorMessage error={submit.error} />}
        </div>
      )}
    </Modal>
  )
}
