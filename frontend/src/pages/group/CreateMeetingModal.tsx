import { useEffect, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { meetingsApi } from '@/api/meetings'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { ErrorMessage } from '@/components/ErrorMessage'
import { DateTime } from 'luxon'

interface Props {
  groupId: string
  open: boolean
  onClose: () => void
  initialStartsAt?: string
  initialEndsAt?: string
}

function toIso(local: string): string {
  return DateTime.fromISO(local, { zone: 'local' }).toUTC().toISO()!
}

function defaultDatetime(offsetHours: number): string {
  return DateTime.now().plus({ hours: offsetHours }).toFormat("yyyy-MM-dd'T'HH:mm")
}

export function CreateMeetingModal({ groupId, open, onClose, initialStartsAt, initialEndsAt }: Props) {
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startsAt, setStartsAt] = useState(defaultDatetime(24))
  const [endsAt, setEndsAt] = useState(defaultDatetime(25))

  useEffect(() => {
    if (!open) return
    setTitle('')
    setDescription('')
    if (initialStartsAt && initialEndsAt) {
      setStartsAt(DateTime.fromISO(initialStartsAt).toLocal().toFormat("yyyy-MM-dd'T'HH:mm"))
      setEndsAt(DateTime.fromISO(initialEndsAt).toLocal().toFormat("yyyy-MM-dd'T'HH:mm"))
    } else {
      setStartsAt(defaultDatetime(24))
      setEndsAt(defaultDatetime(25))
    }
  }, [open, initialStartsAt, initialEndsAt])

  const onStartsAtChange = (value: string) => {
    setStartsAt(value)
    const newStart = DateTime.fromISO(value)
    const currentEnd = DateTime.fromISO(endsAt)
    if (!currentEnd.isValid || currentEnd <= newStart) {
      setEndsAt(newStart.plus({ hours: 1 }).toFormat("yyyy-MM-dd'T'HH:mm"))
    }
  }

  const create = useMutation({
    mutationFn: () =>
      meetingsApi.create(groupId, {
        title,
        description: description || undefined,
        startsAt: toIso(startsAt),
        endsAt: toIso(endsAt),
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['meetings', groupId] })
      onClose()
    },
  })

  return (
    <Modal
      title="Schedule meeting"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button loading={create.isPending} disabled={!title.trim()} onClick={() => create.mutate()}>
            Schedule
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <Input
          label="Title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Sprint planning"
          minLength={3}
          maxLength={100}
          required
        />
        <Input
          label="Description (optional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={2000}
        />
        <Input
          label="Starts at"
          type="datetime-local"
          value={startsAt}
          onChange={(e) => onStartsAtChange(e.target.value)}
        />
        <Input
          label="Ends at"
          type="datetime-local"
          value={endsAt}
          onChange={(e) => setEndsAt(e.target.value)}
        />
        {create.error && <ErrorMessage error={create.error} />}
      </div>
    </Modal>
  )
}
