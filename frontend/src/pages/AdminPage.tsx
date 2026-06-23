import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/store/auth'
import { Layout } from '@/components/Layout'
import { Button } from '@/components/Button'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import { BanModal } from '@/components/BanModal'
import type { AdminFeedbackItem, AdminUser } from '@/types/admin'

const TABS = ['Users', 'Feedback', 'Groups'] as const
type Tab = typeof TABS[number]

// ── Users tab ─────────────────────────────────────────────────────────────────

function UserTableSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex gap-3 items-center px-4 py-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-16" />
          <Skeleton className="h-8 w-16" />
        </div>
      ))}
    </div>
  )
}

function RoleBadge({ role }: { role: AdminUser['role'] }) {
  return role === 'ADMIN' ? (
    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-indigo-100 text-indigo-700 dark:bg-indigo-900 dark:text-indigo-300">
      ADMIN
    </span>
  ) : (
    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400">
      USER
    </span>
  )
}

function UsersTab({ currentUserId }: { currentUserId: string }) {
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [banTarget, setBanTarget] = useState<AdminUser | null>(null)

  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [searchInput])

  const qc = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'users', search, page],
    queryFn: () => adminApi.getUsers(search || undefined, page),
  })

  const unban = useMutation({
    mutationFn: (id: string) => adminApi.unbanUser(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const canActOn = (u: AdminUser) => u.id !== currentUserId && u.role !== 'ADMIN'

  return (
    <div>
      {/* Search */}
      <div className="mb-4">
        <input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search by email or display name…"
          className="w-full sm:w-80 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {error && <ErrorMessage error={error} />}

      {isLoading ? (
        <UserTableSkeleton />
      ) : (
        <>
          {/* Table */}
          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60">
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Email</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Display name</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Role</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Plan</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">Status</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                {data?.users.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                      No users found.
                    </td>
                  </tr>
                )}
                {data?.users.map((u) => (
                  <tr key={u.id} className={u.isBanned ? 'opacity-50' : undefined}>
                    <td className="px-4 py-3 text-gray-900 dark:text-gray-100 max-w-[200px] truncate">
                      {u.email}
                    </td>
                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300">
                      {u.displayName}
                      {u.isGuest && (
                        <span className="ml-1.5 text-xs text-gray-400 dark:text-gray-500">guest</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <RoleBadge role={u.role} />
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{u.plan}</td>
                    <td className="px-4 py-3">
                      {u.isBanned ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400">
                          Banned
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400">
                          Active
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {canActOn(u) && (
                        u.isBanned ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            loading={unban.isPending && unban.variables === u.id}
                            onClick={() => unban.mutate(u.id)}
                          >
                            Unban
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setBanTarget(u)}
                          >
                            Ban
                          </Button>
                        )
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {data && data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-gray-500 dark:text-gray-400">
              <span>
                Page {data.page + 1} of {data.totalPages} — {data.totalElements} users
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  ← Prev
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next →
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <BanModal user={banTarget} onClose={() => setBanTarget(null)} />
    </div>
  )
}

// ── Feedback tab ──────────────────────────────────────────────────────────────

const CATEGORY_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'BUG', label: 'Bug' },
  { value: 'FEATURE_REQUEST', label: 'Feature Request' },
  { value: 'OTHER', label: 'Other' },
] as const

const RESOLVED_OPTIONS = [
  { value: undefined as boolean | undefined, label: 'All' },
  { value: false, label: 'Pending' },
  { value: true, label: 'Resolved' },
]

function FeedbackCardSkeleton() {
  return (
    <div className="flex flex-col gap-2 p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
      <div className="flex items-center gap-2">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-4 w-16" />
      </div>
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-3/4" />
      <div className="flex justify-between items-center mt-1">
        <Skeleton className="h-3 w-32" />
        <Skeleton className="h-7 w-20" />
      </div>
    </div>
  )
}

function CategoryBadge({ category }: { category: AdminFeedbackItem['category'] }) {
  const styles: Record<AdminFeedbackItem['category'], string> = {
    BUG: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400',
    FEATURE_REQUEST: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400',
    OTHER: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  }
  const labels: Record<AdminFeedbackItem['category'], string> = {
    BUG: 'Bug',
    FEATURE_REQUEST: 'Feature',
    OTHER: 'Other',
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${styles[category]}`}>
      {labels[category]}
    </span>
  )
}

function FeedbackCard({
  item,
  onResolve,
  onUnresolve,
  resolving,
  unresolving,
}: {
  item: AdminFeedbackItem
  onResolve: () => void
  onUnresolve: () => void
  resolving: boolean
  unresolving: boolean
}) {
  const date = new Date(item.createdAt).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  })

  return (
    <div className={`p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex flex-col gap-2 ${item.resolved ? 'opacity-60' : ''}`}>
      <div className="flex items-center gap-2 flex-wrap">
        <CategoryBadge category={item.category} />
        {item.resolved && (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400">
            Resolved
          </span>
        )}
      </div>
      <p className="text-sm text-gray-800 dark:text-gray-200 whitespace-pre-wrap">{item.message}</p>
      <div className="flex items-center justify-between gap-2 flex-wrap mt-1">
        <span className="text-xs text-gray-400 dark:text-gray-500">
          {item.authorDisplayName ?? 'Unknown'} · {item.authorEmail ?? '—'} · {date}
        </span>
        {item.resolved ? (
          <Button variant="ghost" size="sm" loading={unresolving} onClick={onUnresolve}>
            Unresolve
          </Button>
        ) : (
          <Button variant="ghost" size="sm" loading={resolving} onClick={onResolve}>
            Resolve
          </Button>
        )}
      </div>
    </div>
  )
}

function FeedbackTab() {
  const [category, setCategory] = useState('')
  const [resolved, setResolved] = useState<boolean | undefined>(undefined)
  const [page, setPage] = useState(0)

  const qc = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'feedback', category, resolved, page],
    queryFn: () => adminApi.getFeedback(category || undefined, resolved, page),
  })

  const resolve = useMutation({
    mutationFn: (id: string) => adminApi.resolveFeedback(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'feedback'] }),
  })

  const unresolve = useMutation({
    mutationFn: (id: string) => adminApi.unresolveFeedback(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'feedback'] }),
  })

  const handleCategoryChange = (value: string) => {
    setCategory(value)
    setPage(0)
  }

  const handleResolvedChange = (value: boolean | undefined) => {
    setResolved(value)
    setPage(0)
  }

  return (
    <div>
      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div className="flex gap-1">
          {CATEGORY_OPTIONS.map((opt) => (
            <button
              key={opt.value}
              onClick={() => handleCategoryChange(opt.value)}
              className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                category === opt.value
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300 dark:border-indigo-400'
                  : 'border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-400 dark:hover:border-gray-500'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
        <div className="flex gap-1">
          {RESOLVED_OPTIONS.map((opt) => (
            <button
              key={String(opt.value)}
              onClick={() => handleResolvedChange(opt.value)}
              className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                resolved === opt.value
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:bg-indigo-900/40 dark:text-indigo-300 dark:border-indigo-400'
                  : 'border-gray-300 dark:border-gray-600 text-gray-600 dark:text-gray-400 hover:border-gray-400 dark:hover:border-gray-500'
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      {error && <ErrorMessage error={error} />}

      {isLoading ? (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 4 }).map((_, i) => <FeedbackCardSkeleton key={i} />)}
        </div>
      ) : (
        <>
          {data?.items.length === 0 && (
            <p className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">No feedback found.</p>
          )}
          <div className="flex flex-col gap-3">
            {data?.items.map((item) => (
              <FeedbackCard
                key={item.id}
                item={item}
                onResolve={() => resolve.mutate(item.id)}
                onUnresolve={() => unresolve.mutate(item.id)}
                resolving={resolve.isPending && resolve.variables === item.id}
                unresolving={unresolve.isPending && unresolve.variables === item.id}
              />
            ))}
          </div>

          {data && data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-gray-500 dark:text-gray-400">
              <span>
                Page {data.page + 1} of {data.totalPages} — {data.totalElements} items
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  ← Prev
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next →
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ── AdminPage ─────────────────────────────────────────────────────────────────

export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<Tab>('Users')
  const { userId } = useAuthStore()

  return (
    <Layout>
      <div className="max-w-6xl">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">Admin Panel</h1>

        {/* Tabs */}
        <div className="mb-6 border-b border-gray-200 dark:border-gray-700">
          <nav className="flex gap-1">
            {TABS.map((tab) => {
              const disabled = tab === 'Groups'
              return (
                <button
                  key={tab}
                  disabled={disabled}
                  onClick={() => !disabled && setActiveTab(tab)}
                  className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                    activeTab === tab
                      ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                      : disabled
                        ? 'border-transparent text-gray-300 dark:text-gray-600 cursor-not-allowed'
                        : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                  }`}
                >
                  {tab}
                  {disabled && (
                    <span className="ml-1.5 text-xs opacity-60">soon</span>
                  )}
                </button>
              )
            })}
          </nav>
        </div>

        {activeTab === 'Users' && <UsersTab currentUserId={userId ?? ''} />}
        {activeTab === 'Feedback' && <FeedbackTab />}
      </div>
    </Layout>
  )
}
