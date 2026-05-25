import { useState, useRef, useEffect } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '../../stores/auth-store'
import { api } from '../../lib/api'
import ThemeToggle from './ThemeToggle'

// ── Types ─────────────────────────────────────────────────────────────────────

interface Notification {
  id: string
  type: string
  title: string
  body: string
  isRead: boolean
  createdAt: string
}

interface NavItem {
  to: string
  label: string
  icon: React.ReactNode
}

// ── Icons ─────────────────────────────────────────────────────────────────────

const BellIcon = () => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
    <path d="M13.73 21a2 2 0 0 1-3.46 0" />
  </svg>
)

const ChevronIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
    <path d="M6 9l6 6 6-6" />
  </svg>
)

const LogoutIcon = () => (
  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <polyline points="16 17 21 12 16 7" />
    <line x1="21" y1="12" x2="9" y2="12" />
  </svg>
)

// ── NotificationsPanel ────────────────────────────────────────────────────────

function NotificationsPanel({ onClose }: { onClose: () => void }) {
  const user = useAuthStore((s) => s.user)
  const queryClient = useQueryClient()

  const { data: notifications = [], isLoading } = useQuery<Notification[]>({
    queryKey: ['notifications', user?.id],
    queryFn: () =>
      api.get<Notification[]>(`/api/v1/notifications/${user?.id}`).then((r) => r.data),
    enabled: !!user?.id,
    staleTime: 30_000,
  })

  const markAllRead = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/notifications/${user?.id}/mark-all-read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications', user?.id] })
      queryClient.invalidateQueries({ queryKey: ['notifications-count', user?.id] })
    },
  })

  const unread = notifications.filter((n) => !n.isRead)

  return (
    <motion.div
      initial={{ opacity: 0, y: -8, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.97 }}
      transition={{ duration: 0.15 }}
      className="absolute right-0 top-full mt-2 w-80 bg-bg-surface border border-border-subtle rounded-2xl shadow-2xl shadow-black/40 overflow-hidden z-50"
    >
      <div className="flex items-center justify-between px-4 py-3 border-b border-border-subtle">
        <span className="text-sm font-semibold text-text-primary">Notifications</span>
        {unread.length > 0 && (
          <button
            onClick={() => markAllRead.mutate()}
            className="text-xs text-brand-primary hover:underline"
          >
            Mark all read
          </button>
        )}
      </div>

      <div className="max-h-80 overflow-y-auto">
        {isLoading ? (
          <div className="p-4 space-y-2">
            {[1, 2, 3].map((i) => (
              <div key={i} className="h-12 bg-bg-elevated rounded-lg animate-pulse" />
            ))}
          </div>
        ) : notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 gap-2">
            <span className="text-3xl">🔔</span>
            <p className="text-text-muted text-sm">All caught up!</p>
          </div>
        ) : (
          notifications.map((n) => (
            <div
              key={n.id}
              className={`px-4 py-3 border-b border-border-subtle/50 last:border-0 transition-colors hover:bg-bg-elevated ${
                !n.isRead ? 'bg-brand-primary/5' : ''
              }`}
            >
              <div className="flex items-start gap-2.5">
                {!n.isRead && (
                  <div className="mt-1.5 w-1.5 h-1.5 rounded-full bg-brand-primary shrink-0" />
                )}
                <div className={!n.isRead ? '' : 'ml-4'}>
                  <p className="text-sm font-medium text-text-primary leading-snug">{n.title}</p>
                  <p className="text-xs text-text-muted mt-0.5 leading-snug">{n.body}</p>
                </div>
              </div>
            </div>
          ))
        )}
      </div>
    </motion.div>
  )
}

// ── UserMenu ──────────────────────────────────────────────────────────────────

