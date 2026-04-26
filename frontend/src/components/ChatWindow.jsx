import { useState, useEffect, useRef, useCallback } from 'react';
import { conversations, files, connections } from '../services/api';
import { subscribeToConversation } from '../services/socket';
import { useAuth } from '../context/AuthContext';
import { format } from 'date-fns';
import toast from 'react-hot-toast';

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

  // folder upload progress: null | { done: number, total: number }
  const [folderProgress, setFolderProgress] = useState(null);

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
    loadFiles(0, false);
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
  const sendFile = async (file) => {
    if (!file) return;
    setUploading(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      const { data } = await files.send(conversation.id, fd);
      setMessages(prev => [data, ...prev]);
      toast.success(`"${file.name}" uploaded!`);
    } catch (e) { toast.error('Upload failed: ' + e); }
    finally { setUploading(false); }
  };

  const handleFileInput = e => {
    const picked = e.target.files[0];
    if (picked) sendFile(picked);
    e.target.value = '';
  };

  // ── Folder upload ───────────────────────────────────────────────────────────
  const handleFolderInput = async (e) => {
    const picked = Array.from(e.target.files || []);
    e.target.value = '';
    if (!picked.length) return;

    const folderName = picked[0].webkitRelativePath?.split('/')[0] || 'folder';
    setFolderProgress({ done: 0, total: picked.length });

    const fd = new FormData();
    picked.forEach(f => {
      fd.append('files', f);
      fd.append('relativePaths', f.webkitRelativePath || f.name);
    });

    try {
      const { data } = await files.sendFolder(conversation.id, fd, (evt) => {
        if (evt.lengthComputable) {
          // Approximate file count from bytes progress
          const pct = evt.loaded / evt.total;
          setFolderProgress(prev => ({
            done:  Math.round(pct * prev.total),
            total: prev.total,
          }));
        }
      });
      // The WebSocket will push each NEW_FILE event, but add any that arrive
      // via HTTP response as a fallback (de-dup by id)
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
  };

  const handleDrop = e => {
    e.preventDefault(); setDragOver(false);
    const dropped = e.dataTransfer.files[0];
    if (dropped) sendFile(dropped);
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

  // ── Filtered list ───────────────────────────────────────────────────────────
  const displayed  = filter === 'ALL' ? messages : messages.filter(m => m.category === filter);
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
    <div className="flex-1 flex flex-col h-screen overflow-hidden
                    bg-gradient-to-br from-sky-400 via-sky-500 to-sky-600"
         onDragOver={e => { e.preventDefault(); setDragOver(true); }}
         onDragLeave={() => setDragOver(false)}
         onDrop={handleDrop}>

      {/* ── Top bar ── */}
      <div className="flex items-center justify-between px-6 py-4">
        {/* Conversation identity */}
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 flex-shrink-0 flex items-center justify-center
                          bg-white/20 text-white font-bold text-sm
                          ${conversation.type === 'GROUP' ? 'rounded-xl' : 'rounded-full'}`}>
            {conversation.iconUrl
              ? <img src={conversation.iconUrl} alt="" className="w-full h-full object-cover rounded-full"/>
              : conversation.type === 'GROUP'    ? '👥'
              : conversation.type === 'PERSONAL' ? '🗄️'
              : initials(conversation.name)}
          </div>
          <div>
            <h2 className="text-white font-bold text-lg leading-tight">{conversation.name}</h2>
            <p className="text-white/70 text-xs">
              {conversation.type === 'GROUP'
                ? `${conversation.memberCount} members`
                : conversation.type === 'PERSONAL'
                  ? 'My personal storage'
                  : 'Direct file share'}
              {' · '}{messages.length} file{messages.length !== 1 ? 's' : ''}
            </p>
          </div>
        </div>

        {/* Block button — only for DIRECT conversations */}
        {conversation.type === 'DIRECT' && (
          <button
            onClick={async () => {
              if (!window.confirm(
                `Block ${conversation.name}? They won't be able to see or contact you.`
              )) return;
              try {
                // Derive the other user's id from the conversation name isn't reliable;
                // we embed otherUserId in the conversation response (see ConversationResponse)
                // For now we use the conversationId endpoint route
                await connections.block(conversation.otherUserId);
                toast.success(`${conversation.name} has been blocked.`);
              } catch (e) { toast.error(e?.toString() || 'Could not block user'); }
            }}
            className="p-2 rounded-xl bg-white/10 hover:bg-red-500/20 text-white/70
                       hover:text-red-200 transition-all text-sm"
            title="Block user">
            🚫
          </button>
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
      <div className="px-6 pb-3 flex gap-2 overflow-x-auto">
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

      {/* ── File browser card ── */}
      <div className="flex-1 mx-4 mb-4 bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden">

        {/* Card header row */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
          <div className="flex items-center gap-3">
            <input type="checkbox" className="w-4 h-4 accent-sky-500 cursor-pointer"
                   checked={allChecked} onChange={toggleAll}/>
            <span className="text-sm font-semibold text-slate-500">
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
                className="text-xs px-3 py-1.5 rounded-lg bg-sky-50 text-sky-700
                           font-semibold hover:bg-sky-100 transition-all">
                ⬇ Download
              </button>
              <button
                onClick={async () => {
                  if (!window.confirm(`Delete ${selected.size} file(s)?`)) return;
                  for (const id of [...selected]) {
                    const msg = messages.find(m => m.id === id);
                    if (msg?.senderId === currentUser?.id) await handleDelete(msg.id);
                  }
                }}
                className="text-xs px-3 py-1.5 rounded-lg bg-red-50 text-red-600
                           font-semibold hover:bg-red-100 transition-all">
                🗑 Delete
              </button>
            </div>
          )}
        </div>

        {/* File rows */}
        <div className="flex-1 overflow-y-auto">

          {hasMore && (
            <div className="text-center py-2 border-b border-slate-50">
              <button onClick={() => { const n = page + 1; setPage(n); loadFiles(n, true); }}
                      disabled={loading}
                      className="text-xs text-sky-500 hover:underline">
                {loading ? 'Loading…' : '⬆ Load older files'}
              </button>
            </div>
          )}

          {displayed.length === 0 && !loading && (
            <div className="flex flex-col items-center justify-center h-full py-16 gap-3">
              <span className="text-5xl">📭</span>
              <p className="text-sm font-semibold text-slate-400">No files here yet</p>
              <p className="text-xs text-slate-300">
                Drop a file or click <strong className="text-sky-500">Upload File</strong>
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
                    className="w-full flex items-center gap-3 px-5 py-3 bg-slate-50
                               border-b border-slate-100 sticky top-0 z-10
                               hover:bg-sky-50/60 transition-colors cursor-pointer">

                    {/* Chevron */}
                    <span className={`text-slate-400 text-xs transition-transform duration-200
                                      ${isExpanded ? 'rotate-90' : ''}`}>
                      ▶
                    </span>

                    {/* Folder icon + name */}
                    <span className="text-lg">📁</span>
                    <span className="text-sm font-semibold text-slate-700 truncate flex-1 text-left">
                      {/* Show only the deepest folder segment */}
                      {group.folderPath.replace(/\/$/, '').split('/').pop()}
                    </span>

                    {/* File count badge */}
                    <span className="flex-shrink-0 text-xs font-semibold px-2 py-0.5
                                     bg-sky-100 text-sky-600 rounded-full">
                      {group.items.length} file{group.items.length !== 1 ? 's' : ''}
                    </span>
                  </button>
                )}

                {/* ── File rows (hidden when folder is collapsed) ── */}
                {isExpanded && group.items.map((msg, idx) => {
                  const { emoji, bg } = fileIcon(msg.category);
                  const isMine = msg.senderId === currentUser?.id;
                  const isLast = idx === group.items.length - 1 && gi === groups.length - 1;

                  return (
                    <div key={msg.id}
                         className={`flex items-center gap-3 py-3 group
                                     hover:bg-sky-50/60 transition-colors cursor-default
                                     ${isFolder ? 'pl-12 pr-5' : 'px-5'}
                                     ${selected.has(msg.id) ? 'bg-sky-50' : ''}
                                     ${!isLast ? 'border-b border-slate-50' : ''}`}>

                      {/* Checkbox */}
                      <input type="checkbox"
                             className="w-4 h-4 accent-sky-500 cursor-pointer flex-shrink-0"
                             checked={selected.has(msg.id)}
                             onChange={() => toggleSelect(msg.id)}/>

                      {/* Icon */}
                      <div className={`w-10 h-10 rounded-xl flex-shrink-0 flex items-center
                                      justify-center text-xl ${bg}`}>
                        {emoji}
                      </div>

                      {/* Details */}
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-slate-800 truncate">
                          {msg.originalFileName}
                        </p>
                        <p className="text-xs text-slate-400 truncate">
                          {formatBytes(msg.fileSizeBytes)}
                          {' · '}
                          {format(new Date(msg.sentAt), 'd MMM yyyy')}
                          {!isMine && msg.senderName && (
                            <> · <span className="text-sky-500 font-medium">{msg.senderName}</span></>
                          )}
                        </p>
                        {msg.caption && (
                          <p className="text-xs text-slate-400 italic truncate mt-0.5">
                            "{msg.caption}"
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
        <div className="px-5 py-2 border-t border-slate-100 text-center">
          <p className="text-xs text-slate-300">
            Drag &amp; drop files anywhere · Max 500 MB
          </p>
        </div>
      </div>

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
    </div>
  );
}
