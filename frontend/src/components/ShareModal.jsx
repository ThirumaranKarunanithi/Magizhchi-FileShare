import { useState, useEffect } from 'react';
import { users, conversations, sharing } from '../services/api';
import toast from 'react-hot-toast';
import Avatar from './Avatar';

// ── Helpers ──────────────────────────────────────────────────────────────────

function initials(name) {
  if (!name) return '?';
  const p = name.trim().split(' ');
  return p.length >= 2 ? (p[0][0] + p[1][0]).toUpperCase() : p[0][0].toUpperCase();
}

// ── Component ─────────────────────────────────────────────────────────────────

/**
 * ShareModal
 * Props:
 *   selectedIds  — Set<number> of FileMessage IDs to share
 *   onClose      — () => void
 */
export default function ShareModal({ selectedIds, onClose }) {
  const [tab,        setTab]        = useState('USERS');   // 'USERS' | 'GROUPS'
  const [query,      setQuery]      = useState('');
  const [userRes,    setUserRes]    = useState([]);
  const [groups,     setGroups]     = useState([]);
  const [target,     setTarget]     = useState(null);      // { id, name, photoUrl, type }
  // Download-permission picker — same shape as the Upload dialog so the user
  // sees a consistent control across actions.
  const [downloadPerm, setDownloadPerm] = useState('CAN_DOWNLOAD'); // 'CAN_DOWNLOAD' | 'VIEW_ONLY' | 'ADMIN_ONLY_DOWNLOAD'
  const [searching,  setSearching]  = useState(false);
  const [sharing_,   setSharing_]   = useState(false);

  const count = selectedIds instanceof Set ? selectedIds.size : (selectedIds?.length ?? 0);
  const ids   = selectedIds instanceof Set ? [...selectedIds] : (selectedIds ?? []);

  // ── Load groups on mount ───────────────────────────────────────────────────
  useEffect(() => {
    conversations.list()
      .then(r => setGroups(r.data.filter(c => c.type === 'GROUP')))
      .catch(console.error);
  }, []);

  // ── Search users (debounced) ───────────────────────────────────────────────
  useEffect(() => {
    if (tab !== 'USERS') return;
    if (query.trim().length < 2) { setUserRes([]); return; }
    const t = setTimeout(() => {
      setSearching(true);
      users.search(query.trim())
        .then(r => setUserRes(r.data))
        .catch(console.error)
        .finally(() => setSearching(false));
    }, 350);
    return () => clearTimeout(t);
  }, [query, tab]);

  // Map the upload-style download-permission picker to the backend share
  // permission. CAN_DOWNLOAD grants the recipient full access (EDITOR);
  // VIEW_ONLY and ADMIN_ONLY_DOWNLOAD restrict the share to view-only.
  // For ADMIN_ONLY_DOWNLOAD, downloads are gated by the FILE's own
  // permission (admins of the target only) — the share itself stays VIEWER.
  const sharePermissionFromDownloadPerm = (dp) =>
    dp === 'CAN_DOWNLOAD' ? 'EDITOR' : 'VIEWER';

  // ── Submit ─────────────────────────────────────────────────────────────────
  const handleShare = async () => {
    if (!target) return;
    setSharing_(true);
    try {
      const permission = sharePermissionFromDownloadPerm(downloadPerm);
      await sharing.create({
        resourceIds: ids,
        shareType:   target.type,
        targetId:    target.id,
        permission,
      });
      toast.success(
        `${count} file${count !== 1 ? 's' : ''} shared with ${target.name}`
      );
      onClose();
    } catch (e) {
      toast.error(e?.toString() || 'Share failed');
    } finally {
      setSharing_(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
         style={{ background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(4px)' }}
         onClick={e => e.target === e.currentTarget && onClose()}>

      <div className="w-full max-w-md rounded-2xl overflow-hidden shadow-2xl"
           style={{
             background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)',
           }}>

        <div className="relative">

          {/* ── Header ── */}
          <div className="flex items-center justify-between px-6 py-4 border-b border-white/20">
            <div>
              <h2 className="text-white font-bold text-lg">Share Files</h2>
              <p className="text-white/60 text-xs mt-0.5">
                {count} file{count !== 1 ? 's' : ''} selected
              </p>
            </div>
            <button onClick={onClose}
                    className="w-8 h-8 flex items-center justify-center rounded-full
                               bg-white/10 hover:bg-white/20 text-white transition-colors">
              ✕
            </button>
          </div>

          {/* ── Tabs ── */}
          <div className="flex px-6 pt-4 gap-2">
            {['USERS', 'GROUPS'].map(t => (
              <button key={t}
                      onClick={() => { setTab(t); setTarget(null); setQuery(''); }}
                      className={`px-4 py-1.5 rounded-lg text-sm font-semibold transition-all
                                  ${tab === t
                                    ? 'bg-white text-sky-700 shadow'
                                    : 'bg-white/15 text-white hover:bg-white/25'}`}>
                {t === 'USERS' ? '👤 Users' : '👥 Groups'}
              </button>
            ))}
          </div>

          {/* ── Search / List ── */}
          <div className="px-6 pt-3 pb-2">
            {tab === 'USERS' ? (
              <>
                <input
                  className="w-full px-4 py-2.5 rounded-xl text-sm outline-none
                             bg-white/15 border border-white/25 text-white
                             placeholder-white/50 focus:bg-white/20 focus:border-white/50
                             transition-all"
                  placeholder="Search by name, phone or email…"
                  value={query}
                  onChange={e => { setQuery(e.target.value); setTarget(null); }}
                  autoFocus
                />
                <div className="mt-2 max-h-44 overflow-y-auto rounded-xl"
                     style={{ background: 'rgba(255,255,255,0.08)' }}>
                  {searching && (
                    <p className="text-xs text-white/50 px-4 py-3">Searching…</p>
                  )}
                  {!searching && query.trim().length >= 2 && userRes.length === 0 && (
                    <p className="text-xs text-white/50 px-4 py-3 text-center">No users found</p>
                  )}
                  {userRes.map(u => (
                    <button key={u.id}
                            onClick={() => setTarget({ id: u.id, name: u.displayName,
                                                       photoUrl: u.profilePhotoUrl, type: 'USER' })}
                            className={`w-full flex items-center gap-3 px-4 py-2.5 text-left
                                        transition-colors border-b border-white/10 last:border-0
                                        ${target?.id === u.id && target?.type === 'USER'
                                          ? 'bg-white/20'
                                          : 'hover:bg-white/10'}`}>
                      <Avatar name={u.displayName} photoUrl={u.profilePhotoUrl} size="sm"/>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-white truncate">{u.displayName}</p>
                        {u.mobileNumber && (
                          <p className="text-xs text-white/50 truncate">{u.mobileNumber}</p>
                        )}
                      </div>
                      {target?.id === u.id && target?.type === 'USER' && (
                        <span className="text-white text-sm flex-shrink-0">✓</span>
                      )}
                    </button>
                  ))}
                </div>
              </>
            ) : (
              /* GROUPS tab */
              <div className="max-h-52 overflow-y-auto rounded-xl"
                   style={{ background: 'rgba(255,255,255,0.08)' }}>
                {groups.length === 0 && (
                  <p className="text-xs text-white/50 px-4 py-3 text-center">
                    No groups yet. Create a group first.
                  </p>
                )}
                {groups.map(g => (
                  <button key={g.id}
                          onClick={() => setTarget({ id: g.id, name: g.name,
                                                     photoUrl: g.iconUrl, type: 'GROUP' })}
                          className={`w-full flex items-center gap-3 px-4 py-2.5 text-left
                                      transition-colors border-b border-white/10 last:border-0
                                      ${target?.id === g.id && target?.type === 'GROUP'
                                        ? 'bg-white/20'
                                        : 'hover:bg-white/10'}`}>
                    <div className="w-8 h-8 rounded-xl flex-shrink-0 overflow-hidden
                                    bg-white/20 flex items-center justify-center text-base">
                      {g.iconUrl
                        ? <img src={g.iconUrl} alt="" className="w-full h-full object-cover"/>
                        : '👥'}
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-white truncate">{g.name}</p>
                      <p className="text-xs text-white/50">{g.memberCount} members</p>
                    </div>
                    {target?.id === g.id && target?.type === 'GROUP' && (
                      <span className="text-white text-sm flex-shrink-0">✓</span>
                    )}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* ── Download Permission (matches the Upload dialog) ──
              "Admins" only makes sense for group shares — there are no
              admins in a 1-on-1 share. Disable that option whenever the
              user is on the Users tab AND auto-fall-back if they had it
              selected before switching tabs. */}
          {(() => {
            const adminAllowed = tab === 'GROUPS';
            // Auto-fall-back: if Admins is selected but tab is Users, reset.
            if (!adminAllowed && downloadPerm === 'ADMIN_ONLY_DOWNLOAD') {
              setTimeout(() => setDownloadPerm('CAN_DOWNLOAD'), 0);
            }
            return (
          <div className="px-6 pb-4">
            <p className="text-xs font-semibold text-white/60 mb-2 uppercase tracking-wide">
              Download Permission
            </p>
            <div className="flex gap-2">
              {[
                { value: 'CAN_DOWNLOAD',         icon: '⬇', label: 'Anyone',    desc: 'Anyone can download'      },
                { value: 'VIEW_ONLY',            icon: '👁', label: 'View only', desc: 'Preview only, no download' },
                { value: 'ADMIN_ONLY_DOWNLOAD',  icon: '🛡', label: 'Admins',    desc: 'Only admins can download' },
              ].map(opt => {
                const disabled = opt.value === 'ADMIN_ONLY_DOWNLOAD' && !adminAllowed;
                const selected = !disabled && downloadPerm === opt.value;
                return (
                  <button key={opt.value}
                          type="button"
                          disabled={disabled}
                          onClick={() => !disabled && setDownloadPerm(opt.value)}
                          title={disabled ? 'Available only when sharing with a group' : undefined}
                          className="flex-1 flex flex-col items-center gap-0.5 py-2 px-1 rounded-xl
                                     border transition-all text-center"
                          style={{
                            background: selected ? 'white' : 'rgba(255,255,255,0.10)',
                            borderColor: selected ? 'white' : 'rgba(255,255,255,0.20)',
                            boxShadow: selected ? '0 0 0 2px rgba(255,255,255,0.30)' : 'none',
                            cursor: disabled ? 'not-allowed' : 'pointer',
                            opacity: disabled ? 0.4 : 1,
                          }}>
                    <span className="text-base leading-none"
                          style={{ color: selected ? '#0284c7' : 'white' }}>
                      {opt.icon}
                    </span>
                    <span style={{
                      fontSize: '11px', fontWeight: 700,
                      color: selected ? '#0284c7' : 'white',
                    }}>{opt.label}</span>
                    <span style={{
                      fontSize: '9px', lineHeight: 1.3,
                      color: selected ? 'rgba(2,132,199,0.7)' : 'rgba(255,255,255,0.55)',
                    }}>{opt.desc}</span>
                  </button>
                );
              })}
            </div>
            <p className="text-xs text-white/45 mt-1.5">
              {downloadPerm === 'CAN_DOWNLOAD'
                ? 'Recipient can preview AND download the files.'
                : downloadPerm === 'VIEW_ONLY'
                  ? 'Recipient can preview only — no downloads.'
                  : 'Only admins of the target can download. Others get view-only access.'}
            </p>
          </div>
            );
          })()}

          {/* ── Selected target preview ── */}
          {target && (
            <div className="mx-6 mb-4 px-4 py-3 rounded-xl flex items-center gap-3"
                 style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.25)' }}>
              {target.type === 'GROUP'
                ? <div className="w-8 h-8 rounded-xl flex-shrink-0 bg-white/20
                                  flex items-center justify-center overflow-hidden text-base">
                    {target.photoUrl
                      ? <img src={target.photoUrl} alt=""
                             className="w-full h-full object-cover"
                             onError={e => { e.currentTarget.style.display='none'; }}/>
                      : '👥'}
                  </div>
                : <Avatar name={target.name} photoUrl={target.photoUrl} size="sm"/>
              }
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-white truncate">{target.name}</p>
                <p className="text-xs text-white/50">
                  {count} file{count !== 1 ? 's' : ''} ·{' '}
                  {downloadPerm === 'CAN_DOWNLOAD'
                    ? '⬇ Anyone'
                    : downloadPerm === 'VIEW_ONLY'
                      ? '👁 View only'
                      : '🛡 Admins'}
                </p>
              </div>
              <button onClick={() => setTarget(null)}
                      className="text-white/40 hover:text-white text-xs">✕</button>
            </div>
          )}

          {/* ── Action buttons ── */}
          <div className="px-6 pb-6 flex gap-3">
            <button onClick={onClose}
                    className="flex-1 py-2.5 rounded-xl font-semibold text-sm text-white
                               transition-all"
                    style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.2)' }}>
              Cancel
            </button>
            <button onClick={handleShare}
                    disabled={!target || sharing_}
                    className="flex-1 py-2.5 rounded-xl font-bold text-sm text-sky-700
                               bg-white shadow-lg hover:bg-sky-50 disabled:opacity-50
                               disabled:cursor-not-allowed transition-all active:scale-95">
              {sharing_ ? '⏳ Sharing…' : `🔗 Share`}
            </button>
          </div>

        </div>{/* end relative */}
      </div>
    </div>
  );
}
