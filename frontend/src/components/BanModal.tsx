import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/admin'
import { Modal } from './Modal'
import { Button } from './Button'
import type { AdminUser } from '@/types/admin'

interface Props {
  user: AdminUser | null
  onClose: () => void
}

export function BanModal({ user, onClose }: Props) {
  const [reason, setReason] = useState('')
  const qc = useQueryClient()

  const ban = useMutation({
    mutationFn: () => adminApi.banUser(user!.id, reason),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      setReason('')
      onClose()
    },
  })

  const handleClose = () => {
    setReason('')
    ban.reset()
    onClose()
  }

  return (
    <Modal
      title={`Ban: ${user?.displayName ?? ''}`}
      open={user !== null}
      onClose={handleClose}
      footer={
        <>
          <Button variant="secondary" onClick={handleClose}>
            Cancel
          </Button>
          <Button
            variant="danger"
            loading={ban.isPending}
            disabled={reason.trim().length === 0}
            onClick={() => ban.mutate()}
          >
            Ban user
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm text-gray-600 dark:text-gray-400">
          The user will be unable to sign in until unbanned.
        </p>
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Reason</label>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            placeholder="Describe why this user is being banned…"
            className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-red-500 resize-none"
          />
        </div>
        {ban.error && (
          <p className="text-sm text-red-600 dark:text-red-400">Failed to ban user. Please try again.</p>
        )}
      </div>
    </Modal>
  )
}
