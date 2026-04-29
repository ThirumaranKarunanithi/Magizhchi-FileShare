import { useState, useEffect, useCallback, useRef } from 'react';
import { conversations, users, connections, storage, files as filesApi, sharing } from '../services/api';
import { subscribeToUserNotifications } from '../services/socket';
import { useAuth } from '../context/AuthContext';
import { formatDistanceToNow } from 'date-fns';
import toast from 'react-hot-toast';
import NewGroupModal            from './NewGroupModal';
import ProfileModal             from './ProfileModal';
import ConnectionRequestsModal  from './ConnectionRequestsModal';
import StorageModal             from './StorageModal';
import Avatar                   from './Avatar';
import NotificationHelpModal   from './NotificationHelpModal';
import { useDesktopNotifications } from '../hooks/useDesktopNotifications';

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

// ── Conversation dedup ────────────────────────────────────────────────────────
// Defensive guard against backend hiccups that can return two distinct DIRECT
// conversations with the same counterparty (a race in getOrCreateDirect can
// create a second row before the first commits). Collapse them so the UI only
// shows ONE row per (type + counterparty), keeping the one with the most
// recent activity (and most files attached).
function dedupeConvList(list) {
  const byId = new Map();
  for (const c of list) {
    if (!byId.has(c.id)) byId.set(c.id, c);
  }
  const groups = new Map(); // type+name → conv
  for (const c of byId.values()) {
    if (c.type !== 'DIRECT') {
      groups.set(`__keep__:${c.id}`, c);
      continue;
    }
    const key = `DIRECT:${c.name}`;
    const existing = groups.get(key);
    if (!existing) { groups.set(key, c); continue; }
    // Pick the row with the more recent lastFile; tie-break with higher id.
    const ts  = (c.lastFile?.sentAt ? Date.parse(c.lastFile.sentAt) : 0);
    const eTs = (existing.lastFile?.sentAt ? Date.parse(existing.lastFile.sentAt) : 0);
    if (ts > eTs || (ts === eTs && c.id > existing.id)) groups.set(key, c);
  }
  return [...groups.values()];
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
  // Aggregate stats for the "Shared with me" card → { count, totalBytes }
  const [sharedStats,      setSharedStats]      = useState({ count: 0, totalBytes: 0 });
  // Per-user action loading: { [userId]: 'sending'|'accepting'|'cancelling'|'rejecting' }
  const [actionLoading,    setActionLoading]    = useState({});

  // 'PEOPLE' = direct chats only · 'GROUPS' = group conversations only
  // Persisted in localStorage so the user's preference sticks.
  const [convTab, setConvTab] = useState(() => {
    try { return localStorage.getItem('msh:convTab') === 'GROUPS' ? 'GROUPS' : 'PEOPLE'; }
    catch { return 'PEOPLE'; }
  });
  useEffect(() => {
    try { localStorage.setItem('msh:convTab', convTab); } catch {}
  }, [convTab]);

  // Always-current refs — used inside the WS callback / OS-notification
  // onClick handler to avoid stale closures (the WS effect's deps don't
  // include these so they're captured at subscription time).
  const selectedRef = useRef(selected);
  useEffect(() => { selectedRef.current = selected; }, [selected]);
  const onSelectRef = useRef(onSelect);
  useEffect(() => { onSelectRef.current = onSelect; }, [onSelect]);
  const convListRef = useRef([]);
  useEffect(() => { convListRef.current = convList; }, [convList]);

  // Desktop notifications — fires browser-native pushes to the OS tray when
  // the tab is in the background (or the conversation isn't currently open).
  const { permission: notifPermission, request: requestNotif, notify } = useDesktopNotifications();
  const notifyRef = useRef(notify);
  useEffect(() => { notifyRef.current = notify; }, [notify]);
  // Live permission mirror — kept as a state so the toggle button re-renders
  // immediately after the user accepts/declines the browser prompt. We also
  // poll once a second while the page is visible so changes made from the
  // browser's site-settings UI surface back into our icon without a refresh.
  const [livePerm, setLivePerm] = useState(
    typeof window !== 'undefined' && 'Notification' in window
      ? Notification.permission : 'unsupported'
  );
  // Help dialog shown when permission is 'denied' — only the user can flip it
  // back from browser site-settings, so this modal walks them through.
  const [showNotifHelp, setShowNotifHelp] = useState(false);
  useEffect(() => {
    if (!('Notification' in window)) return;
    const poll = () => {
      if (document.visibilityState === 'visible') {
        const p = Notification.permission;
        setLivePerm(prev => prev === p ? prev : p);
      }
    };
    const id = setInterval(poll, 1000);
    document.addEventListener('visibilitychange', poll);
    return () => { clearInterval(id); document.removeEventListener('visibilitychange', poll); };
  }, []);

  // ── Load conversations + storage on mount ───────────────────────────────
  useEffect(() => {
    conversations.list().then(r => {
      const list = dedupeConvList(r.data);
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

    // Aggregate everything shared WITH the current user — count + total size.
    sharing.sharedWithMe()
      .then(r => {
        const list  = Array.isArray(r.data) ? r.data : [];
        const count = list.length;
        const totalBytes = list.reduce((s, x) => s + (x.sizeBytes ?? x.fileSizeBytes ?? 0), 0);
        setSharedStats({ count, totalBytes });
      })
      .catch(() => setSharedStats({ count: 0, totalBytes: 0 }));
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

      // Should the OS-level notification fire? Skip when this very
      // conversation is open AND the tab is currently visible — otherwise
      // the user would get a system pop-up for something they're already
      // looking at.
      const shouldNotify = (cid) =>
        !(selectedRef.current?.id === cid && !document.hidden);

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
        // Desktop / OS-tray notification
        if (shouldNotify(cid)) {
          notifyRef.current?.({
            title: `📂 ${p.senderName ?? 'Someone'} shared a file`,
            body:  p.fileName ?? '',
            tag:   `file-${cid}`,
            onClick: () => {
              const conv = convListRef.current.find(c => c.id === cid);
              if (conv) onSelectRef.current?.(conv);
            },
          });
        }
        // Refresh the conversation list so the lastFile preview updates
        conversations.list().then(r => setConvList(dedupeConvList(r.data))).catch(console.error);
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
        // Refresh aggregate stats so the Shared Files card stays accurate
        sharing.sharedWithMe()
          .then(r => {
            const list  = Array.isArray(r.data) ? r.data : [];
            setSharedStats({
              count:      list.length,
              totalBytes: list.reduce((s, x) => s + (x.sizeBytes ?? x.fileSizeBytes ?? 0), 0),
            });
          })
          .catch(() => {});
        // Desktop notification
        notifyRef.current?.({
          title: p.groupName
            ? `🔗 ${p.senderName} shared with "${p.groupName}"`
            : `🔗 ${p.senderName} shared a file with you`,
          body:  p.groupName
            ? `${count} file${count !== 1 ? 's' : ''}`
            : (p.fileName ?? ''),
          tag:   `share-${p.senderName}-${p.groupName ?? 'direct'}`,
        });
      }

      // ── Connection request received ──
      else if (event.type === 'CONNECTION_REQUEST') {
        setPendingCount(prev => prev + 1);
        toast(`🤝 ${p.senderName} wants to connect!`, { duration: 6000 });
        notifyRef.current?.({
          title: `🤝 ${p.senderName ?? 'Someone'} wants to connect`,
          body:  'Open Magizhchi Box to accept or decline.',
          tag:   `connreq-${p.senderName}`,
          requireInteraction: true,
        });
      }

      // ── Connection request accepted ──
      else if (event.type === 'CONNECTION_ACCEPTED') {
        toast.success(`🎉 ${p.receiverName} accepted your request!`);
        conversations.list().then(r => setConvList(dedupeConvList(r.data))).catch(console.error);
      }
    });

    return unsub;
  }, [currentUser?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Unified search ───────────────────────────────────────────────────────
  // One debounced effect that fires BOTH the user search and the file search
  // in parallel. The results panel below renders them under section headers,
  // so a single search box covers people + files at once.
  useEffect(() => {
    const q = search.trim();
    if (q.length < 2) {
      setSearchRes([]);
      setFileSearchRes([]);
      return;
    }
    const t = setTimeout(() => {
      setSearching(true);
      Promise.allSettled([
        users.search(q).then(r => setSearchRes(r.data)).catch(console.error),
        filesApi.search(q).then(r => setFileSearchRes(r.data)).catch(console.error),
      ]).finally(() => setSearching(false));
    }, 350);
    return () => clearTimeout(t);
  }, [search]);

  // ── Conversation open (clears unread badge + records open time) ─────────
  const openDirect = async (userId) => {
    try {
      const { data } = await conversations.openDirect(userId);
      setConvList(prev =>
        prev.find(c => c.id === data.id)
          ? prev
          : dedupeConvList([data, ...prev]));
      onSelect(data);
      markOpened(data.id);
      setUnreadCounts(prev => { const m = new Map(prev); m.delete(data.id); return m; });
      setSearch('');
    } catch (e) { toast.error(e?.toString() || 'Cannot open conversation'); }
  };

  const handleGroupCreated = (conv) => {
    setConvList(prev => dedupeConvList([conv, ...prev]));
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

          {/* Desktop-notification toggle.
              Hidden when the browser doesn't support Notification at all.
              When permission is 'default' the user is asked on click.
              When 'denied' clicking surfaces a hint toast (the user must
              re-enable from the browser's site settings).
              When 'granted' it acts as a status indicator. */}
          {('Notification' in window) && (
            <button
              onClick={() => {
                // Bypass the hook for the toggle and talk to the browser
                // Notification API directly — minimum indirection means the
                // call happens inside the same micro-task as the click event,
                // which is what browsers require for the prompt to appear.
                console.log('[notif] click — current permission:', Notification.permission,
                            '| isSecureContext:', window.isSecureContext);

                // Notifications API is gated on a secure context. If the page
                // is served over plain http (and isn't on localhost), browsers
                // silently block requestPermission() and new Notification().
                if (!window.isSecureContext) {
                  toast.error('Desktop notifications need a secure connection (HTTPS or localhost). Switch to https:// to enable them.', { duration: 8000 });
                  return;
                }

                const perm = Notification.permission;

                // Already on → confirm visually with a test push.
                if (perm === 'granted') {
                  try {
                    new Notification('Magizhchi Box', {
                      body: 'Notifications are working ✓',
                      tag:  'notif-test',
                    });
                    toast('Sent a test notification — check your OS tray.', { icon: '🔔' });
                  } catch (e) {
                    console.warn('[notif] new Notification threw:', e);
                    toast.error('Permission is granted but the OS blocked the notification. Check system Do-Not-Disturb / Focus-Assist settings.', { duration: 8000 });
                  }
                  return;
                }

                // Permanently denied → JavaScript can't undo this. Open the
                // visual help modal that walks the user through unblocking
                // from their specific browser's site settings.
                if (perm === 'denied') {
                  setShowNotifHelp(true);
                  return;
                }

                // 'default' — show the prompt RIGHT NOW. Calling
                // Notification.requestPermission() synchronously inside the
                // click handler keeps the user-gesture context that some
                // browsers require for the prompt to appear.
                let result;
                try {
                  result = Notification.requestPermission();
                } catch (e) {
                  console.error('[notif] requestPermission threw:', e);
                  toast.error('Could not open the notification prompt: ' + (e?.message || e));
                  return;
                }

                // Hint the user the prompt should appear; close it after the
                // promise settles. Keep this toast short — most browsers will
                // fire the prompt within a frame.
                const hint = toast.loading('Opening browser permission prompt…');

                Promise.resolve(result).then(r => {
                  toast.dismiss(hint);
                  console.log('[notif] requestPermission resolved:', r);
                  // Sync our live permission mirror so the icon updates immediately.
                  setLivePerm(r);
                  if (r === 'granted') {
                    toast.success('Desktop notifications enabled');
                    try {
                      new Notification('Magizhchi Box', {
                        body: 'You\'ll see notifications here from now on.',
                        tag:  'notif-welcome',
                      });
                    } catch (e) {
                      console.warn('[notif] welcome notification threw:', e);
                    }
                  } else if (r === 'denied') {
                    toast.error('You blocked notifications. Re-enable them later from the site-settings menu in your browser.', { duration: 8000 });
                  } else {
                    toast('Notification dialog dismissed.', { icon: '🔕' });
                  }
                }).catch(err => {
                  toast.dismiss(hint);
                  console.error('[notif] permission promise rejected:', err);
                  toast.error('Permission request failed: ' + (err?.message || err));
                });
              }}
              title={
                livePerm === 'granted' ? 'Desktop notifications: ON · click to test'
                : livePerm === 'denied'  ? 'Desktop notifications BLOCKED — click for help'
                : 'Enable desktop notifications'}
              className="relative p-2 rounded-xl hover:bg-sky-100 text-lg transition-colors"
              style={{
                // Color-code the three states for instant visual feedback,
                // and use a megaphone glyph so this button is plainly NOT
                // the connection-requests 🔔 sitting next to it.
                color: livePerm === 'granted' ? '#0ea5e9'   // sky blue · active
                     : livePerm === 'denied'  ? '#ef4444'   // red · blocked
                                              : '#94a3b8',  // slate · default
                opacity: livePerm === 'granted' ? 1 : 0.85,
              }}>
              <span className="relative inline-block">
                {/* Megaphone — distinct from the connection-requests bell */}
                📢
                {/* Status badge in the corner: dot for ON, slash for BLOCKED */}
                {livePerm === 'granted' && (
                  <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full
                                   bg-emerald-500 border-2 border-white"/>
                )}
                {livePerm === 'denied' && (
                  <span className="absolute -top-1 -right-1 w-3.5 h-3.5 rounded-full
                                   bg-red-500 border-2 border-white text-white text-[8px]
                                   font-bold flex items-center justify-center leading-none">
                    ✕
                  </span>
                )}
              </span>
            </button>
          )}
        </div>
      </div>

      {/* ── My Storage + Shared Files (side by side) ── */}
      <div className="px-3 pt-3 pb-2 flex gap-2 overflow-visible">

        {/* My Storage — top row opens the personal space, the embedded
            progress bar at the bottom opens the storage-breakdown modal. */}
        {(() => {
          const pct       = storageData?.usedPercent ?? 0;
          const barColor  = pct >= 90 ? 'bg-red-500' : pct >= 70 ? 'bg-amber-500' : 'bg-green-500';
          const textColor = pct >= 90 ? 'text-red-700' : pct >= 70 ? 'text-amber-700' : 'text-emerald-700';
          return (
            <div className="flex-1 flex flex-col gap-2 px-3 py-2.5 rounded-2xl border transition-all group"
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

              {/* Top row — clickable, opens personal space */}
              <button
                onClick={openMyStorage}
                disabled={loadingStorage}
                className="flex items-center gap-2.5 text-left disabled:opacity-60">
                <div className="w-8 h-8 rounded-xl flex items-center justify-center text-lg flex-shrink-0"
                     style={{
                       background: selected?.type === 'PERSONAL' ? 'rgba(255,255,255,0.20)' : 'rgba(255,255,255,0.08)',
                       border:     '1px solid rgba(255,255,255,0.15)',
                     }}>
                  {loadingStorage ? <span className="animate-spin text-sm">⏳</span> : '🗄️'}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-xs font-bold leading-tight truncate text-slate-900">My Storage</p>
                  <p className="text-[10px] leading-tight mt-0.5 truncate text-slate-600">
                    {currentUser?.displayName
                      ? `${currentUser.displayName.split(' ')[0]}'s space`
                      : 'Personal space'}
                  </p>
                </div>
              </button>

              {/* Embedded storage progress bar — wrapped in a glass-morphism
                  pill so it visually matches the rest of the UI. Clicking it
                  shows the storage-breakdown modal (independent of the row above). */}
              {storageData && (
                <button
                  onClick={() => setShowStorage(true)}
                  title="See storage breakdown"
                  className="text-left w-full px-2.5 py-2 rounded-xl transition-colors"
                  style={{
                    background:           'rgba(255,255,255,0.18)',
                    backdropFilter:       'blur(10px)',
                    WebkitBackdropFilter: 'blur(10px)',
                    border:               '1px solid rgba(255,255,255,0.30)',
                    boxShadow:            '0 1px 4px rgba(255,255,255,0.10) inset, 0 2px 6px rgba(15,23,42,0.10)',
                  }}>
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-[10px] font-semibold text-slate-800/80">
                      💾 {Math.round(pct)}% used
                    </span>
                    <span className={`text-[10px] font-bold ${textColor}`}>
                      {fmtStorage(storageData.usedBytes)} / {fmtStorage(storageData.limitBytes)}
                    </span>
                  </div>
                  <div className="w-full h-1.5 rounded-full overflow-hidden"
                       style={{ background: 'rgba(255,255,255,0.30)',
                                boxShadow: 'inset 0 1px 2px rgba(15,23,42,0.10)' }}>
                    <div className={`h-full rounded-full transition-all ${barColor}`}
                         style={{ width: `${Math.min(100, pct)}%` }}/>
                  </div>
                  {pct >= 90 && (
                    <p className="text-[10px] text-red-700 font-semibold mt-1">
                      🚨 Almost full — upgrade your plan
                    </p>
                  )}
                </button>
              )}
            </div>
          );
        })()}

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
                : sharedStats.count > 0
                  ? `${sharedStats.count} file${sharedStats.count !== 1 ? 's' : ''} · ${fmtStorage(sharedStats.totalBytes)}`
                  : 'No shared files yet'}
            </p>
          </div>
        </button>

      </div>

      {/* ── Search (unified — searches people AND files in one go) ── */}
      <div className="px-3 pb-2 border-b border-sky-100">
        <input className="input !py-2 !text-sm"
               placeholder="🔍 Search people or files…"
               value={search}
               onChange={e => setSearch(e.target.value)}/>
      </div>

      {/* ── Results ── */}
      <div className="flex-1 overflow-y-auto">

        {search.trim().length >= 2 ? (
          /* ── Combined search results: People first, Files second ── */
          <div className="px-2 pt-2 space-y-1.5 pb-2">
            {searching && searchRes.length === 0 && fileSearchRes.length === 0 && (
              <p className="text-xs text-slate-600 px-2 py-2">Searching…</p>
            )}
            {!searching && searchRes.length === 0 && fileSearchRes.length === 0 && (
              <p className="text-sm text-slate-600 px-2 py-6 text-center">
                No people or files match "{search.trim()}"
              </p>
            )}

            {/* ── Files section ── */}
            {fileSearchRes.length > 0 && (
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-2 pt-1 pb-0.5">
                📄 Files · {fileSearchRes.length}
              </p>
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

            {/* ── People section ── */}
            {searchRes.length > 0 && (
              <p className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-2 pt-3 pb-0.5">
                👤 People · {searchRes.length}
              </p>
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
          /* ── Conversation list (split into People / Groups tabs) ── */
          (() => {
            // Filter once and reuse for both the empty state and the list
            const peopleConvs = convList.filter(c => c.type === 'DIRECT');
            const groupConvs  = convList.filter(c => c.type === 'GROUP');
            const visibleConvs = convTab === 'GROUPS' ? groupConvs : peopleConvs;
            return (
          <div>
            {/* Tab strip — no top-padding here; the search section's pb-2 +
                border-b above already gives the same gap as the rest of the
                sidebar sections (storage→search), keeping the rhythm even. */}
            <div className="px-3 pb-2">
              <div className="flex gap-1 p-0.5 rounded-xl"
                   style={{ background: 'rgba(14,130,210,0.18)',
                            border: '1px solid rgba(255,255,255,0.14)' }}>
                {[
                  { key: 'PEOPLE', icon: '👤', label: 'People', count: peopleConvs.length },
                  { key: 'GROUPS', icon: '👥', label: 'Groups', count: groupConvs.length  },
                ].map(t => {
                  const active = convTab === t.key;
                  return (
                    <button key={t.key}
                            onClick={() => setConvTab(t.key)}
                            className={`flex-1 flex items-center justify-center gap-1.5 py-1.5 rounded-xl
                                        text-xs font-semibold transition-all
                                        ${active ? 'text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-800'}`}
                            style={active ? { background: 'rgba(255,255,255,0.92)' } : {}}>
                      <span>{t.icon}</span>{t.label}
                      <span className={`ml-1 text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                        active ? 'bg-sky-100 text-sky-700' : 'bg-slate-200/60 text-slate-500'
                      }`}>{t.count}</span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="px-2 space-y-1.5 pb-2">
              {visibleConvs.length === 0 && (
                <div className="text-center py-12 px-4">
                  <div className="text-4xl mb-2">{convTab === 'GROUPS' ? '👥' : '👤'}</div>
                  <p className="text-sm text-slate-400">
                    {convTab === 'GROUPS' ? 'No groups yet.' : 'No direct chats yet.'}
                  </p>
                  <p className="text-xs text-slate-300 mt-1">
                    {convTab === 'GROUPS'
                      ? 'Tap 👥 above to create a new group.'
                      : 'Search for someone above to start chatting.'}
                  </p>
                </div>
              )}
              {visibleConvs.map(conv => {
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
          </div>
            );
          })()
        )}
      </div>

      {/* (Old bottom storage bar removed — the progress bar now lives inside
          the My Storage card at the top of the sidebar.) */}

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

      {/* Notification-blocked help — opens when the user clicks the 🚫 toggle
          and their browser has 'denied' the permission. Only they can fix it
          from the browser's site-settings UI, so we walk them through. */}
      {showNotifHelp && (
        <NotificationHelpModal
          onClose={() => {
            setShowNotifHelp(false);
            // Re-read the live permission in case they unblocked & we're being
            // dismissed without a reload (some browsers reflect the change
            // in this tab immediately).
            try { setLivePerm(Notification.permission); } catch {}
          }}
          onTryAgain={() => {
            // Re-attempt the prompt. In Chrome this resolves to 'denied'
            // immediately and silently; in Firefox/Safari (after the user
            // clears the block) it actually re-shows the dialog.
            try {
              const result = Notification.requestPermission();
              Promise.resolve(result).then(r => {
                setLivePerm(r);
                if (r === 'granted') {
                  setShowNotifHelp(false);
                  toast.success('Notifications enabled — try a test by clicking the bell.');
                  try { new Notification('Magizhchi Box', { body: 'You\'ll see notifications here from now on.' }); } catch {}
                } else if (r === 'denied') {
                  toast('Still blocked — change the setting in your browser, then click Reload.', { icon: '🚫', duration: 5000 });
                }
              });
            } catch (e) {
              console.warn('[notif] retry failed', e);
              toast.error('Could not request again: ' + (e?.message || e));
            }
          }}
        />
      )}
      {showConnections && (
        <ConnectionRequestsModal
          onClose={() => { setShowConnections(false); refreshPendingCount(); }}
          onConnectionChanged={() => {
            refreshPendingCount();
            conversations.list().then(r => setConvList(dedupeConvList(r.data))).catch(console.error);
          }}
        />
      )}
    </div>
  );
}