function UserMenu({ onClose }: { onClose: () => void }) {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  return (
    <motion.div
      initial={{ opacity: 0, y: -8, scale: 0.97 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      exit={{ opacity: 0, y: -8, scale: 0.97 }}
      transition={{ duration: 0.15 }}
      className="absolute right-0 top-full mt-2 w-52 bg-bg-surface border border-border-subtle rounded-2xl shadow-2xl shadow-black/40 overflow-hidden z-50"
    >
      <div className="px-4 py-3 border-b border-border-subtle">
        <p className="text-sm font-semibold text-text-primary truncate">{user?.displayName}</p>
        <p className="text-xs text-text-muted truncate">{user?.email}</p>
      </div>
      <div className="p-1.5">
        <Link
          to="/dashboard"
          onClick={onClose}
          className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-text-secondary hover:text-text-primary hover:bg-bg-elevated transition-colors"
        >
          Dashboard
        </Link>
        <Link
          to="/problems"
          onClick={onClose}
          className="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-text-secondary hover:text-text-primary hover:bg-bg-elevated transition-colors"
        >
          Problems
        </Link>
        <button
          onClick={() => { logout(); onClose() }}
          className="flex w-full items-center gap-2.5 px-3 py-2 rounded-lg text-sm text-error hover:bg-error/10 transition-colors"
        >
          <LogoutIcon />
          Sign out
        </button>
      </div>
    </motion.div>
  )
}

// ── AppNav ────────────────────────────────────────────────────────────────────

export default function AppNav() {
  const user = useAuthStore((s) => s.user)
  const location = useLocation()
  const [showNotifications, setShowNotifications] = useState(false)
  const [showUserMenu, setShowUserMenu] = useState(false)
  const notifRef = useRef<HTMLDivElement>(null)
  const userRef = useRef<HTMLDivElement>(null)

  const { data: unreadCount = 0 } = useQuery<number>({
    queryKey: ['notifications-count', user?.id],
    queryFn: () =>
      api.get<number>(`/api/v1/notifications/${user?.id}/unread-count`).then((r) => r.data),
    enabled: !!user?.id,
    refetchInterval: 60_000,
    staleTime: 30_000,
  })

  // Close dropdowns on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (notifRef.current && !notifRef.current.contains(e.target as Node)) {
        setShowNotifications(false)
      }
      if (userRef.current && !userRef.current.contains(e.target as Node)) {
        setShowUserMenu(false)
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const navLinks: NavItem[] = [
    {
      to: '/problems',
      label: 'Problems',
      icon: (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
      ),
    },
    {
      to: '/sysdesign',
      label: 'System Design',
      icon: (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>
      ),
    },
    {
      to: '/collaborate',
      label: 'Collaborate',
      icon: (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
      ),
    },
  ]

  const isActive = (to: string) =>
    to === '/problems'
      ? location.pathname.startsWith('/problems')
      : location.pathname.startsWith(to)

  const initials = user?.displayName
    ?.split(' ')
    .map((w) => w[0])
    .join('')
    .toUpperCase()
    .slice(0, 2) ?? '?'

  return (
    <nav className="border-b border-border-subtle bg-bg-surface/80 backdrop-blur-md sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 h-14 flex items-center justify-between gap-4">
        {/* Logo */}
        <Link to="/dashboard" className="font-bold text-gradient-brand text-lg shrink-0">
          AlgoVerse
        </Link>

        {/* Nav links */}
        <div className="hidden md:flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.to}
              to={link.to}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-all duration-150 ${
                isActive(link.to)
                  ? 'bg-brand-primary/10 text-brand-primary'
                  : 'text-text-secondary hover:text-text-primary hover:bg-bg-elevated'
              }`}
            >
              {link.icon}
              {link.label}
            </Link>
          ))}
        </div>

        {/* Right side */}
        <div className="flex items-center gap-2 shrink-0">
          <ThemeToggle />

          {/* Notification bell */}
          <div ref={notifRef} className="relative">
            <button
              onClick={() => { setShowNotifications((v) => !v); setShowUserMenu(false) }}
              className="relative p-2 rounded-lg text-text-secondary hover:text-text-primary hover:bg-bg-elevated transition-colors"
              aria-label="Notifications"
            >
              <BellIcon />
              {unreadCount > 0 && (
                <motion.span
                  initial={{ scale: 0 }}
                  animate={{ scale: 1 }}
                  className="absolute top-1 right-1 w-4 h-4 rounded-full bg-brand-primary text-white text-[10px] font-bold flex items-center justify-center"
                >
                  {unreadCount > 9 ? '9+' : unreadCount}
                </motion.span>
              )}
            </button>
            <AnimatePresence>
              {showNotifications && (
                <NotificationsPanel onClose={() => setShowNotifications(false)} />
              )}
            </AnimatePresence>
          </div>

          {/* User menu */}
          <div ref={userRef} className="relative">
            <button
              onClick={() => { setShowUserMenu((v) => !v); setShowNotifications(false) }}
              className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-bg-elevated transition-colors group"
            >
              <div className="w-7 h-7 rounded-full bg-gradient-to-br from-brand-primary to-brand-accent flex items-center justify-center text-white text-xs font-bold">
                {initials}
              </div>
              <span className="hidden sm:block text-sm text-text-secondary group-hover:text-text-primary transition-colors max-w-[100px] truncate">
                {user?.displayName}
              </span>
              <span className="text-text-muted group-hover:text-text-secondary transition-colors">
                <ChevronIcon />
              </span>
            </button>
            <AnimatePresence>
              {showUserMenu && (
                <UserMenu onClose={() => setShowUserMenu(false)} />
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </nav>
  )
}
