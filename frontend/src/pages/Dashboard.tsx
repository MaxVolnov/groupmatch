import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { meApi } from '@/api/me'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { Layout } from '@/components/Layout'
import { TIMEZONES, getBrowserTimezone } from '@/utils/timezones'

function GroupCard({ group }: { group: GroupResponse }) {
  return (
    <Link
      to={`/groups/${group.id}`}
      className="block rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-semibold text-gray-900 dark:text-gray-100">{group.title}</h3>
          {group.description && (
            <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">{group.description}</p>
          )}
        </div>
        <span className="ml-4 shrink-0 rounded-full bg-indigo-50 dark:bg-indigo-900/30 px-2 py-0.5 text-xs font-medium text-indigo-700 dark:text-indigo-300">
          {group.tzId}
        </span>
      </div>
      <div className="mt-3 flex gap-3 text-xs text-gray-400 dark:text-gray-500">
        {group.locked && <span>🔒 Locked</span>}
        {group.showParticipants && <span>👥 Names visible</span>}
      </div>
    </Link>
  )
}

function GroupCardSkeleton() {
  return (
    <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5 shadow-sm">
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <Skeleton className="h-5 w-2/3 mb-2" />
          <Skeleton className="h-4 w-full mb-1" />
          <Skeleton className="h-4 w-4/5" />
        </div>
        <Skeleton className="ml-4 shrink-0 h-5 w-20 rounded-full" />
      </div>
      <div className="mt-3 flex gap-3">
        <Skeleton className="h-3 w-16" />
      </div>
    </div>
  )
}

function CreateGroupModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [tzId, setTzId] = useState(getBrowserTimezone)

  const create = useMutation({
    mutationFn: () => groupsApi.create({ title, description: description || undefined, tzId }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] })
      setTitle('')
      setDescription('')
      setTzId(getBrowserTimezone())
      onClose()
    },
  })

  return (
    <Modal
      title="Create group"
      open={open}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            onClick={() => create.mutate()}
            disabled={!title.trim()}
          >
            Create
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
        {create.error && <ErrorMessage error={create.error} />}
      </div>
    </Modal>
  )
}

export function Dashboard() {
  const [showCreate, setShowCreate] = useState(false)
  const [resendSent, setResendSent] = useState(false)
  const isGuest = useAuthStore((s) => s.isGuest)
  const qc = useQueryClient()

  const { data: groups, isLoading, error } = useQuery({
    queryKey: ['groups'],
    queryFn: groupsApi.list,
  })

  const { data: meData } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
    enabled: !isGuest,
  })

  const isEmailVerified = isGuest || (meData?.isEmailVerified ?? true)

  const handleResend = async () => {
    await authApi.resendVerification()
    setResendSent(true)
    qc.invalidateQueries({ queryKey: ['me'] })
  }

  return (
    <Layout>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">My Groups</h1>
        <Button onClick={() => setShowCreate(true)}>+ New group</Button>
      </div>

      {!isGuest && !isEmailVerified && (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-700 rounded-lg p-3 mb-4 flex items-center justify-between">
          <span className="text-sm text-yellow-800 dark:text-yellow-200">
            Please confirm your email address to unlock all features.
          </span>
          {resendSent ? (
            <span className="text-sm font-medium text-yellow-700 dark:text-yellow-300 ml-4">Sent!</span>
          ) : (
            <button
              onClick={handleResend}
              className="text-sm font-medium text-yellow-700 dark:text-yellow-300 hover:underline ml-4"
            >
              Resend
            </button>
          )}
        </div>
      )}

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <GroupCardSkeleton key={i} />
          ))}
        </div>
      )}
      {error && <ErrorMessage error={error} />}

      {groups && groups.length === 0 && (
        <div className="rounded-xl border-2 border-dashed border-gray-200 dark:border-gray-700 py-16 text-center">
          <p className="text-3xl mb-3">📋</p>
          <p className="text-gray-500 dark:text-gray-400">No groups yet.</p>
          <button
            className="mt-2 inline-flex items-center justify-center min-h-[44px] px-4 text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300"
            onClick={() => setShowCreate(true)}
          >
            Create your first group →
          </button>
        </div>
      )}

      {groups && groups.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {groups.map((g) => (
            <GroupCard key={g.id} group={g} />
          ))}
        </div>
      )}

      <CreateGroupModal open={showCreate} onClose={() => setShowCreate(false)} />
    </Layout>
  )
}
