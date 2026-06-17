import { useEffect, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { feedbackApi } from '@/api/feedback'
import { Button } from './Button'
import { Modal } from './Modal'
import { ErrorMessage } from './ErrorMessage'
import type { FeedbackCategory } from '@/types'

interface Props {
  open: boolean
  onClose: () => void
}

const CATEGORIES: { value: FeedbackCategory; label: string }[] = [
  { value: 'BUG', label: 'Bug report' },
  { value: 'FEATURE_REQUEST', label: 'Feature request' },
  { value: 'OTHER', label: 'Other' },
]

export function FeedbackModal({ open, onClose }: Props) {
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

  useEffect(() => {
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    if (!open) return
    setCategory('OTHER')
    setMessage('')
    submit.reset()
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [open])

  const isSuccess = submit.isSuccess

  return (
    <Modal
      title="Send feedback"
      open={open}
      onClose={onClose}
      footer={
        isSuccess ? undefined : (
          <>
            <Button variant="secondary" onClick={onClose}>Cancel</Button>
            <Button
              loading={submit.isPending}
              disabled={message.trim().length < 10}
              onClick={() => submit.mutate()}
            >
              Send
            </Button>
          </>
        )
      }
    >
      {isSuccess ? (
        <div className="py-6 text-center">
          <p className="text-2xl mb-3">✅</p>
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
            Thanks! We got your feedback.
          </p>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Category</label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as FeedbackCategory)}
              className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
            >
              {CATEGORIES.map((c) => (
                <option key={c.value} value={c.value}>{c.label}</option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1">
            <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Message</label>
            <textarea
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              rows={5}
              minLength={10}
              maxLength={2000}
              required
              placeholder="Tell us what's on your mind..."
              className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500 dark:focus:ring-indigo-400 resize-none"
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
