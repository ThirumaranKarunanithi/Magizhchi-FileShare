import { useState, useEffect, useRef, useCallback } from 'react';
import { conversations, files, connections, sharing } from '../services/api';
import { subscribeToConversation } from '../services/socket';
import { useAuth } from '../context/AuthContext';
import { format } from 'date-fns';
import toast from 'react-hot-toast';
import ShareModal     from './ShareModal';
import GroupInfoModal from './GroupInfoModal';
import Avatar         from './Avatar';

// ── Folder helpers ────────────────────────────────────────────────────────────

/** Group a flat array of messages into [{folderPath, items}] preserving display order. */
function groupByFolder(msgs) {
  const groups = [];
  const seen   = new Map(); // folderPath → index

  for (const msg of msgs) {
    const fp = msg.folderPath || null;
    if (fp && seen.has(fp)) {
      groups[seen.get(fp)].items.push(msg);
    } else if (fp) {
      seen.set(fp, groups.length);
      groups.push({ folderPath: fp, items: [msg] });
    } else {
      groups.push({ folderPath: null, items: [msg] });
    }
  }
  return groups;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function formatBytes(b) {
  if (!b) return '—';
  if (b < 1024)        return `${b} B`;
  if (b < 1024 ** 2)   return `${(b / 1024).toFixed(1)} KB`;
  if (b < 1024 ** 3)   return `${(b / 1024 ** 2).toFixed(1)} MB`;
  return                     `${(b / 1024 ** 3).toFixed(2)} GB`;
}

function fileIcon(category) {
  switch (category) {
    case 'IMAGE':    return { emoji: '🖼',  bg: 'bg-purple-100' };
    case 'VIDEO':    return { emoji: '🎬',  bg: 'bg-red-100'    };
    case 'DOCUMENT': return { emoji: '📄',  bg: 'bg-blue-100'   };
    case 'AUDIO':    return { emoji: '🎵',  bg: 'bg-green-100'  };
    case 'ARCHIVE':  return { emoji: '🗜',  bg: 'bg-yellow-100' };
    default:         return { emoji: '📎',  bg: 'bg-slate-100'  };
  }
}

function initials(name) {
  if (!name) return '?';
  const p = name.trim().split(' ');
  return p.length >= 2 ? (p[0][0] + p[1][0]).toUpperCase() : p[0][0].toUpperCase();
}

const CATEGORIES = ['ALL', 'IMAGE', 'VIDEO', 'DOCUMENT', 'AUDIO', 'ARCHIVE'];
const CAT_LABEL  = { ALL: 'All Files', IMAGE: '🖼 Images', VIDEO: '🎬 Videos',
                     DOCUMENT: '📄 Docs', AUDIO: '🎵 Audio', ARCHIVE: '🗜 Archives' };

// ── Main Component ────────────────────────────────────────────────────────────

export default function ChatWindow({ conversation }) {
  const { currentUser } = useAuth();

  const [messages,  setMessages]  = useState([]);
  const [page,      setPage]      = useState(0);
  const [hasMore,   setHasMore]   = useState(true);
  const [loading,   setLoading]   = useState(false);
  const [uploading, setUploading] = useState(false);
  const [selected,  setSelected]  = useState(new Set());
  const [dragOver,       setDragOver]       = useState(false);
  const [filter,         setFilter]         = useState('ALL');
  const [expandedFolders, setExpandedFolders] = useState(new Set());

  const fileInputRef   = useRef(null);
  const folderInputRef = useRef(null);

  // ── Description dialog ─────────────────────────────────────────────────────
  // pendingUpload: null | { type:'file', file:File } | { type:'folder', picked:File[], folderName:string }
  const [pendingUpload, setPendingUpload] = useState(null);
  const [description,   setDescription]  = useState('');

  // ── Search ─────────────────────────────────────────────────────────────────
  const [searchQuery, setSearchQuery] = useState('');

  // folder upload progress: null | { done: number, total: number }
  const [folderProgress, setFolderProgress] = useState(null);
  const [showShare,      setShowShare]      = useState(false);
  const [showGroupInfo,  setShowGroupInfo]  = useState(false);
  // memberCount kept in sync after add/remove so the header subtitle stays accurate
  const [memberCount,    setMemberCount]    = useState(conversation.memberCount ?? 0);
  // Map<fileMessageId, SharedResourceResponse[]> — files the current user shared, with targets
  const [sharedFilesMap, setSharedFilesMap] = useState(new Map());
  // Shares visible inside this specific conversation (bidirectional for DIRECT, group-wide for GROUP)
  const [contextShares,  setContextShares]  = useState([]);

  // ── Load files ──────────────────────────────────────────────────────────────
  const loadFiles = useCallback(async (p = 0, prepend = false) => {
    if (loading) return;
    setLoading(true);
    try {
      const { data } = await conversations.fileHistory(conversation.id, p);
      const items = (data.content ?? data).filter(m => !m.isDeleted);
      if (prepend) {
        setMessages(prev => [...items.reverse(), ...prev]);
      } else {
        setMessages(items.reverse());
        setPage(0);
      }
      setHasMore(!(data.last ?? items.length === 0));
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, [conversation.id, loading]);

  useEffect(() => {
    setMessages([]);
    setPage(0);
    setHasMore(true);
    setSelected(new Set());
    setMemberCount(conversation.memberCount ?? 0);
    loadFiles(0, false);
    // Build Map<fileMessageId, shares[]> so each row knows WHO the file was shared with
    sharing.sharedByMe()
      .then(r => {
        const map = new Map();
        r.data.forEach(s => {
          const list = map.get(s.fileMessageId) ?? [];
          list.push(s);
          map.set(s.fileMessageId, list);
        });
        setSharedFilesMap(map);
      })
      .catch(() => {});
    // Load shares visible in this conversation — works for both sharer and recipient.
    // Uses conversationId only; the server resolves the type internally.
    if (conversation.type === 'DIRECT' || conversation.type === 'GROUP') {
      sharing.inConversation(conversation.id)
        .then(r => setContextShares(r.data))
        .catch(() => setContextShares([]));
    } else {
      setContextShares([]);
    }
  }, [conversation.id]); // eslint-disable-line

  // ── Real-time ───────────────────────────────────────────────────────────────
  useEffect(() => {
    const unsub = subscribeToConversation(conversation.id, event => {
      if (event.type === 'NEW_FILE') {
        setMessages(prev => [event.payload, ...prev]);
      } else if (event.type === 'FILE_DELETED') {
        setMessages(prev => prev.filter(m => m.id !== event.payload.id));
      }
    });
    return unsub;
  }, [conversation.id]);

  // ── Upload ──────────────────────────────────────────────────────────────────
  const sendFile = async (file, caption) => {
    if (!file) return;
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      if (caption?.trim()) fd.append('caption', caption.trim());
      const { data } = await files.send(conversation.id, fd);
      setMessages(prev => [data, ...prev]);
      toast.success(`"${file.name}" uploaded!`);
    } catch (e) { toast.error('Upload failed: ' + e); }
    finally { setUploading(false); }
  };

  // Intercept file pick → show description dialog instead of uploading immediately
  const handleFileInput = e => {
    const picked = e.target.files[0];
    e.target.value = '';
    if (!picked) return;
    setDescription('');
    setPendingUpload({ type: 'file', file: picked });
  };

  // ── Folder upload ───────────────────────────────────────────────────────────
  // Intercept folder pick → show description dialog instead of uploading immediately
  const handleFolderInput = async (e) => {
    const picked = Array.from(e.target.files || []);
    e.target.value = '';
    if (!picked.length) return;
    const folderName = picked[0].webkitRelativePath?.split('/')[0] || 'folder';
    setDescription('');
    setPendingUpload({ type: 'folder', picked, folderName });
  };

  // Called when the user confirms the description dialog
  const confirmUpload = async () => {
    if (!pendingUpload) return;
    const caption = description.trim() || undefined;
    setPendingUpload(null);
    setDescription('');

    if (pendingUpload.type === 'file') {
      await sendFile(pendingUpload.file, caption);
    } else {
      // Folder upload
      const { picked, folderName } = pendingUpload;
      setFolderProgress({ done: 0, total: picked.length });
      const fd = new FormData();
      picked.forEach(f => {
        fd.append('files', f);
        fd.append('relativePaths', f.webkitRelativePath || f.name);
      });
      if (caption) fd.append('caption', caption);
      try {
        const { data } = await files.sendFolder(conversation.id, fd, (evt) => {
          if (evt.lengthComputable) {
            const pct = evt.loaded / evt.total;
            setFolderProgress(prev => ({
              done:  Math.round(pct * prev.total),
              total: prev.total,
            }));
          }
        });
        if (Array.isArray(data)) {
          setMessages(prev => {
            const existingIds = new Set(prev.map(m => m.id));
            const fresh = data.filter(m => !existingIds.has(m.id));
            return [...fresh, ...prev];
          });
        }
        toast.success(`📁 "${folderName}" — ${picked.length} file${picked.length !== 1 ? 's' : ''} uploaded!`);
      } catch (err) {
        toast.error('Folder upload failed: ' + err);
      } finally {
        setFolderProgress(null);
      }
    }
  };

  const handleDrop = e => {
    e.preventDefault(); setDragOver(false);
    const dropped = e.dataTransfer.files[0];
    if (!dropped) return;
    setDescription('');
    setPendingUpload({ type: 'file', file: dropped });
  };

  // ── Download ────────────────────────────────────────────────────────────────
  const handleDownload = async (msg) => {
    try {
      const { data } = await files.downloadUrl(msg.id);
      const a = document.createElement('a');
      a.href = data.url; a.download = msg.originalFileName; a.click();
    } catch (e) { toast.error('Download failed: ' + e); }
  };

  // ── Delete ──────────────────────────────────────────────────────────────────
  const handleDelete = async (msgId) => {
    if (!window.confirm('Delete this file? This cannot be undone.')) return;
    try {
      await files.delete(msgId);
      setMessages(prev => prev.filter(m => m.id !== msgId));
      setSelected(prev => { const s = new Set(prev); s.delete(msgId); return s; });
      toast.success('File deleted.');
    } catch (e) { toast.error('Could not delete: ' + e); }
  };

  // ── Selection ───────────────────────────────────────────────────────────────
  const toggleSelect = (id) => {
    setSelected(prev => {
      const s = new Set(prev);
      s.has(id) ? s.delete(id) : s.add(id);
      return s;
    });
  };

  // ── Filtered + searched list ────────────────────────────────────────────────
  const q = searchQuery.trim().toLowerCase();
  const displayed = messages
    .filter(m => filter === 'ALL' || m.category === filter)
    .filter(m => !q || (
      m.originalFileName?.toLowerCase().includes(q) ||
      m.caption?.toLowerCase().includes(q)
    ));
  const allChecked = displayed.length > 0 && displayed.every(m => selected.has(m.id));
  const groups     = groupByFolder(displayed);

  const toggleAll = () => {
    if (allChecked) setSelected(new Set());
    else setSelected(new Set(displayed.map(m => m.id)));
  };

  const toggleFolder = (fp) => {
    setExpandedFolders(prev => {
      const next = new Set(prev);
      next.has(fp) ? next.delete(fp) : next.add(fp);
      return next;
    });
  };

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    <div className="flex-1 flex flex-col h-screen overflow-hidden relative"
         style={{ background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)' }}
         onDragOver={e => { e.preventDefault(); setDragOver(true); }}
         onDragLeave={() => setDragOver(false)}
         onDrop={handleDrop}>

      {/* Dot texture */}
      <div className="absolute inset-0 pointer-events-none z-0"
           style={{
             backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)',
             backgroundSize: '22px 22px',
           }}/>

      {/* All content above texture */}
      <div className="relative z-10 flex flex-col flex-1 overflow-hidden">

      {/* ── Top bar ── */}
      <div className="flex items-center justify-between px-6 py-4">
        {/* Conversation identity */}
        <div className="flex items-center gap-3">
          {conversation.type === 'GROUP' ? (
            <div className="w-10 h-10 flex-shrink-0 rounded-xl overflow-hidden
                            bg-white/20 flex items-center justify-center text-lg">
              {conversation.iconUrl
                ? <img src={conversation.iconUrl} alt=""
                       className="w-full h-full object-cover"
                       onError={e => { e.currentTarget.style.display = 'none'; }}/>
                : '👥'}
            </div>
          ) : conversation.type === 'PERSONAL' ? (
            <div className="w-10 h-10 flex-shrink-0 rounded-full bg-white/20
                            flex items-center justify-center text-xl">🗄️</div>
          ) : (
            <Avatar name={conversation.name}
                    photoUrl={conversation.iconUrl}
                    size="md"
                    className="ring-2 ring-white/30"/>
          )}
          <div>
            <h2 className="text-white font-bold text-lg leading-tight">{conversation.name}</h2>
            <p className="text-white/70 text-xs">
              {conversation.type === 'GROUP'
                ? `${memberCount} member${memberCount !== 1 ? 's' : ''}`
                : conversation.type === 'PERSONAL'
                  ? 'My personal storage'
                  : 'Direct file share'}
              {' · '}{messages.length} file{messages.length !== 1 ? 's' : ''}
            </p>
          </div>
        </div>

        {/* Group manage button */}
        {conversation.type === 'GROUP' && (
          <button
            onClick={() => setShowGroupInfo(true)}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/10
                       hover:bg-white/20 text-white/80 hover:text-white
                       transition-all text-sm font-semibold"
            title="Manage group members">
            ⚙ Manage
          </button>
        )}

        {/* DIRECT conversation actions: Unfriend + Block */}
        {conversation.type === 'DIRECT' && (
          <div className="flex items-center gap-2">
            <button
              onClick={async () => {
                if (!window.confirm(
                  `Remove ${conversation.name} from your connections? You won't be able to share files until you reconnect.`
                )) return;
                try {
                  await connections.unfriend(conversation.otherUserId);
                  toast.success(`${conversation.name} has been removed from your connections.`);
                } catch (e) { toast.error(e?.toString() || 'Could not unfriend user'); }
              }}
              className="p-2 rounded-xl bg-white/10 hover:bg-amber-500/20 text-white/70
                         hover:text-amber-200 transition-all text-sm"
              title="Remove connection">
              👤✕
            </button>
            <button
              onClick={async () => {
                if (!window.confirm(
                  `Block ${conversation.name}? They won't be able to see or contact you.`
                )) return;
                try {
                  await connections.block(conversation.otherUserId);
                  toast.success(`${conversation.name} has been blocked.`);
                } catch (e) { toast.error(e?.toString() || 'Could not block user'); }
              }}
              className="p-2 rounded-xl bg-white/10 hover:bg-red-500/20 text-white/70
                         hover:text-red-200 transition-all text-sm"
              title="Block user">
              🚫
            </button>
          </div>
        )}

        {/* Upload buttons */}
        <div className="flex items-center gap-2">
          {/* Single file */}
          <button onClick={() => fileInputRef.current?.click()}
                  disabled={uploading || !!folderProgress}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl bg-white text-sky-700
                             font-bold text-sm shadow-lg hover:bg-sky-50
                             disabled:opacity-60 transition-all active:scale-95">
            <span className={uploading ? 'animate-spin' : ''}>
              {uploading ? '⏳' : '⬆'}
            </span>
            {uploading ? 'Uploading…' : 'Upload File'}
          </button>

          {/* Folder */}
          <button onClick={() => folderInputRef.current?.click()}
                  disabled={uploading || !!folderProgress}
                  className="flex items-center gap-2 px-4 py-2.5 rounded-xl
                             bg-white/20 text-white border border-white/40
                             font-bold text-sm shadow hover:bg-white/30
                             disabled:opacity-60 transition-all active:scale-95">
            {folderProgress
              ? <><span className="animate-spin">⏳</span> {folderProgress.done}/{folderProgress.total}</>
              : <><span>📁</span> Upload Folder</>
            }
          </button>
        </div>

        <input ref={fileInputRef}   type="file" className="hidden" onChange={handleFileInput}/>
        {/* webkitdirectory lets the user pick an entire folder */}
        <input ref={folderInputRef} type="file" className="hidden" multiple
               // @ts-ignore – non-standard but universally supported
               webkitdirectory="true"
               onChange={handleFolderInput}/>
      </div>

      {/* ── Filter pills ── */}
      <div className="px-6 pb-2 flex gap-2 overflow-x-auto">
        {CATEGORIES.map(cat => (
          <button key={cat} onClick={() => setFilter(cat)}
                  className={`flex-shrink-0 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all
                              ${filter === cat
                                ? 'bg-white text-sky-700 shadow-md'
                                : 'bg-white/20 text-white hover:bg-white/30'}`}>
            {CAT_LABEL[cat]}
          </button>
        ))}
      </div>

      {/* ── Search bar ── */}
      <div className="px-6 pb-3">
        <div className="flex items-center gap-2 px-3 py-2 rounded-xl"
             style={{
               background: 'rgba(255,255,255,0.12)',
               border: '1px solid rgba(255,255,255,0.25)',
             }}>
          <span className="text-white/50 text-sm flex-shrink-0">🔍</span>
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Search by file name or description…"
            className="flex-1 bg-transparent text-sm text-white placeholder-white/35
                       outline-none min-w-0"
          />
          {searchQuery && (
            <button onClick={() => setSearchQuery('')}
                    className="text-white/40 hover:text-white text-xs flex-shrink-0
                               transition-colors leading-none">
              ✕
            </button>
          )}
        </div>
        {searchQuery && (
          <p className="text-[11px] text-white/40 mt-1 px-1">
            {displayed.length} result{displayed.length !== 1 ? 's' : ''} for "{searchQuery}"
          </p>
        )}
      </div>

      {/* ── File browser card (glass) ── */}
      <div className="flex-1 mx-4 mb-4 rounded-2xl flex flex-col overflow-hidden"
           style={{
             background: 'rgba(255,255,255,0.13)',
             backdropFilter: 'blur(20px)',
             WebkitBackdropFilter: 'blur(20px)',
             border: '1px solid rgba(255,255,255,0.28)',
             boxShadow: '0 8px 48px rgba(0,0,0,0.18), inset 0 1px 0 rgba(255,255,255,0.3)',
           }}>

        {/* Card header row */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-white/20">
          <div className="flex items-center gap-3">
            <input type="checkbox" className="w-4 h-4 accent-sky-500 cursor-pointer"
                   checked={allChecked} onChange={toggleAll}/>
            <span className="text-sm font-semibold text-white/80">
              {selected.size > 0
                ? `${selected.size} of ${displayed.length} selected`
                : `${displayed.length} file${displayed.length !== 1 ? 's' : ''}`}
            </span>
          </div>

          {/* Bulk actions — visible only when files are selected */}
          {selected.size > 0 && (
            <div className="flex gap-2">
              <button
                onClick={async () => {
                  for (const id of selected) {
                    const msg = messages.find(m => m.id === id);
                    if (msg) await handleDownload(msg);
                  }
                }}
                className="text-xs px-3 py-1.5 rounded-lg font-semibold text-white transition-all"
                style={{ background: 'rgba(255,255,255,0.2)', border: '1px solid rgba(255,255,255,0.3)' }}>
                ⬇ Download
              </button>
              <button
                onClick={() => setShowShare(true)}
                className="text-xs px-3 py-1.5 rounded-lg font-semibold text-white transition-all"
                style={{ background: 'rgba(139,92,246,0.35)', border: '1px solid rgba(167,139,250,0.5)' }}>
                🔗 Share
              </button>
              <button
                onClick={async () => {
                  if (!window.confirm(`Delete ${selected.size} file(s)?`)) return;
                  for (const id of [...selected]) {
                    const msg = messages.find(m => m.id === id);
                    if (msg?.senderId === currentUser?.id) await handleDelete(msg.id);
                  }
                }}
                className="text-xs px-3 py-1.5 rounded-lg font-semibold text-red-200 transition-all"
                style={{ background: 'rgba(239,68,68,0.2)', border: '1px solid rgba(239,68,68,0.3)' }}>
                🗑 Delete
              </button>
            </div>
          )}
        </div>

        {/* File rows */}
        <div className="flex-1 overflow-y-auto">

          {/* ── Shared-in-this-space section ── */}
          {contextShares.length > 0 && (
            <div>
              {/* Section header */}
              <div className="flex items-center gap-2 px-5 py-2.5 sticky top-0 z-10"
                   style={{
                     background: 'rgba(109,40,217,0.30)',
                     borderBottom: '1px solid rgba(167,139,250,0.35)',
                   }}>
                <span className="text-sm">🔗</span>
                <span className="text-xs font-bold text-violet-200 uppercase tracking-wide">
                  Shared in this space
                </span>
                <span className="ml-auto text-xs font-semibold px-2 py-0.5 rounded-full"
                      style={{ background: 'rgba(167,139,250,0.25)', color: '#ddd6fe',
                               border: '1px solid rgba(167,139,250,0.4)' }}>
                  {contextShares.length}
                </span>
              </div>

              {contextShares.map((share, idx) => {
                const { emoji, bg } = fileIcon(share.category);
                const isMyShare = share.ownerId === currentUser?.id;
                const isLast    = idx === contextShares.length - 1;

                return (
                  <div key={share.id}
                       className="flex items-center gap-3 px-5 py-3 group transition-colors cursor-default"
                       style={{
                         background: 'rgba(109,40,217,0.12)',
                         borderBottom: !isLast ? '1px solid rgba(167,139,250,0.15)' : 'none',
                         borderLeft: '3px solid rgba(167,139,250,0.7)',
                       }}
                       onMouseEnter={e => e.currentTarget.style.background = 'rgba(109,40,217,0.22)'}
                       onMouseLeave={e => e.currentTarget.style.background = 'rgba(109,40,217,0.12)'}>

                    {/* Icon with violet ring */}
                    <div className="w-10 h-10 rounded-xl flex-shrink-0 flex items-center
                                    justify-center text-xl"
                         style={{ background: 'rgba(139,92,246,0.25)',
                                  boxShadow: '0 0 0 2px rgba(167,139,250,0.55)' }}>
                      {emoji}
                    </div>

                    {/* Details */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="text-sm font-semibold text-white truncate">
                          {share.fileName}
                        </p>
                        {/* Direction badge — different label for sharer vs receiver */}
                        <span className="flex-shrink-0 text-[10px] font-bold px-1.5 py-0.5 rounded-full"
                              style={{
                                background: isMyShare ? 'rgba(139,92,246,0.4)' : 'rgba(56,189,248,0.25)',
                                border: isMyShare ? '1px solid rgba(167,139,250,0.5)' : '1px solid rgba(56,189,248,0.4)',
                                color: '#ede9fe',
                              }}>
                          {isMyShare
                            ? `📤 Shared with ${share.targetName ?? 'them'}`
                            : `📥 From ${share.ownerName}`}
                        </span>
                        <span className="flex-shrink-0 text-[10px] font-semibold px-1.5 py-0.5 rounded-full"
                              style={{ background: share.permission === 'EDITOR'
                                         ? 'rgba(34,197,94,0.2)' : 'rgba(255,255,255,0.1)',
                                       border: share.permission === 'EDITOR'
                                         ? '1px solid rgba(34,197,94,0.35)' : '1px solid rgba(255,255,255,0.2)',
                                       color: 'rgba(255,255,255,0.8)' }}>
                          {share.permission === 'EDITOR' ? '✏️ Edit' : '👁 View'}
                        </span>
                      </div>
                      <p className="text-xs text-violet-200/60 truncate mt-0.5">
                        {formatBytes(share.sizeBytes)}
                        {share.sharedAt && ` · ${format(new Date(share.sharedAt), 'd MMM yyyy')}`}
                      </p>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100
                                    transition-opacity flex-shrink-0">
                      <button
                        onClick={async () => {
                          try {
                            const { data } = await files.downloadUrl(share.fileMessageId);
                            const a = document.createElement('a');
                            a.href = data.url; a.download = share.fileName; a.click();
                          } catch (e) { toast.error('Download failed: ' + e); }
                        }}
                        title="Download"
                        className="p-2 rounded-lg text-sm transition-all"
                        style={{ background: 'rgba(139,92,246,0.2)', color: '#c4b5fd' }}>
                        ⬇
                      </button>
                      {isMyShare && (
                        <button
                          onClick={async () => {
                            if (!window.confirm(`Revoke share of "${share.fileName}"?`)) return;
                            try {
                              await sharing.revoke(share.id);
                              setContextShares(prev => prev.filter(s => s.id !== share.id));
                              setSharedFilesMap(prev => {
                                const next = new Map(prev);
                                const list = (next.get(share.fileMessageId) ?? []).filter(s => s.id !== share.id);
                                list.length ? next.set(share.fileMessageId, list) : next.delete(share.fileMessageId);
                                return next;
                              });
                              toast.success('Share revoked.');
                            } catch (e) { toast.error(e?.toString() || 'Could not revoke.'); }
                          }}
                          title="Revoke share"
                          className="p-2 rounded-lg text-sm transition-all"
                          style={{ background: 'rgba(239,68,68,0.15)', color: '#fca5a5' }}>
                          ✕
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}

              {/* Divider between shared section and own files */}
              <div className="px-5 py-2 flex items-center gap-3"
                   style={{ borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
                <div className="flex-1 h-px" style={{ background: 'rgba(255,255,255,0.1)' }}/>
                <span className="text-xs text-white/30">conversation files</span>
                <div className="flex-1 h-px" style={{ background: 'rgba(255,255,255,0.1)' }}/>
              </div>
            </div>
          )}

          {hasMore && (
            <div className="text-center py-2" style={{ borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
              <button onClick={() => { const n = page + 1; setPage(n); loadFiles(n, true); }}
                      disabled={loading}
                      className="text-xs text-white/70 hover:text-white hover:underline">
                {loading ? 'Loading…' : '⬆ Load older files'}
              </button>
            </div>
          )}

          {displayed.length === 0 && !loading && (
            <div className="flex flex-col items-center justify-center h-full py-16 gap-3">
              <span className="text-5xl">📭</span>
              <p className="text-sm font-semibold text-white/70">No files here yet</p>
              <p className="text-xs text-white/50">
                Drop a file or click <strong className="text-white font-bold">Upload File</strong>
              </p>
            </div>
          )}

          {groups.map((group, gi) => {
            const isFolder   = !!group.folderPath;
            const isExpanded = isFolder ? expandedFolders.has(group.folderPath) : true;

            return (
              <div key={group.folderPath ?? `ungrouped-${gi}`}>

                {/* ── Folder header (clickable, collapsible) ── */}
                {isFolder && (
                  <button
                    onClick={() => toggleFolder(group.folderPath)}
                    className="w-full flex items-center gap-3 px-5 py-3 sticky top-0 z-10
                               transition-colors cursor-pointer"
                    style={{ background: 'rgba(255,255,255,0.12)', borderBottom: '1px solid rgba(255,255,255,0.15)' }}
                    onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.2)'}
                    onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.12)'}>

                    {/* Chevron */}
                    <span className={`text-white/60 text-xs transition-transform duration-200
                                      ${isExpanded ? 'rotate-90' : ''}`}>
                      ▶
                    </span>

                    {/* Folder icon + name */}
                    <span className="text-lg">📁</span>
                    <span className="text-sm font-semibold text-white truncate flex-1 text-left">
                      {/* Show only the deepest folder segment */}
                      {group.folderPath.replace(/\/$/, '').split('/').pop()}
                    </span>

                    {/* File count badge */}
                    <span className="flex-shrink-0 text-xs font-semibold px-2 py-0.5
                                     rounded-full text-white"
                          style={{ background: 'rgba(255,255,255,0.2)', border: '1px solid rgba(255,255,255,0.3)' }}>
                      {group.items.length} file{group.items.length !== 1 ? 's' : ''}
                    </span>
                  </button>
                )}

                {/* ── File rows (hidden when folder is collapsed) ── */}
                {isExpanded && group.items.map((msg, idx) => {
                  const { emoji, bg } = fileIcon(msg.category);
                  const isMine    = msg.senderId === currentUser?.id;
                  const myShares  = sharedFilesMap.get(msg.id) ?? [];
                  const isShared  = myShares.length > 0;
                  const isLast   = idx === group.items.length - 1 && gi === groups.length - 1;

                  // Shared files get a violet left-border accent and a faint violet tint
                  const sharedBg      = 'rgba(139,92,246,0.18)';
                  const sharedBgHover = 'rgba(139,92,246,0.28)';
                  const selectedBg    = 'rgba(255,255,255,0.18)';
                  const normalBgHover = 'rgba(255,255,255,0.1)';

                  const baseBg = selected.has(msg.id)
                    ? selectedBg
                    : isShared ? sharedBg : 'transparent';

                  return (
                    <div key={msg.id}
                         className={`flex items-center gap-3 py-3 group transition-colors cursor-default
                                     ${isFolder ? 'pl-12 pr-5' : 'px-5'}`}
                         style={{
                           background: baseBg,
                           borderLeft: isShared ? '3px solid rgba(167,139,250,0.8)' : '3px solid transparent',
                           borderBottom: !isLast ? '1px solid rgba(255,255,255,0.1)' : 'none',
                         }}
                         onMouseEnter={e => {
                           if (!selected.has(msg.id))
                             e.currentTarget.style.background = isShared ? sharedBgHover : normalBgHover;
                         }}
                         onMouseLeave={e => {
                           if (!selected.has(msg.id))
                             e.currentTarget.style.background = isShared ? sharedBg : 'transparent';
                         }}>

                      {/* Checkbox */}
                      <input type="checkbox"
                             className="w-4 h-4 accent-sky-500 cursor-pointer flex-shrink-0"
                             checked={selected.has(msg.id)}
                             onChange={() => toggleSelect(msg.id)}/>

                      {/* Icon — violet ring when shared */}
                      <div className={`w-10 h-10 rounded-xl flex-shrink-0 flex items-center
                                      justify-center text-xl ${isShared ? '' : bg}`}
                           style={isShared ? {
                             background: 'rgba(139,92,246,0.25)',
                             boxShadow: '0 0 0 2px rgba(167,139,250,0.6)',
                           } : {}}>
                        {isShared ? '🔗' : emoji}
                      </div>

                      {/* Details */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <p className="text-sm font-semibold text-white truncate">
                            {msg.originalFileName}
                          </p>
                          {isShared && (
                            <span className="flex-shrink-0 text-[10px] font-bold px-1.5 py-0.5 rounded-full"
                                  style={{
                                    background: 'rgba(139,92,246,0.35)',
                                    border: '1px solid rgba(167,139,250,0.5)',
                                    color: '#ddd6fe',
                                  }}>
                              🔗 Shared
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-white/55 truncate">
                          {formatBytes(msg.fileSizeBytes)}
                          {' · '}
                          {format(new Date(msg.sentAt), 'd MMM yyyy')}
                          {!isMine && msg.senderName && (
                            <> · <span className="text-sky-200 font-medium">{msg.senderName}</span></>
                          )}
                        </p>
                        {msg.caption && (
                          <p className="text-xs text-white/45 italic truncate mt-0.5">
                            "{msg.caption}"
                          </p>
                        )}
                        {isShared && (
                          <p className="text-[11px] mt-0.5 truncate" style={{ color: '#c4b5fd' }}>
                            Shared with&nbsp;
                            {myShares.map((s, i) => (
                              <span key={s.id}>
                                {i > 0 && ', '}
                                <span className="font-semibold">
                                  {s.shareType === 'GROUP' ? `👥 ${s.targetName}` : s.targetName}
                                </span>
                                <span style={{ color: 'rgba(196,181,253,0.6)' }}>
                                  &nbsp;({s.permission === 'EDITOR' ? 'Edit' : 'View'})
                                </span>
                              </span>
                            ))}
                          </p>
                        )}
                      </div>

                      {/* Hover actions */}
                      <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100
                                      transition-opacity flex-shrink-0">
                        <button onClick={() => handleDownload(msg)} title="Download"
                                className="p-2 rounded-lg bg-sky-50 hover:bg-sky-100
                                           text-sky-600 transition-all text-sm">
                          ⬇
                        </button>
                        {isMine && (
                          <button
                            onClick={() => {
                              setSelected(new Set([msg.id]));
                              setShowShare(true);
                            }}
                            title="Share"
                            className="p-2 rounded-lg transition-all text-sm"
                            style={{ background: 'rgba(139,92,246,0.2)', color: '#c4b5fd' }}>
                            🔗
                          </button>
                        )}
                        {isMine && (
                          <button onClick={() => handleDelete(msg.id)} title="Delete"
                                  className="p-2 rounded-lg bg-red-50 hover:bg-red-100
                                             text-red-500 transition-all text-sm">
                            🗑
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div className="px-5 py-2 text-center" style={{ borderTop: '1px solid rgba(255,255,255,0.15)' }}>
          <p className="text-xs text-white/40">
            Drag &amp; drop files anywhere · Max 500 MB
          </p>
        </div>
      </div>

      {/* ── Description dialog ── */}
      {pendingUpload && (
        <div className="absolute inset-0 z-50 flex items-center justify-center"
             style={{ background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(4px)' }}>
          <div className="w-full max-w-sm mx-4 rounded-2xl p-6"
               style={{
                 background: 'linear-gradient(135deg, rgba(255,255,255,0.96) 0%, rgba(255,255,255,0.88) 100%)',
                 border: '1px solid rgba(255,255,255,0.7)',
                 boxShadow: '0 24px 48px rgba(0,0,0,0.3)',
               }}>

            {/* Header */}
            <div className="flex items-start gap-3 mb-4">
              <div className="w-10 h-10 rounded-xl flex-shrink-0 flex items-center justify-center
                              text-xl bg-sky-50">
                {pendingUpload.type === 'folder' ? '📁' : '📎'}
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="text-gray-900 font-bold text-base">Add a description</h3>
                <p className="text-gray-500 text-xs mt-0.5 truncate">
                  {pendingUpload.type === 'folder'
                    ? `📁 ${pendingUpload.folderName} — ${pendingUpload.picked.length} file${pendingUpload.picked.length !== 1 ? 's' : ''}`
                    : pendingUpload.file.name}
                </p>
              </div>
            </div>

            {/* Description textarea */}
            <div className="mb-4">
              <label className="block text-xs font-semibold text-gray-600 mb-1.5 uppercase tracking-wide">
                Description <span className="text-gray-400 normal-case font-normal">(optional)</span>
              </label>
              <textarea
                autoFocus
                rows={3}
                maxLength={500}
                value={description}
                onChange={e => setDescription(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) confirmUpload();
                  if (e.key === 'Escape') { setPendingUpload(null); setDescription(''); }
                }}
                placeholder="What is this file about? (e.g. Q3 report, project assets…)"
                className="w-full px-3 py-2.5 rounded-xl text-sm text-gray-800 resize-none
                           outline-none transition-all placeholder-gray-400"
                style={{
                  background: 'rgba(241,245,249,1)',
                  border: '1px solid rgba(148,163,184,0.5)',
                  boxShadow: 'inset 0 1px 3px rgba(0,0,0,0.06)',
                }}
                onFocus={e => {
                  e.target.style.border = '1px solid #0ea5e9';
                  e.target.style.boxShadow = '0 0 0 3px rgba(14,165,233,0.15)';
                }}
                onBlur={e => {
                  e.target.style.border = '1px solid rgba(148,163,184,0.5)';
                  e.target.style.boxShadow = 'inset 0 1px 3px rgba(0,0,0,0.06)';
                }}
              />
              <p className="text-right text-[10px] text-gray-400 mt-1">{description.length}/500</p>
            </div>

            {/* Actions */}
            <div className="flex gap-2">
              <button
                onClick={() => { setPendingUpload(null); setDescription(''); }}
                className="flex-1 py-2.5 rounded-xl text-sm font-semibold text-gray-600
                           bg-gray-100 hover:bg-gray-200 transition-all">
                Cancel
              </button>
              <button
                onClick={confirmUpload}
                disabled={uploading || !!folderProgress}
                className="flex-1 py-2.5 rounded-xl text-sm font-bold text-white
                           transition-all hover:-translate-y-0.5 active:translate-y-0
                           disabled:opacity-50 disabled:cursor-not-allowed"
                style={{
                  background: '#0F172A',
                  boxShadow: '0 4px 16px rgba(15,23,42,0.25)',
                }}>
                {pendingUpload.type === 'folder' ? '📁 Upload Folder' : '⬆ Upload File'}
              </button>
            </div>
            <p className="text-center text-[10px] text-gray-400 mt-2">
              Ctrl+Enter to upload · Esc to cancel
            </p>
          </div>
        </div>
      )}

      {/* ── Drop overlay ── */}
      {dragOver && (
        <div className="absolute inset-0 bg-sky-500/30 border-4 border-dashed border-white/70
                        flex items-center justify-center pointer-events-none z-50 m-4 rounded-2xl">
          <div className="text-center">
            <span className="text-6xl">📂</span>
            <p className="text-xl font-bold text-white mt-3 drop-shadow-lg">Drop to share</p>
          </div>
        </div>
      )}
      </div>{/* end z-10 wrapper */}

      {/* ── Group info / manage modal ── */}
      {showGroupInfo && conversation.type === 'GROUP' && (
        <GroupInfoModal
          conversation={conversation}
          onClose={() => setShowGroupInfo(false)}
          onUpdated={() => {
            // Re-fetch member count after add/remove
            conversations.members(conversation.id)
              .then(r => setMemberCount(r.data.length))
              .catch(() => {});
          }}
        />
      )}

      {/* ── Share modal ── */}
      {showShare && (
        <ShareModal
          selectedIds={selected}
          onClose={() => {
            setShowShare(false);
            // Refresh shared-targets map and context panel
            sharing.sharedByMe()
              .then(r => {
                const map = new Map();
                r.data.forEach(s => {
                  const list = map.get(s.fileMessageId) ?? [];
                  list.push(s);
                  map.set(s.fileMessageId, list);
                });
                setSharedFilesMap(map);
              })
              .catch(() => {});
            if (conversation.type === 'DIRECT' || conversation.type === 'GROUP') {
              sharing.inConversation(conversation.id)
                .then(r => setContextShares(r.data)).catch(() => {});
            }
          }}
        />
      )}
    </div>
  );
}
