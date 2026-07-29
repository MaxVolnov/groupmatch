import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi } from '@/api/admin'
import { useAuthStore } from '@/store/auth'
import { Layout } from '@/components/Layout'
import { Button } from '@/components/Button'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import { BanModal } from '@/components/BanModal'
import type { AdminFeedbackItem, AdminGroup, AdminUser } from '@/types/admin'

const TABS = ['Users', 'Feedback', 'Groups'] as const
type Tab = typeof TABS[number]
const TAB_LABEL_KEYS: Record<Tab, string> = {
  Users: 'admin.tabs.users',
  Feedback: 'admin.tabs.feedback',
  Groups: 'admin.tabs.groups',
}

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
  const { t } = useTranslation()
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

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: string; role: 'USER' | 'ADMIN' }) =>
      adminApi.changeUserRole(id, role),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })

  const changePlan = useMutation({
    mutationFn: ({ id, plan }: { id: string; plan: 'FREE' | 'PRO' }) =>
      adminApi.changeUserPlan(id, plan),
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
          placeholder={t('admin.searchUsers')}
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
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.email')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.displayName')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.role')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.plan')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.status')}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                {data?.users.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                      {t('admin.noUsersFound')}
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
                        <span className="ml-1.5 text-xs text-gray-400 dark:text-gray-500">{t('admin.guestBadge')}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <RoleBadge role={u.role} />
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400">{u.plan}</td>
                    <td className="px-4 py-3">
                      {u.isBanned ? (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400">
                          {t('admin.banned')}
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400">
                          {t('admin.active')}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-1 flex-wrap">
                        {/* Role toggle */}
                        <Button
                          variant="ghost"
                          size="sm"
                          disabled={u.id === currentUserId}
                          loading={changeRole.isPending && changeRole.variables?.id === u.id}
                          onClick={() => changeRole.mutate({
                            id: u.id,
                            role: u.role === 'ADMIN' ? 'USER' : 'ADMIN',
                          })}
                        >
                          {u.role === 'ADMIN' ? t('admin.revokeAdmin') : t('admin.makeAdmin')}
                        </Button>

                        {/* Plan toggle */}
                        {u.plan !== 'TEAM' && (
                          <Button
                            variant="ghost"
                            size="sm"
                            loading={changePlan.isPending && changePlan.variables?.id === u.id}
                            onClick={() => changePlan.mutate({
                              id: u.id,
                              plan: u.plan === 'FREE' ? 'PRO' : 'FREE',
                            })}
                          >
                            {u.plan === 'FREE' ? '→ PRO' : '→ FREE'}
                          </Button>
                        )}

                        {/* Ban / Unban */}
                        {canActOn(u) && (
                          u.isBanned ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              loading={unban.isPending && unban.variables === u.id}
                              onClick={() => unban.mutate(u.id)}
                            >
                              {t('admin.unban')}
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              size="sm"
                              onClick={() => setBanTarget(u)}
                            >
                              {t('admin.ban')}
                            </Button>
                          )
                        )}
                      </div>
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
                {t('admin.page')} {data.page + 1} {t('admin.of')} {data.totalPages} — {data.totalElements} {t('admin.users')}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  {t('admin.prev')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  {t('admin.next')}
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
  { value: '', labelKey: 'admin.categoryAll' },
  { value: 'BUG', labelKey: 'admin.categoryBug' },
  { value: 'FEATURE_REQUEST', labelKey: 'admin.categoryFeature' },
  { value: 'OTHER', labelKey: 'admin.categoryOther' },
] as const

const RESOLVED_OPTIONS = [
  { value: undefined as boolean | undefined, labelKey: 'admin.resolvedAll' },
  { value: false, labelKey: 'admin.resolvedPending' },
  { value: true, labelKey: 'admin.resolvedResolved' },
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
  const { t } = useTranslation()
  const styles: Record<AdminFeedbackItem['category'], string> = {
    BUG: 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400',
    FEATURE_REQUEST: 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400',
    OTHER: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  }
  const labelKeys: Record<AdminFeedbackItem['category'], string> = {
    BUG: 'admin.categoryBug',
    FEATURE_REQUEST: 'admin.categoryFeature',
    OTHER: 'admin.categoryOther',
  }
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${styles[category]}`}>
      {t(labelKeys[category])}
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
  const { t } = useTranslation()
  const date = new Date(item.createdAt).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  })

  return (
    <div className={`p-4 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex flex-col gap-2 ${item.resolved ? 'opacity-60' : ''}`}>
      <div className="flex items-center gap-2 flex-wrap">
        <CategoryBadge category={item.category} />
        {item.resolved && (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400">
            {t('admin.resolvedResolved')}
          </span>
        )}
      </div>
      <p className="text-sm text-gray-800 dark:text-gray-200 whitespace-pre-wrap">{item.message}</p>
      <div className="flex items-center justify-between gap-2 flex-wrap mt-1">
        <span className="text-xs text-gray-400 dark:text-gray-500">
          {item.authorDisplayName ?? t('admin.unknown')} · {item.authorEmail ?? '—'} · {date}
        </span>
        {item.resolved ? (
          <Button variant="ghost" size="sm" loading={unresolving} onClick={onUnresolve}>
            {t('admin.unresolve')}
          </Button>
        ) : (
          <Button variant="ghost" size="sm" loading={resolving} onClick={onResolve}>
            {t('admin.resolve')}
          </Button>
        )}
      </div>
    </div>
  )
}

function FeedbackTab() {
  const { t } = useTranslation()
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
              {t(opt.labelKey)}
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
              {t(opt.labelKey)}
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
            <p className="py-8 text-center text-sm text-gray-400 dark:text-gray-500">{t('admin.noFeedbackFound')}</p>
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
                {t('admin.page')} {data.page + 1} {t('admin.of')} {data.totalPages} — {data.totalElements} {t('admin.items')}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  {t('admin.prev')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  {t('admin.next')}
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  )
}

// ── Groups tab ────────────────────────────────────────────────────────────────

function GroupTableSkeleton() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <div key={i} className="flex gap-3 items-center px-4 py-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
          <Skeleton className="h-4 flex-1" />
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-10" />
          <Skeleton className="h-4 w-24" />
          <Skeleton className="h-8 w-16" />
        </div>
      ))}
    </div>
  )
}

function GroupsTab() {
  const { t } = useTranslation()
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [searchInput])

  const qc = useQueryClient()

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin', 'groups', search, page],
    queryFn: () => adminApi.getGroups(search || undefined, page),
  })

  const deleteGroup = useMutation({
    mutationFn: (id: string) => adminApi.deleteGroup(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'groups'] }),
  })

  const handleDelete = (group: AdminGroup) => {
    if (!window.confirm(t('admin.deleteGroupConfirm', { title: group.title }))) return
    deleteGroup.mutate(group.id)
  }

  return (
    <div>
      {/* Search */}
      <div className="mb-4">
        <input
          type="search"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder={t('admin.searchGroups')}
          className="w-full sm:w-80 rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
      </div>

      {error && <ErrorMessage error={error} />}

      {isLoading ? (
        <GroupTableSkeleton />
      ) : (
        <>
          <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/60">
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.title')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.owner')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.members')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.timezone')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.locked')}</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('admin.table.created')}</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200 dark:divide-gray-700 bg-white dark:bg-gray-800">
                {data?.groups.length === 0 && (
                  <tr>
                    <td colSpan={7} className="px-4 py-8 text-center text-sm text-gray-400 dark:text-gray-500">
                      {t('admin.noGroupsFound')}
                    </td>
                  </tr>
                )}
                {data?.groups.map((g) => (
                  <tr key={g.id}>
                    <td className="px-4 py-3 text-gray-900 dark:text-gray-100 max-w-[180px] truncate font-medium">
                      {g.title}
                    </td>
                    <td className="px-4 py-3 text-gray-700 dark:text-gray-300 max-w-[160px]">
                      <div className="truncate">{g.ownerDisplayName}</div>
                      <div className="truncate text-xs text-gray-400 dark:text-gray-500">{g.ownerEmail}</div>
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-center">
                      {g.memberCount}
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                      {g.timezone}
                    </td>
                    <td className="px-4 py-3">
                      {g.isLocked && (
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-400">
                          {t('admin.table.locked')}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-xs text-gray-400 dark:text-gray-500 whitespace-nowrap">
                      {new Date(g.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        variant="ghost"
                        size="sm"
                        loading={deleteGroup.isPending && deleteGroup.variables === g.id}
                        onClick={() => handleDelete(g)}
                      >
                        {t('admin.delete')}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {data && data.totalPages > 1 && (
            <div className="mt-4 flex items-center justify-between text-sm text-gray-500 dark:text-gray-400">
              <span>
                {t('admin.page')} {data.page + 1} {t('admin.of')} {data.totalPages} — {data.totalElements} {t('admin.groups')}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  {t('admin.prev')}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={data.page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  {t('admin.next')}
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
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<Tab>('Users')
  const { userId } = useAuthStore()

  return (
    <Layout>
      <div className="max-w-6xl">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mb-6">{t('admin.title')}</h1>

        {/* Tabs */}
        <div className="mb-6 border-b border-gray-200 dark:border-gray-700">
          <nav className="flex gap-1">
            {TABS.map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`px-4 py-2.5 text-sm font-medium border-b-2 transition-colors ${
                  activeTab === tab
                    ? 'border-indigo-600 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                    : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                }`}
              >
                {t(TAB_LABEL_KEYS[tab])}
              </button>
            ))}
          </nav>
        </div>

        {activeTab === 'Users' && <UsersTab currentUserId={userId ?? ''} />}
        {activeTab === 'Feedback' && <FeedbackTab />}
        {activeTab === 'Groups' && <GroupsTab />}
      </div>
    </Layout>
  )
}
