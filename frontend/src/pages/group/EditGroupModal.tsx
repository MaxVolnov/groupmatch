import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { TIMEZONES } from '@/utils/timezones'

interface Props {
  group: GroupResponse
  open: boolean
  onClose: () => void
}

export function EditGroupModal({ group, open, onClose }: Props) {
  const qc = useQueryClient()
  const [title, setTitle] = useState(group.title)
  const [description, setDescription] = useState(group.description ?? '')
  const [tzId, setTzId] = useState(group.tzId)
  const [showParticipants, setShowParticipants] = useState(group.showParticipants)

  useEffect(() => {
    if (open) {
      setTitle(group.title)
      setDescription(group.description ?? '')
      setTzId(group.tzId)
      setShowParticipants(group.showParticipants)
    }
  }, [open, group])

  const update = useMutation({
    mutationFn: () =>
      groupsApi.update(group.id, {
        title,
        description: description || undefined,
        tzId,
        showParticipants,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['group', group.id] })
      qc.invalidateQueries({ queryKey: ['groups'] })
      onClose()
    },
  })

  return (
    <Modal
      title="Edit group"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={update.isPending}
            disabled={!title.trim()}
            onClick={() => update.mutate()}
          >
            Save
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Input
          label="Group name"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="e.g. Weekly Sync"
          minLength={3}
          maxLength={100}
          required
        />
        <Input
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="What's this group for?"
          maxLength={1000}
        />
        <div className="flex flex-col gap-1">
          <label className="text-sm font-medium text-gray-700 dark:text-gray-300">Timezone</label>
          <select
            value={tzId}
            onChange={(e) => setTzId(e.target.value)}
            className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          >
            {TIMEZONES.map((tz) => (
              <option key={tz.value} value={tz.value}>{tz.label}</option>
            ))}
          </select>
        </div>
        <label className="flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={showParticipants}
            onChange={(e) => setShowParticipants(e.target.checked)}
            className="h-4 w-4 rounded border-gray-300 dark:border-gray-600 text-indigo-600 focus:ring-indigo-500"
          />
          <span className="text-sm text-gray-700 dark:text-gray-300">
            Show participant names to group members
          </span>
        </label>
        {update.error && <ErrorMessage error={update.error} />}
      </div>
    </Modal>
  )
}
