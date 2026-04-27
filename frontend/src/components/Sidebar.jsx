import { useState, useEffect, useCallback, useRef } from 'react';
import { conversations, users, connections, storage } from '../services/api';
import { subscribeToUserNotifications } from '../services/socket';
import { useAuth } from '../context/AuthContext';
import { formatDistanceToNow } from 'date-fns';
import toast from 'react-hot-toast';
import NewGroupModal            from './NewGroupModal';
import ProfileModal             from './ProfileModal';
import ConnectionRequestsModal  from './ConnectionRequestsModal';
import StorageModal             from './StorageModal';
import Avatar                   from './Avatar';

// Sentinel object representing the "Shared with Me" virtual view
export const SHARED_WITH_ME_VIEW = Object.freeze({ id: '__shared_with_me__', type: 'SHARED_WITH_ME' });

function fmtStorage(bytes) {
  if (!bytes) return '0 B';
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(0)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
}

function initials(name) {
  if (!name) return '?';
  const p = name.trim().split(' ');
  return p.length >= 2 ? (p[0][0] + p[1][0]).toUpperCase() : p[0][0].toUpperCase();
}

function fileIcon(category) {
  switch (category) {
    case 'IMAGE':    return '🖼';
    case 'VIDEO':    return '🎬';
    case 'DOCUMENT': return '📄';
    case 'AUDIO':    return '🎵';
    case 'ARCHIVE':  return '🗜';
    default:         return '📎';
  }
}

// ── Connection status helpers ─────────────────────────────────────────────────

