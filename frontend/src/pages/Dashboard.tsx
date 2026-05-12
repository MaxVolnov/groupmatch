import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { groupsApi } from '@/api/groups'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Modal } from '@/components/Modal'
import { Spinner } from '@/components/Spinner'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { GroupResponse } from '@/types'
import { Layout } from '@/components/Layout'

function GroupCard({ group }: { group: GroupResponse }) {
  return (
    <Link
      to={`/groups/${group.id}`}
      className="block rounded-xl border bg-white p-5 shadow-sm hover:shadow-md transition-shadow"
    >
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-semibold text-gray-900">{group.title}</h3>
          {group.description && (
            <p className="mt-1 text-sm text-gray-500 line-clamp-2">{group.description}</p>
          )}
        </div>
        <span className="ml-4 shrink-0 rounded-full bg-indigo-50 px-2 py-0.5 text-xs font-medium text-indigo-700">
          {group.tzId}
        </span>
      </div>
      <div className="mt-3 flex gap-3 text-xs text-gray-400">
        {group.locked && <span>🔒 Locked</span>}
        {group.showParticipants && <span>👥 Names visible</span>}
      </div>
    </Link>
  )
}

function CreateGroupModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const create = useMutation({
    mutationFn: () => groupsApi.create({ title, description: description || undefined }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['groups'] })
      setTitle('')
      setDescription('')
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
        {create.error && <ErrorMessage error={create.error} />}
      </div>
    </Modal>
  )
}

export function Dashboard() {
  const [showCreate, setShowCreate] = useState(false)
  const { data: groups, isLoading, error } = useQuery({
    queryKey: ['groups'],
    queryFn: groupsApi.list,
  })

  return (
    <Layout>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">My Groups</h1>
        <Button onClick={() => setShowCreate(true)}>+ New group</Button>
      </div>

      {isLoading && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      )}
      {error && <ErrorMessage error={error} />}

      {groups && groups.length === 0 && (
        <div className="rounded-xl border-2 border-dashed border-gray-200 py-16 text-center">
          <p className="text-gray-500">No groups yet.</p>
          <button
            className="mt-2 text-sm font-medium text-indigo-600 hover:text-indigo-700"
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
