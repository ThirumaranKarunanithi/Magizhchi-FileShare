import { useState, useEffect } from 'react';
import { sharing, files as filesApi } from '../services/api';
import Avatar from './Avatar';
import { format } from 'date-fns';
import toast from 'react-hot-toast';

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

// ── Component ─────────────────────────────────────────────────────────────────

export default function SharedWithMeView() {
  const [tab,      setTab]      = useState('WITH_ME');   // 'WITH_ME' | 'BY_ME'
  const [items,    setItems]    = useState([]);
  const [loading,  setLoading]  = useState(false);
  const [filter,   setFilter]   = useState('ALL');       // 'ALL' | 'USER' | 'GROUP'

  const loadData = async (t) => {
    setLoading(true);
    try {
      const fn = t === 'WITH_ME' ? sharing.sharedWithMe : sharing.sharedByMe;
      const { data } = await fn();
      setItems(data);
    } catch (e) {
      console.error(e);
      setItems([]);
    } finally { setLoading(false); }
  };

  useEffect(() => { loadData(tab); }, [tab]);

  const handleDownload = async (item) => {
    try {
      const { data } = await filesApi.downloadUrl(item.fileMessageId);
      const a = document.createElement('a');
      a.href = data.url; a.download = item.fileName; a.click();
    } catch (e) { toast.error('Download failed: ' + e); }
  };

  const handleRevoke = async (item) => {
    if (!window.confirm(`Revoke access to "${item.fileName}"?`)) return;
    try {
      await sharing.revoke(item.id);
      setItems(prev => prev.filter(x => x.id !== item.id));
      toast.success('Share revoked.');
    } catch (e) { toast.error(e?.toString() || 'Could not revoke.'); }
  };

  const displayed = filter === 'ALL' ? items : items.filter(x => x.shareType === filter);

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="flex-1 flex flex-col h-screen overflow-hidden relative"
         style={{ background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)' }}>

      {/* Dot texture */}
      <div className="absolute inset-0 pointer-events-none z-0"
           style={{
             backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)',
             backgroundSize: '22px 22px',
           }}/>

      <div className="relative z-10 flex flex-col flex-1 overflow-hidden">

        {/* ── Header ── */}
        <div className="flex items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-white/20 flex items-center justify-center text-xl">
              🔗
            </div>
            <div>
              <h2 className="text-white font-bold text-lg">Shared Files</h2>
              <p className="text-white/60 text-xs">
                {displayed.length} file{displayed.length !== 1 ? 's' : ''}
              </p>
            </div>
          </div>

          {/* Filter pills */}
          <div className="flex gap-2">
            {['ALL', 'USER', 'GROUP'].map(f => (
              <button key={f}
                      onClick={() => setFilter(f)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all
                                  ${filter === f
                                    ? 'bg-white text-sky-700 shadow'
                                    : 'bg-white/20 text-white hover:bg-white/30'}`}>
                {f === 'ALL' ? 'All' : f === 'USER' ? '👤 Direct' : '👥 Groups'}
              </button>
            ))}
          </div>
        </div>

        {/* ── Tabs ── */}
        <div className="px-6 pb-3 flex gap-2">
          {[
            { key: 'WITH_ME', label: '📥 Shared with Me' },
            { key: 'BY_ME',   label: '📤 Shared by Me'   },
          ].map(t => (
            <button key={t.key}
                    onClick={() => setTab(t.key)}
                    className={`px-4 py-2 rounded-xl text-sm font-semibold transition-all
                                ${tab === t.key
                                  ? 'bg-white text-sky-700 shadow-md'
                                  : 'bg-white/20 text-white hover:bg-white/30'}`}>
              {t.label}
            </button>
          ))}
        </div>

        {/* ── Content card ── */}
        <div className="flex-1 mx-4 mb-4 rounded-2xl flex flex-col overflow-hidden"
             style={{
               background: 'rgba(255,255,255,0.13)',
               backdropFilter: 'blur(20px)',
               WebkitBackdropFilter: 'blur(20px)',
               border: '1px solid rgba(255,255,255,0.28)',
               boxShadow: '0 8px 48px rgba(0,0,0,0.18), inset 0 1px 0 rgba(255,255,255,0.3)',
             }}>

          {/* Column headers */}
          <div className="grid gap-3 px-5 py-2.5 border-b border-white/20 text-xs font-semibold
                          text-white/50 uppercase tracking-wide"
               style={{ gridTemplateColumns: '1fr 140px 100px 90px 80px' }}>
            <span>File</span>
            <span>{tab === 'WITH_ME' ? 'Shared by' : 'Shared with'}</span>
            <span>Type</span>
            <span>Permission</span>
            <span>Date</span>
          </div>

          {/* Rows */}
          <div className="flex-1 overflow-y-auto">
            {loading && (
              <div className="flex items-center justify-center h-full">
                <span className="text-white/50 text-sm animate-pulse">Loading…</span>
              </div>
            )}

            {!loading && displayed.length === 0 && (
              <div className="flex flex-col items-center justify-center h-full py-16 gap-3">
                <span className="text-5xl">🔗</span>
                <p className="text-sm font-semibold text-white/70">
                  {tab === 'WITH_ME' ? 'Nothing shared with you yet' : 'You haven\'t shared anything yet'}
                </p>
                <p className="text-xs text-white/40">
                  {tab === 'WITH_ME'
                    ? 'When someone shares files with you, they\'ll appear here.'
                    : 'Select files in any conversation and click Share to get started.'}
                </p>
              </div>
            )}

            {!loading && displayed.map((item, idx) => {
              const { emoji, bg } = fileIcon(item.category);
              const isLast = idx === displayed.length - 1;
              const who = tab === 'WITH_ME'
                ? { name: item.ownerName,  photo: item.ownerPhotoUrl  }
                : { name: item.targetName, photo: item.targetPhotoUrl };

              return (
                <div key={item.id}
                     className="grid gap-3 items-center px-5 py-3 group transition-colors"
                     style={{
                       gridTemplateColumns: '1fr 140px 100px 90px 80px',
                       borderBottom: !isLast ? '1px solid rgba(255,255,255,0.1)' : 'none',
                     }}
                     onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.07)'}
                     onMouseLeave={e => e.currentTarget.style.background = 'transparent'}>

                  {/* File name + size */}
                  <div className="flex items-center gap-3 min-w-0">
                    <div className={`w-9 h-9 rounded-xl flex-shrink-0 flex items-center justify-center text-lg ${bg}`}>
                      {emoji}
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-white truncate">{item.fileName}</p>
                      <p className="text-xs text-white/45">{formatBytes(item.sizeBytes)}</p>
                    </div>
                  </div>

                  {/* Who */}
                  <div className="flex items-center gap-2 min-w-0">
                    <Avatar name={who.name} photoUrl={who.photo} size="xs"/>
                    <span className="text-xs text-white/70 truncate">{who.name || '—'}</span>
                  </div>

                  {/* Share type badge */}
                  <div>
                    <span className="text-xs font-semibold px-2 py-1 rounded-full"
                          style={{
                            background: item.shareType === 'GROUP'
                              ? 'rgba(168,85,247,0.25)'
                              : 'rgba(56,189,248,0.25)',
                            border: item.shareType === 'GROUP'
                              ? '1px solid rgba(168,85,247,0.4)'
                              : '1px solid rgba(56,189,248,0.4)',
                            color: 'white',
                          }}>
                      {item.shareType === 'GROUP' ? `👥 ${item.targetName ?? 'Group'}` : '👤 Direct'}
                    </span>
                  </div>

                  {/* Permission badge */}
                  <div>
                    <span className="text-xs font-semibold px-2 py-1 rounded-full"
                          style={{
                            background: item.permission === 'EDITOR'
                              ? 'rgba(34,197,94,0.2)'
                              : 'rgba(255,255,255,0.12)',
                            border: item.permission === 'EDITOR'
                              ? '1px solid rgba(34,197,94,0.4)'
                              : '1px solid rgba(255,255,255,0.2)',
                            color: 'white',
                          }}>
                      {item.permission === 'EDITOR' ? '✏️ Edit' : '👁 View'}
                    </span>
                  </div>

                  {/* Date + actions */}
                  <div className="flex flex-col items-end gap-1">
                    <span className="text-xs text-white/45">
                      {item.sharedAt ? format(new Date(item.sharedAt), 'd MMM') : '—'}
                    </span>
                    <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                      <button onClick={() => handleDownload(item)}
                              title="Download"
                              className="p-1.5 rounded-lg text-xs bg-sky-50 hover:bg-sky-100
                                         text-sky-600 transition-all">
                        ⬇
                      </button>
                      {tab === 'BY_ME' && (
                        <button onClick={() => handleRevoke(item)}
                                title="Revoke"
                                className="p-1.5 rounded-lg text-xs bg-red-50 hover:bg-red-100
                                           text-red-500 transition-all">
                          ✕
                        </button>
                      )}
                    </div>
                  </div>

                </div>
              );
            })}
          </div>

          {/* Footer */}
          <div className="px-5 py-2 text-center" style={{ borderTop: '1px solid rgba(255,255,255,0.15)' }}>
            <p className="text-xs text-white/40">
              Shared files do not count toward your storage quota
            </p>
          </div>
        </div>

      </div>
    </div>
  );
}