function ConnectionChip({ status }) {
  const map = {
    CONNECTED:        { label: 'Connected',     cls: 'bg-green-100 text-green-700' },
    PENDING_SENT:     { label: 'Request Sent',  cls: 'bg-amber-100 text-amber-700' },
    PENDING_RECEIVED: { label: 'Wants to connect', cls: 'bg-sky-100 text-sky-700' },
    BLOCKED_BY_ME:    { label: 'Blocked',       cls: 'bg-red-100   text-red-600'   },
    SELF:             { label: 'You',           cls: 'bg-slate-100 text-slate-500' },
  };
  const m = map[status];
  if (!m) return null;
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${m.cls}`}>
      {m.label}
    </span>
  );
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function Sidebar({ selected, onSelect }) {
  const { currentUser, logout } = useAuth();

  const [convList,         setConvList]         = useState([]);
  const [search,           setSearch]           = useState('');
  const [searchRes,        setSearchRes]        = useState([]);
  const [searching,        setSearching]        = useState(false);
  const [showGroup,        setShowGroup]        = useState(false);
  const [showProfile,      setShowProfile]      = useState(false);
  const [showConnections,  setShowConnections]  = useState(false);
  const [myStorage,        setMyStorage]        = useState(null);
  const [loadingStorage,   setLoadingStorage]   = useState(false);
  const [pendingCount,     setPendingCount]     = useState(0);
  const [storageData,      setStorageData]      = useState(null);
  const [showStorage,      setShowStorage]      = useState(false);
  // Unread badges
  const [unreadConvIds,    setUnreadConvIds]    = useState(new Set()); // convIds with new unseen files
  const [unreadShares,     setUnreadShares]     = useState(0);         // new shares received
  // Per-user action loading: { [userId]: 'sending'|'accepting'|'cancelling'|'rejecting' }
  const [actionLoading,    setActionLoading]    = useState({});

  // Always-current ref to selected — used inside the WS callback to avoid stale closures
  const selectedRef = useRef(selected);
  useEffect(() => { selectedRef.current = selected; }, [selected]);

  // ── Load conversations + storage on mount ───────────────────────────────
  useEffect(() => {
    conversations.list().then(r => setConvList(r.data)).catch(console.error);
    storage.usage()
      .then(r => setStorageData(r.data))
      .catch(e => {
        console.error('[Storage]', e);
        // Show placeholder so the bar is always visible
        setStorageData({ usedBytes: 0, limitBytes: 5368709120, usedPercent: 0 });
      });
  }, []);

  // ── Refresh pending count ────────────────────────────────────────────────
  const refreshPendingCount = useCallback(() => {
    connections.receivedRequests()
      .then(r => setPendingCount(r.data.length))
      .catch(() => {});
  }, []);

  useEffect(() => { refreshPendingCount(); }, []);

  // ── Real-time notifications ───────────────────────────────────────────────
  useEffect(() => {
    if (!currentUser?.id) return;

    const unsub = subscribeToUserNotifications(currentUser.id, event => {
      const p = event.payload ?? {};

      // ── New file uploaded in a conversation ──
      if (event.type === 'NEW_FILE') {
        const cid = p.conversationId;
        // Show a toast
        toast(`📂 ${p.senderName} shared "${p.fileName}"`,
              { duration: 5000, icon: '📁' });
        // Mark this conversation as having unread files (only if not currently open)
        setUnreadConvIds(prev => {
          if (selectedRef.current?.id === cid) return prev; // already open — no badge
          const next = new Set(prev);
          next.add(cid);
          return next;
        });
        // Refresh the conversation list so the lastFile preview updates
        conversations.list().then(r => setConvList(r.data)).catch(console.error);
      }

      // ── A file was shared directly with the user or via a group ──
      else if (event.type === 'FILE_SHARED') {
        const count = p.fileCount ?? 1;
        if (p.groupName) {
          toast(`🔗 ${p.senderName} shared ${count} file${count !== 1 ? 's' : ''} with group "${p.groupName}"`,
                { duration: 6000, icon: '👥' });
        } else {
          toast(`🔗 ${p.senderName} shared "${p.fileName}" with you`,
                { duration: 6000, icon: '🔗' });
        }
        setUnreadShares(prev => prev + count);
      }

      // ── Connection request received ──
      else if (event.type === 'CONNECTION_REQUEST') {
        setPendingCount(prev => prev + 1);
        toast(`🤝 ${p.senderName} wants to connect!`, { duration: 6000 });
      }

      // ── Connection request accepted ──
      else if (event.type === 'CONNECTION_ACCEPTED') {
        toast.success(`🎉 ${p.receiverName} accepted your request!`);
        conversations.list().then(r => setConvList(r.data)).catch(console.error);
      }
    });

    return unsub;
  }, [currentUser?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── User search ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (search.trim().length < 2) { setSearchRes([]); return; }
    const t = setTimeout(() => {
      setSearching(true);
      users.search(search.trim())
        .then(r => setSearchRes(r.data))
        .catch(console.error)
        .finally(() => setSearching(false));
    }, 350);
    return () => clearTimeout(t);
  }, [search]);

  // ── Conversation open (clears unread badge) ─────────────────────────────
  const openDirect = async (userId) => {
    try {
      const { data } = await conversations.openDirect(userId);
      setConvList(prev => prev.find(c => c.id === data.id) ? prev : [data, ...prev]);
      onSelect(data);
      setUnreadConvIds(prev => { const s = new Set(prev); s.delete(data.id); return s; });
      setSearch('');
    } catch (e) { toast.error(e?.toString() || 'Cannot open conversation'); }
  };

  const handleGroupCreated = (conv) => {
    setConvList(prev => [conv, ...prev]);
    onSelect(conv);
  };

  const openMyStorage = async () => {
    if (myStorage) { onSelect(myStorage); return; }
    setLoadingStorage(true);
    try {
      const { data } = await conversations.personal();
      setMyStorage(data);
      onSelect(data);
    } catch (e) {
      console.error('[MyStorage]', e);
      toast.error('Could not open My Storage: ' + (e?.message || e));
    } finally { setLoadingStorage(false); }
  };

  // ── Connection actions ───────────────────────────────────────────────────
  const setLoading = (userId, state) =>
    setActionLoading(prev => ({ ...prev, [userId]: state }));

  const handleSendRequest = async (user) => {
    setLoading(user.id, 'sending');
    try {
      const { data } = await connections.sendRequest(user.id);
      // Update search result in-place
      setSearchRes(prev => prev.map(u =>
        u.id === user.id
          ? { ...u, connectionStatus: 'PENDING_SENT', connectionRequestId: data.id }
          : u));
      toast.success(`Request sent to ${user.displayName}`);
    } catch (e) { toast.error(e?.toString() || 'Failed to send request'); }
    finally { setLoading(user.id, null); }
  };

  const handleAcceptInSearch = async (user) => {
    setLoading(user.id, 'accepting');
    try {
      await connections.accept(user.connectionRequestId);
      setSearchRes(prev => prev.map(u =>
        u.id === user.id ? { ...u, connectionStatus: 'CONNECTED', connectionRequestId: null } : u));
      setPendingCount(prev => Math.max(0, prev - 1));
      toast.success(`Connected with ${user.displayName}!`);
    } catch (e) { toast.error(e?.toString() || 'Failed to accept'); }
    finally { setLoading(user.id, null); }
  };

  const handleCancelInSearch = async (user) => {
    setLoading(user.id, 'cancelling');
    try {
      await connections.cancel(user.connectionRequestId);
      setSearchRes(prev => prev.map(u =>
        u.id === user.id ? { ...u, connectionStatus: 'NONE', connectionRequestId: null } : u));
    } catch (e) { toast.error(e?.toString() || 'Failed to cancel'); }
    finally { setLoading(user.id, null); }
  };

  const handleBlockInSearch = async (user) => {
    if (!window.confirm(`Block ${user.displayName}? They won't be able to see you or contact you.`)) return;
    try {
      await connections.block(user.id);
      setSearchRes(prev => prev.filter(u => u.id !== user.id));
      toast.success(`${user.displayName} blocked.`);
    } catch (e) { toast.error(e?.toString() || 'Failed to block'); }
  };

  // ── Render ───────────────────────────────────────────────────────────────
  return (
    <div className="w-80 flex-shrink-0 bg-white border-r border-slate-200 flex flex-col h-screen">

      {/* ── Top bar ── */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-slate-100">
        <div className="flex items-center">
          <img src="/logo.png" alt="Magizhchi Box"
               className="h-9 select-none"
               onError={e => {
                 e.currentTarget.style.display = 'none';
                 document.getElementById('logo-fallback-sidebar').style.display = 'flex';
               }}/>
          <div id="logo-fallback-sidebar"
               className="hidden items-center gap-2">
            <span className="text-xl">📂</span>
            <span className="font-bold text-slate-800">Magizhchi Box</span>
          </div>
        </div>
        <div className="flex items-center gap-1">

          {/* Connection requests bell */}
          <button onClick={() => setShowConnections(true)}
                  className="relative p-2 rounded-xl hover:bg-slate-100 text-slate-500 text-lg"
                  title="Connection Requests">
            🔔
            {pendingCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px]
                               bg-red-500 text-white text-[10px] font-bold rounded-full
                               flex items-center justify-center px-1">
                {pendingCount > 9 ? '9+' : pendingCount}
              </span>
            )}
          </button>

          <button onClick={() => setShowGroup(true)}
                  className="p-2 rounded-xl hover:bg-slate-100 text-slate-500 text-lg"
                  title="New Group">👥</button>

          <button onClick={() => setShowProfile(true)} title="Profile"
                  className="rounded-full focus:outline-none focus:ring-2 focus:ring-sky-400">
            <Avatar name={currentUser?.displayName}
                    photoUrl={currentUser?.profilePhotoUrl}
                    size="sm"/>
          </button>
        </div>
      </div>

      {/* ── My Storage ── */}
      <div className="px-3 pt-3 pb-2">
        <button onClick={openMyStorage} disabled={loadingStorage}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-2xl border
                            transition-all group
                            ${selected?.type === 'PERSONAL'
                              ? 'bg-sky-500 border-sky-500 shadow-md'
                              : 'bg-gradient-to-r from-sky-50 to-indigo-50 border-sky-100 hover:border-sky-300 hover:shadow-sm'}`}>
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center
                           text-xl flex-shrink-0 shadow-sm
                           ${selected?.type === 'PERSONAL' ? 'bg-white/20' : 'bg-white border border-sky-100'}`}>
            {loadingStorage ? <span className="animate-spin text-sm">⏳</span> : '🗄️'}
          </div>
          <div className="flex-1 min-w-0 text-left">
            <p className={`text-sm font-bold truncate
                           ${selected?.type === 'PERSONAL' ? 'text-white' : 'text-slate-800'}`}>
              My Storage
            </p>
            <p className={`text-xs truncate
                           ${selected?.type === 'PERSONAL' ? 'text-white/70' : 'text-slate-400'}`}>
              {currentUser?.displayName
                ? `${currentUser.displayName.split(' ')[0]}'s personal space`
                : 'Your personal file space'}
            </p>
          </div>
          <span className={`text-xs flex-shrink-0 transition-transform group-hover:translate-x-0.5
                            ${selected?.type === 'PERSONAL' ? 'text-white/70' : 'text-sky-400'}`}>›</span>
        </button>
      </div>

      {/* ── Shared with Me ── */}
      <div className="px-3 pb-2">
        <button onClick={() => { onSelect(SHARED_WITH_ME_VIEW); setUnreadShares(0); }}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-2xl border
                            transition-all group
                            ${selected?.type === 'SHARED_WITH_ME'
                              ? 'bg-violet-500 border-violet-500 shadow-md'
                              : 'bg-gradient-to-r from-violet-50 to-purple-50 border-violet-100 hover:border-violet-300 hover:shadow-sm'}`}>
          <div className={`w-10 h-10 rounded-xl flex items-center justify-center
                           text-xl flex-shrink-0 shadow-sm
                           ${selected?.type === 'SHARED_WITH_ME' ? 'bg-white/20' : 'bg-white border border-violet-100'}`}>
            🔗
          </div>
          <div className="flex-1 min-w-0 text-left">
            <p className={`text-sm font-bold truncate
                           ${selected?.type === 'SHARED_WITH_ME' ? 'text-white' : 'text-slate-800'}`}>
              Shared with Me
            </p>
            <p className={`text-xs truncate
                           ${selected?.type === 'SHARED_WITH_ME' ? 'text-white/70' : 'text-slate-400'}`}>
              {unreadShares > 0 && selected?.type !== 'SHARED_WITH_ME'
                ? `${unreadShares} new file${unreadShares !== 1 ? 's' : ''} shared`
                : 'Files others have shared'}
            </p>
          </div>
          {unreadShares > 0 && selected?.type !== 'SHARED_WITH_ME' && (
            <span className="flex-shrink-0 min-w-[20px] h-5 rounded-full
                             bg-violet-500 text-white text-[10px] font-bold
                             flex items-center justify-center px-1">
              {unreadShares > 9 ? '9+' : unreadShares}
            </span>
          )}
          {(unreadShares === 0 || selected?.type === 'SHARED_WITH_ME') && (
            <span className={`text-xs flex-shrink-0 transition-transform group-hover:translate-x-0.5
                              ${selected?.type === 'SHARED_WITH_ME' ? 'text-white/70' : 'text-violet-400'}`}>›</span>
          )}
        </button>
      </div>

      {/* ── Search ── */}
      <div className="px-3 pb-2 border-b border-slate-100">
        <input className="input !py-2 !text-sm"
               placeholder="Search users by name, phone or email…"
               value={search}
               onChange={e => setSearch(e.target.value)}/>
      </div>

      {/* ── Results ── */}
      <div className="flex-1 overflow-y-auto">

        {search.trim().length >= 2 ? (
          /* ── User search results ── */
          <div>
            {searching && <p className="text-xs text-slate-400 px-4 py-2">Searching…</p>}
            {!searching && searchRes.length === 0 && (
              <p className="text-sm text-slate-400 px-4 py-4 text-center">No users found</p>
            )}
            {searchRes.map(u => {
              const busy = actionLoading[u.id];
              return (
                <div key={u.id} className="px-4 py-3 border-b border-slate-50 hover:bg-slate-50">

                  {/* User row */}
                  <div className="flex items-center gap-3">
                    <Avatar name={u.displayName} photoUrl={u.profilePhotoUrl} size="md"/>

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="font-semibold text-sm text-slate-800">{u.displayName}</p>
                        <ConnectionChip status={u.connectionStatus}/>
                      </div>
                      {u.mobileNumber && (
                        <p className="text-xs text-slate-400 truncate">{u.mobileNumber}</p>
                      )}
                    </div>
                  </div>

                  {/* Action buttons row */}
                  <div className="mt-2 flex gap-2 flex-wrap">

                    {/* CONNECTED — open conversation */}
                    {u.connectionStatus === 'CONNECTED' && (
                      <button onClick={() => openDirect(u.id)}
                              className="text-xs px-3 py-1.5 rounded-lg bg-sky-500 text-white
                                         font-semibold hover:bg-sky-600 transition-colors">
                        📁 Open Shared Space
                      </button>
                    )}

                    {/* PENDING_SENT — cancel */}
                    {u.connectionStatus === 'PENDING_SENT' && (
                      <button onClick={() => handleCancelInSearch(u)} disabled={!!busy}
                              className="text-xs px-3 py-1.5 rounded-lg bg-amber-50 text-amber-700
                                         font-semibold hover:bg-amber-100 transition-colors
                                         disabled:opacity-60">
                        {busy === 'cancelling' ? '⏳ Cancelling…' : '✕ Cancel Request'}
                      </button>
                    )}

                    {/* PENDING_RECEIVED — accept or reject */}
                    {u.connectionStatus === 'PENDING_RECEIVED' && (
                      <>
                        <button onClick={() => handleAcceptInSearch(u)} disabled={!!busy}
                                className="text-xs px-3 py-1.5 rounded-lg bg-sky-500 text-white
                                           font-semibold hover:bg-sky-600 transition-colors
                                           disabled:opacity-60">
                          {busy === 'accepting' ? '⏳' : '✅ Accept'}
                        </button>
                        <button onClick={() => connections.reject(u.connectionRequestId)
                                               .then(() => setSearchRes(prev =>
                                                 prev.map(x => x.id === u.id
                                                   ? { ...x, connectionStatus: 'NONE', connectionRequestId: null }
                                                   : x)))
                                               .catch(() => {})}
                                className="text-xs px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600
                                           font-semibold hover:bg-slate-200 transition-colors">
                          ✕ Decline
                        </button>
                      </>
                    )}

                    {/* NONE — send request */}
                    {u.connectionStatus === 'NONE' && (
                      <button onClick={() => handleSendRequest(u)} disabled={!!busy}
                              className="text-xs px-3 py-1.5 rounded-lg bg-sky-50 text-sky-700
                                         font-semibold hover:bg-sky-100 transition-colors
                                         disabled:opacity-60">
                        {busy === 'sending' ? '⏳ Sending…' : '🤝 Send Request'}
                      </button>
                    )}

                    {/* Block (available for CONNECTED and NONE, not for SELF) */}
                    {(u.connectionStatus === 'CONNECTED' || u.connectionStatus === 'NONE') && (
                      <button onClick={() => handleBlockInSearch(u)}
                              className="text-xs px-3 py-1.5 rounded-lg bg-red-50 text-red-500
                                         font-semibold hover:bg-red-100 transition-colors">
                        🚫 Block
                      </button>
                    )}

                    {/* BLOCKED_BY_ME — show unblock via modal only */}
                    {u.connectionStatus === 'BLOCKED_BY_ME' && (
                      <button onClick={() => setShowConnections(true)}
                              className="text-xs px-3 py-1.5 rounded-lg bg-slate-100 text-slate-500
                                         font-semibold hover:bg-slate-200 transition-colors">
                        Manage Block
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* ── Conversation list ── */
          <div>
            {convList.length === 0 && (
              <div className="text-center py-12 px-4">
                <div className="text-4xl mb-2">💬</div>
                <p className="text-sm text-slate-400">No conversations yet.</p>
                <p className="text-xs text-slate-300 mt-1">
                  Search for a user to send a connection request.
                </p>
              </div>
            )}
            {convList.map(conv => {
              const last    = conv.lastFile;
              const hasNew  = unreadConvIds.has(conv.id);
              return (
                <button key={conv.id}
                        onClick={() => {
                          onSelect(conv);
                          setUnreadConvIds(prev => { const s = new Set(prev); s.delete(conv.id); return s; });
                        }}
                        className={`conv-row w-full text-left
                                    ${selected?.id === conv.id ? 'active' : ''}`}>
                  {conv.type === 'GROUP'
                    ? <div className="w-11 h-11 avatar rounded-xl text-sm flex-shrink-0
                                      overflow-hidden">
                        {conv.iconUrl
                          ? <img src={conv.iconUrl} alt=""
                                 className="w-full h-full object-cover rounded-xl"
                                 onError={e => { e.target.style.display = 'none'; }}/>
                          : '👥'}
                      </div>
                    : <Avatar name={conv.name} photoUrl={conv.iconUrl} size="lg"/>
                  }
                  <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-baseline gap-1">
                      <p className={`font-semibold text-sm truncate
                                     ${hasNew ? 'text-sky-700' : 'text-slate-800'}`}>
                        {conv.name}
                      </p>
                      <div className="flex items-center gap-1 flex-shrink-0 ml-1">
                        {hasNew && (
                          <span className="w-2 h-2 rounded-full bg-sky-500 flex-shrink-0"/>
                        )}
                        {last?.sentAt && (
                          <span className="text-xs text-slate-400">
                            {formatDistanceToNow(new Date(last.sentAt), { addSuffix: false })}
                          </span>
                        )}
                      </div>
                    </div>
                    {last
                      ? <p className={`text-xs truncate
                                       ${hasNew ? 'text-sky-600 font-semibold' : 'text-slate-400'}`}>
                          {fileIcon(last.category)}&nbsp;{last.originalFileName}
                        </p>
                      : <p className="text-xs text-slate-300 italic">No files yet</p>
                    }
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* ── Storage bar ── */}
      {storageData && (() => {
        const pct = storageData.usedPercent;
        const barColor = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-amber-500' : 'bg-green-500';
        const textColor = pct >= 90 ? 'text-red-500' : pct >= 70 ? 'text-amber-500' : 'text-green-600';
        return (
          <button onClick={() => setShowStorage(true)}
                  className="mx-3 mb-2 px-3 py-2.5 rounded-xl border border-slate-100
                             bg-slate-50 hover:bg-sky-50 hover:border-sky-200
                             transition-all text-left group w-[calc(100%-24px)]"
                  title="Click to see storage breakdown">
            <div className="flex items-center justify-between mb-1.5">
              <span className="text-xs font-semibold text-slate-600">
                💾 Storage
              </span>
              <span className={`text-xs font-bold ${textColor}`}>
                {fmtStorage(storageData.usedBytes)} / {fmtStorage(storageData.limitBytes)}
              </span>
            </div>
            <div className="w-full h-1.5 bg-slate-200 rounded-full overflow-hidden">
              <div className={`h-full rounded-full transition-all ${barColor}`}
                   style={{ width: `${Math.min(100, pct)}%` }}/>
            </div>
            {pct >= 90 && (
              <p className="text-xs text-red-500 font-semibold mt-1">
                🚨 Almost full — upgrade your plan
              </p>
            )}
          </button>
        );
      })()}

      {/* ── Bottom profile bar ── */}
      <div className="px-4 py-3 border-t border-slate-100 flex items-center gap-3">
        <Avatar name={currentUser?.displayName}
                photoUrl={currentUser?.profilePhotoUrl}
                size="sm"/>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold text-slate-800 truncate">
            {currentUser?.displayName}
          </p>
        </div>
        <button onClick={logout}
                className="text-xs text-slate-400 hover:text-red-500 transition-colors">
          Sign Out
        </button>
      </div>

      {/* Modals */}
      {showGroup      && <NewGroupModal onClose={() => setShowGroup(false)}
                                        onCreated={handleGroupCreated}/>}
      {showProfile    && <ProfileModal  onClose={() => setShowProfile(false)}/>}
      {showStorage    && <StorageModal  onClose={() => setShowStorage(false)}/>}
      {showConnections && (
        <ConnectionRequestsModal
          onClose={() => { setShowConnections(false); refreshPendingCount(); }}
          onConnectionChanged={() => {
            refreshPendingCount();
            conversations.list().then(r => setConvList(r.data)).catch(console.error);
          }}
        />
      )}
    </div>
  );
}
