import { useState, useEffect, useCallback, useRef } from 'react';
import { conversations, users, connections, storage, files as filesApi } from '../services/api';
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

// ── Missed-notification helpers ───────────────────────────────────────────────

const LS_KEY = 'msh:lastOpened'; // { [convId]: ISO-timestamp }

function getLastOpened() {
  try { return JSON.parse(localStorage.getItem(LS_KEY) || '{}'); }
  catch { return {}; }
}
function markOpened(convId) {
  try {
    const map = getLastOpened();
    map[convId] = new Date().toISOString();
    localStorage.setItem(LS_KEY, JSON.stringify(map));
  } catch { /* storage full — ignore */ }
}

// ── Main Component ────────────────────────────────────────────────────────────

export default function Sidebar({ selected, onSelect, refreshSignal = 0 }) {
  const { currentUser, logout } = useAuth();

  const [convList,         setConvList]         = useState([]);
  const [search,           setSearch]           = useState('');
  const [searchMode,       setSearchMode]       = useState('people'); // 'people' | 'files'
  const [searchRes,        setSearchRes]        = useState([]);
  const [fileSearchRes,    setFileSearchRes]    = useState([]);
  const [searching,        setSearching]        = useState(false);
  const [showGroup,        setShowGroup]        = useState(false);
  const [showProfile,      setShowProfile]      = useState(false);
  const [showConnections,  setShowConnections]  = useState(false);
  const [myStorage,        setMyStorage]        = useState(null);
  const [loadingStorage,   setLoadingStorage]   = useState(false);
  const [pendingCount,     setPendingCount]     = useState(0);
  const [storageData,      setStorageData]      = useState(null);
  const [showStorage,      setShowStorage]      = useState(false);
  // Unread badges — Map<convId, fileCount> so we can show "5 files received"
  const [unreadCounts,     setUnreadCounts]     = useState(new Map()); // convId → unread file count
  const [unreadShares,     setUnreadShares]     = useState(0);         // new shares received
  // Per-user action loading: { [userId]: 'sending'|'accepting'|'cancelling'|'rejecting' }
  const [actionLoading,    setActionLoading]    = useState({});

  // Always-current ref to selected — used inside the WS callback to avoid stale closures
  const selectedRef = useRef(selected);
  useEffect(() => { selectedRef.current = selected; }, [selected]);

  // ── Load conversations + storage on mount ───────────────────────────────
  useEffect(() => {
    conversations.list().then(r => {
      const list = r.data;
      setConvList(list);

      // ── Seed unread badges for files received while offline / page was closed ──
      // Compare each conversation's lastFile.sentAt against the timestamp we stored
      // the last time the user opened that conversation.  Any file newer than that
      // timestamp (and not sent by the current user) is counted as unread.
      if (currentUser?.id) {
        const lastOpened = getLastOpened();
        const initialCounts = new Map();
        for (const conv of list) {
          const lf = conv.lastFile;
          if (!lf?.sentAt) continue;                              // no files at all
          if (lf.senderId === currentUser.id) continue;           // own upload — skip
          const openedAt = lastOpened[conv.id];
          if (!openedAt || new Date(lf.sentAt) > new Date(openedAt)) {
            // Missed at least one file — mark as 1 (we can't know the exact count
            // without an extra API call, so 1 is the safe minimum)
            initialCounts.set(conv.id, 1);
          }
        }
        if (initialCounts.size > 0) {
          setUnreadCounts(prev => {
            const merged = new Map(prev);
            initialCounts.forEach((v, k) => {
              // Only set if there's no higher real-time count already
              if (!merged.has(k)) merged.set(k, v);
            });
            return merged;
          });
        }
      }
    }).catch(console.error);

    storage.usage()
      .then(r => setStorageData(r.data))
      .catch(e => {
        console.error('[Storage]', e);
        // Show placeholder so the bar is always visible
        setStorageData({ usedBytes: 0, limitBytes: 5368709120, usedPercent: 0 });
      });
  }, [currentUser?.id, refreshSignal]); // re-run if user changes or forced refresh

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
        // Determine how many files this notification represents.
        // Folder uploads send one summary: e.g. "📁 MyFolder (5 files)"
        const folderMatch = String(p.fileName ?? '').match(/\((\d+) files?\)/i);
        const incoming = folderMatch ? parseInt(folderMatch[1], 10) : 1;
        // Increment the per-conversation count (skip if the conversation is open right now)
        if (selectedRef.current?.id === cid) {
          // Conversation is open — advance the "last opened" timestamp so that
          // a subsequent page refresh doesn't re-show the badge for this file.
          markOpened(cid);
        } else {
          setUnreadCounts(prev => {
            const next = new Map(prev);
            next.set(cid, (next.get(cid) ?? 0) + incoming);
            return next;
          });
        }
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
    if (searchMode !== 'people') return;
    if (search.trim().length < 2) { setSearchRes([]); return; }
    const t = setTimeout(() => {
      setSearching(true);
      users.search(search.trim())
        .then(r => setSearchRes(r.data))
        .catch(console.error)
        .finally(() => setSearching(false));
    }, 350);
    return () => clearTimeout(t);
  }, [search, searchMode]);

  // ── File search ───────────────────────────────────────────────────────────
  useEffect(() => {
    if (searchMode !== 'files') return;
    if (search.trim().length < 2) { setFileSearchRes([]); return; }
    const t = setTimeout(() => {
      setSearching(true);
      filesApi.search(search.trim())
        .then(r => setFileSearchRes(r.data))
        .catch(console.error)
        .finally(() => setSearching(false));
    }, 350);
    return () => clearTimeout(t);
  }, [search, searchMode]);

  // ── Conversation open (clears unread badge + records open time) ─────────
  const openDirect = async (userId) => {
    try {
      const { data } = await conversations.openDirect(userId);
      setConvList(prev => prev.find(c => c.id === data.id) ? prev : [data, ...prev]);
      onSelect(data);
      markOpened(data.id);
      setUnreadCounts(prev => { const m = new Map(prev); m.delete(data.id); return m; });
      setSearch('');
    } catch (e) { toast.error(e?.toString() || 'Cannot open conversation'); }
  };

  const handleGroupCreated = (conv) => {
    setConvList(prev => [conv, ...prev]);
    onSelect(conv);
  };

  const openMyStorage = async () => {
    if (myStorage) {
      onSelect(myStorage);
      markOpened(myStorage.id);
      setUnreadCounts(prev => { const m = new Map(prev); m.delete(myStorage.id); return m; });
      return;
    }
    setLoadingStorage(true);
    try {
      const { data } = await conversations.personal();
      setMyStorage(data);
      onSelect(data);
      markOpened(data.id);
      setUnreadCounts(prev => { const m = new Map(prev); m.delete(data.id); return m; });
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
    <div className="w-80 flex-shrink-0 border-r border-sky-100 flex flex-col h-screen"
         style={{
           backgroundColor: '#e0f2fe',
           backgroundImage:
             'linear-gradient(rgba(255,255,255,0.55) 1px, transparent 1px),' +
             'linear-gradient(90deg, rgba(255,255,255,0.55) 1px, transparent 1px)',
           backgroundSize: '22px 22px',
         }}>

      {/* ── Top bar ── */}
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-sky-100 bg-white/60 backdrop-blur-sm">

        {/* Left: avatar + welcome */}
        <button onClick={() => setShowProfile(true)} title="Edit Profile"
                className="flex items-center gap-2.5 min-w-0 focus:outline-none group">
          {/* Profile pic */}
          <div className="w-10 h-10 rounded-full flex-shrink-0 overflow-hidden
                          ring-2 ring-sky-300 ring-offset-1
                          group-hover:ring-sky-500 transition-all">
            {currentUser?.profilePhotoUrl ? (
              <img src={currentUser.profilePhotoUrl}
                   alt={currentUser.displayName ?? ''}
                   className="w-full h-full object-cover"
                   onError={e => { e.currentTarget.style.display = 'none'; e.currentTarget.nextSibling.style.display = 'flex'; }}/>
            ) : null}
            {/* initials fallback — always rendered, hidden when photo loads */}
            <div className={`w-full h-full bg-gradient-to-br from-sky-400 to-sky-600
                             flex items-center justify-center text-white text-sm font-bold
                             ${currentUser?.profilePhotoUrl ? 'hidden' : 'flex'}`}>
              {currentUser?.displayName
                ? currentUser.displayName.trim().split(/\s+/).slice(0,2).map(w => w[0]).join('').toUpperCase()
                : '?'}
            </div>
          </div>
          {/* Welcome text */}
          <div className="min-w-0 text-left">
            <p className="text-[10px] text-slate-400 leading-tight">Welcome back 👋</p>
            <p className="text-sm font-bold text-slate-800 truncate leading-tight">
              {currentUser?.displayName?.split(' ')[0] ?? 'User'}
            </p>
          </div>
        </button>

        {/* Right: action buttons */}
        <div className="flex items-center gap-0.5">
          <button onClick={() => setShowConnections(true)}
                  className="relative p-2 rounded-xl hover:bg-sky-100 text-slate-500 text-lg transition-colors"
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
                  className="p-2 rounded-xl hover:bg-sky-100 text-slate-500 text-lg transition-colors"
                  title="New Group">👥</button>
        </div>
      </div>

      {/* ── My Storage + Shared Files (side by side) ── */}
      <div className="px-3 pt-3 pb-2 flex gap-2 overflow-visible">

        {/* My Storage */}
        <button onClick={openMyStorage} disabled={loadingStorage}
                className="flex-1 flex items-center gap-2.5 px-3 py-2.5 rounded-2xl border transition-all group"
                style={selected?.type === 'PERSONAL' ? {
                  background: 'rgba(14,130,210,0.90)',
                  backdropFilter: 'blur(12px)',
                  WebkitBackdropFilter: 'blur(12px)',
                  borderColor: 'rgba(255,255,255,0.40)',
                  boxShadow: '0 4px 16px rgba(14,130,210,0.50)',
                } : {
                  background: 'rgba(14,130,210,0.42)',
                  backdropFilter: 'blur(12px)',
                  WebkitBackdropFilter: 'blur(12px)',
                  borderColor: 'rgba(255,255,255,0.18)',
                  boxShadow: '0 2px 8px rgba(14,130,210,0.22)',
                }}>
          <div className="w-8 h-8 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
               style={{ background: selected?.type === 'PERSONAL' ? 'rgba(255,255,255,0.20)' : 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.15)' }}>
            {loadingStorage ? <span className="animate-spin text-sm">⏳</span> : '🗄️'}
          </div>
          <div className="min-w-0 text-left">
            <p className="text-xs font-bold leading-tight truncate text-slate-900">My Storage</p>
            <p className="text-[10px] leading-tight mt-0.5 truncate text-slate-600">
              {currentUser?.displayName
                ? `${currentUser.displayName.split(' ')[0]}'s space`
                : 'Personal space'}
            </p>
          </div>
        </button>

        {/* Shared Files */}
        <button onClick={() => { onSelect(SHARED_WITH_ME_VIEW); setUnreadShares(0); }}
                className="flex-1 flex items-center gap-2.5 px-3 py-2.5 relative rounded-2xl border transition-all group"
                style={selected?.type === 'SHARED_WITH_ME' ? {
                  background: 'rgba(124,58,237,0.90)',
                  backdropFilter: 'blur(12px)',
                  WebkitBackdropFilter: 'blur(12px)',
                  borderColor: 'rgba(255,255,255,0.40)',
                  boxShadow: '0 4px 16px rgba(124,58,237,0.50)',
                } : {
                  background: 'rgba(124,58,237,0.42)',
                  backdropFilter: 'blur(12px)',
                  WebkitBackdropFilter: 'blur(12px)',
                  borderColor: 'rgba(255,255,255,0.18)',
                  boxShadow: '0 2px 8px rgba(124,58,237,0.22)',
                }}>
          {/* unread badge */}
          {unreadShares > 0 && selected?.type !== 'SHARED_WITH_ME' && (
            <span className="absolute -top-1.5 -right-1.5 min-w-[18px] h-[18px] rounded-full
                             bg-violet-500 text-white text-[9px] font-bold
                             flex items-center justify-center px-1 shadow-sm">
              {unreadShares > 9 ? '9+' : unreadShares}
            </span>
          )}
          <div className="w-8 h-8 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
               style={{ background: selected?.type === 'SHARED_WITH_ME' ? 'rgba(255,255,255,0.20)' : 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.15)' }}>
            🔗
          </div>
          <div className="min-w-0 text-left">
            <p className="text-xs font-bold leading-tight truncate text-slate-900">Shared Files</p>
            <p className="text-[10px] leading-tight mt-0.5 truncate text-slate-600">
              {unreadShares > 0 && selected?.type !== 'SHARED_WITH_ME'
                ? `${unreadShares} new file${unreadShares !== 1 ? 's' : ''}`
                : 'Shared files'}
            </p>
          </div>
        </button>

      </div>

      {/* ── Search ── */}
      <div className="px-3 pb-2 border-b border-sky-100 space-y-2">
        {/* Mode toggle */}
        <div className="flex gap-1 p-0.5 rounded-xl" style={{ background: 'rgba(14,130,210,0.35)', border: '1px solid rgba(255,255,255,0.14)' }}>
          {[
            { key: 'people', icon: '👤', label: 'People' },
            { key: 'files',  icon: '🔍', label: 'Files'  },
          ].map(m => (
            <button key={m.key}
                    onClick={() => { setSearchMode(m.key); setSearch(''); setSearchRes([]); setFileSearchRes([]); }}
                    className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-xl
                                text-xs font-semibold transition-all
                                ${searchMode === m.key
                                  ? 'text-slate-900 shadow-sm'
                                  : 'text-white/50 hover:text-white/80'}`}
                    style={searchMode === m.key ? { background: 'rgba(255,255,255,0.92)' } : {}}>
              <span>{m.icon}</span>{m.label}
            </button>
          ))}
        </div>
        <input className="input !py-2 !text-sm"
               placeholder={searchMode === 'files'
                 ? 'Search by file name or description…'
                 : 'Search users by name, phone or email…'}
               value={search}
               onChange={e => setSearch(e.target.value)}/>
      </div>

      {/* ── Results ── */}
      <div className="flex-1 overflow-y-auto">

        {search.trim().length >= 2 && searchMode === 'files' ? (
          /* ── File search results ── */
          <div className="px-2 pt-2 space-y-1.5 pb-2">
            {searching && <p className="text-xs text-slate-600 px-2 py-2">Searching…</p>}
            {!searching && fileSearchRes.length === 0 && (
              <p className="text-sm text-slate-600 px-2 py-4 text-center">No files found</p>
            )}
            {fileSearchRes.map(f => (
              <button key={f.id}
                      onClick={() => {
                        const existing = convList.find(c => c.id === f.conversationId);
                        if (existing) {
                          onSelect(existing);
                          markOpened(existing.id);
                          setUnreadCounts(prev => { const m = new Map(prev); m.delete(existing.id); return m; });
                        }
                      }}
                      className="w-full flex items-start gap-3 px-3 py-2.5
                                 rounded-2xl border text-left transition-all duration-150"
                      style={{
                        background: 'rgba(14,130,210,0.40)',
                        backdropFilter: 'blur(12px)',
                        WebkitBackdropFilter: 'blur(12px)',
                        borderColor: 'rgba(255,255,255,0.16)',
                        boxShadow: '0 1px 6px rgba(14,130,210,0.20)',
                      }}>
                {/* File icon */}
                <div className="w-9 h-9 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
                     style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.14)' }}>
                  {f.category === 'IMAGE'    ? '🖼️' :
                   f.category === 'VIDEO'    ? '🎬' :
                   f.category === 'AUDIO'    ? '🎵' :
                   f.category === 'DOCUMENT' ? '📄' :
                   f.category === 'ARCHIVE'  ? '🗜️' : '📎'}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-bold text-slate-800 truncate">{f.originalFileName}</p>
                  {f.caption && (
                    <p className="text-xs text-slate-500 truncate mt-0.5">"{f.caption}"</p>
                  )}
                  <p className="text-xs text-slate-500 mt-0.5 truncate">
                    <span className="font-semibold text-sky-700">{f.senderName}</span>
                    {f.conversationName && f.conversationName !== f.senderName && (
                      <span className="text-slate-400"> · {f.conversationName}</span>
                    )}
                    <span className="text-slate-400"> · {new Date(f.sentAt).toLocaleDateString()}</span>
                  </p>
                </div>
              </button>
            ))}
          </div>
        ) : search.trim().length >= 2 ? (
          /* ── User search results ── */
          <div className="px-2 pt-2 space-y-1.5 pb-2">
            {searching && <p className="text-xs text-slate-600 px-2 py-2">Searching…</p>}
            {!searching && searchRes.length === 0 && (
              <p className="text-sm text-slate-600 px-2 py-4 text-center">No users found</p>
            )}
            {searchRes.map(u => {
              const busy = actionLoading[u.id];
              return (
                <div key={u.id} className="px-3 py-3 rounded-2xl border transition-all duration-150"
                     style={{
                       background: 'rgba(14,130,210,0.40)',
                       backdropFilter: 'blur(12px)',
                       WebkitBackdropFilter: 'blur(12px)',
                       borderColor: 'rgba(255,255,255,0.16)',
                       boxShadow: '0 1px 6px rgba(14,130,210,0.20)',
                     }}>

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
                        <p className="text-xs text-slate-500 truncate">{u.mobileNumber}</p>
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
          <div className="px-2 pt-2 space-y-1.5 pb-2">
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
              const last         = conv.lastFile;
              const unreadCount  = unreadCounts.get(conv.id) ?? 0;
              const hasNew       = unreadCount > 0;
              const isActive     = selected?.id === conv.id;

              return (
                <button key={conv.id}
                        onClick={() => {
                          onSelect(conv);
                          markOpened(conv.id);
                          setUnreadCounts(prev => { const m = new Map(prev); m.delete(conv.id); return m; });
                        }}
                        className="w-full text-left flex items-center gap-3 px-3 py-2.5
                                   rounded-2xl border transition-all duration-150"
                        style={conv.type === 'GROUP' ? {
                          /* ── Pink glass for groups ── */
                          background: isActive
                            ? 'rgba(219,39,119,0.68)'
                            : hasNew
                              ? 'rgba(219,39,119,0.50)'
                              : 'rgba(219,39,119,0.32)',
                          backdropFilter: 'blur(12px)',
                          WebkitBackdropFilter: 'blur(12px)',
                          borderColor: isActive
                            ? 'rgba(255,255,255,0.38)'
                            : hasNew
                              ? 'rgba(255,255,255,0.22)'
                              : 'rgba(255,255,255,0.16)',
                          boxShadow: isActive
                            ? '0 4px 16px rgba(219,39,119,0.38)'
                            : hasNew
                              ? '0 2px 10px rgba(219,39,119,0.24)'
                              : '0 1px 6px rgba(219,39,119,0.16)',
                        } : {
                          /* ── Blue glass for direct chats ── */
                          background: isActive
                            ? 'rgba(14,130,210,0.72)'
                            : hasNew
                              ? 'rgba(14,130,210,0.55)'
                              : 'rgba(14,130,210,0.38)',
                          backdropFilter: 'blur(12px)',
                          WebkitBackdropFilter: 'blur(12px)',
                          borderColor: isActive
                            ? 'rgba(255,255,255,0.35)'
                            : hasNew
                              ? 'rgba(255,255,255,0.22)'
                              : 'rgba(255,255,255,0.14)',
                          boxShadow: isActive
                            ? '0 4px 16px rgba(14,130,210,0.40)'
                            : hasNew
                              ? '0 2px 10px rgba(14,130,210,0.25)'
                              : '0 1px 6px rgba(14,130,210,0.18)',
                        }}>

                  {/* Avatar / icon */}
                  <div className="relative flex-shrink-0">
                    {conv.type === 'GROUP'
                      ? <div className="w-11 h-11 avatar rounded-xl text-sm overflow-hidden">
                          {conv.iconUrl
                            ? <img src={conv.iconUrl} alt=""
                                   className="w-full h-full object-cover rounded-xl"
                                   onError={e => { e.target.style.display = 'none'; }}/>
                            : '👥'}
                        </div>
                      : <Avatar name={conv.name} photoUrl={conv.iconUrl} size="lg"/>
                    }
                    {/* Pulse dot on avatar when unread */}
                    {hasNew && !isActive && (
                      <span className="absolute -top-0.5 -right-0.5 w-3 h-3 rounded-full
                                       bg-sky-400 border-2 border-slate-900 animate-pulse"/>
                    )}
                  </div>

                  {/* Text content */}
                  <div className="flex-1 min-w-0">
                    {/* Name + badge */}
                    <div className="flex items-center justify-between gap-1 mb-0.5">
                      <p className={`font-bold text-sm truncate
                                     ${hasNew && !isActive ? 'text-slate-900' : isActive ? 'text-slate-900' : 'text-slate-800'}`}>
                        {conv.name}
                      </p>

                      {hasNew && !isActive ? (
                        <span className="flex-shrink-0 flex items-center gap-1 px-1.5 py-0.5
                                         rounded-full text-[10px] font-bold text-white bg-sky-600
                                         shadow-sm">
                          📥 {unreadCount > 99 ? '99+' : unreadCount}
                        </span>
                      ) : (
                        last?.sentAt && (
                          <span className="text-[10px] text-slate-500 flex-shrink-0">
                            {formatDistanceToNow(new Date(last.sentAt), { addSuffix: false })}
                          </span>
                        )
                      )}
                    </div>

                    {/* Subtitle */}
                    {hasNew && !isActive ? (
                      <p className="text-xs font-semibold text-sky-700 truncate">
                        {unreadCount === 1 ? '1 file received' : `${unreadCount} files received`}
                      </p>
                    ) : last ? (
                      <p className="text-xs text-slate-600 truncate">
                        {fileIcon(last.category)}&nbsp;{last.originalFileName}
                      </p>
                    ) : (
                      <p className="text-xs text-slate-400 italic">No files yet</p>
                    )}
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
                  className="mx-3 mb-2 px-3 py-2.5 rounded-xl border border-sky-100
                             bg-white/60 hover:bg-white/80 hover:border-sky-200
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
      <div className="px-4 py-3 border-t border-sky-100 bg-white/60 backdrop-blur-sm flex items-center gap-3">
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
