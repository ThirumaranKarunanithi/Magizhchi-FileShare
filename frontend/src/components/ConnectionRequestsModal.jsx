import { useState, useEffect } from 'react';
import { connections } from '../services/api';
import { formatDistanceToNow } from 'date-fns';
import toast from 'react-hot-toast';

function initials(name) {
  if (!name) return '?';
  const p = name.trim().split(' ');
  return p.length >= 2 ? (p[0][0] + p[1][0]).toUpperCase() : p[0][0].toUpperCase();
}

export default function ConnectionRequestsModal({ onClose, onConnectionChanged }) {
  const [tab,      setTab]      = useState('received');   // 'received' | 'sent' | 'blocked'
  const [received, setReceived] = useState([]);
  const [sent,     setSent]     = useState([]);
  const [blocked,  setBlocked]  = useState([]);
  const [loading,  setLoading]  = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const [r, s, b] = await Promise.all([
        connections.receivedRequests(),
        connections.sentRequests(),
        connections.blocked(),
      ]);
      setReceived(r.data);
      setSent(s.data);
      setBlocked(b.data);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  };

  useEffect(() => { load(); }, []);

  const handleAccept = async (req) => {
    try {
      await connections.accept(req.id);
      setReceived(prev => prev.filter(r => r.id !== req.id));
      toast.success(`Connected with ${req.senderName}!`);
      onConnectionChanged?.();
    } catch (e) { toast.error(e?.message || 'Failed to accept'); }
  };

  const handleReject = async (req) => {
    try {
      await connections.reject(req.id);
      setReceived(prev => prev.filter(r => r.id !== req.id));
      toast.success('Request rejected.');
    } catch (e) { toast.error(e?.message || 'Failed to reject'); }
  };

  const handleCancel = async (req) => {
    try {
      await connections.cancel(req.id);
      setSent(prev => prev.filter(r => r.id !== req.id));
      toast.success('Request cancelled.');
    } catch (e) { toast.error(e?.message || 'Failed to cancel'); }
  };

  const handleUnblock = async (user) => {
    if (!window.confirm(`Unblock ${user.displayName}?`)) return;
    try {
      await connections.unblock(user.id);
      setBlocked(prev => prev.filter(u => u.id !== user.id));
      toast.success(`${user.displayName} unblocked.`);
      onConnectionChanged?.();
    } catch (e) { toast.error(e?.message || 'Failed to unblock'); }
  };

  const totalBadge = received.length;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
         onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md flex flex-col max-h-[80vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h2 className="font-bold text-slate-800 text-lg">Connections</h2>
          <button onClick={onClose}
                  className="text-slate-400 hover:text-slate-600 text-xl leading-none">✕</button>
        </div>

        {/* Tabs */}
        <div className="flex border-b border-slate-100">
          {[
            { key: 'received', label: 'Received', count: received.length },
            { key: 'sent',     label: 'Sent',     count: sent.length     },
            { key: 'blocked',  label: 'Blocked',  count: blocked.length  },
          ].map(t => (
            <button key={t.key} onClick={() => setTab(t.key)}
                    className={`flex-1 py-2.5 text-sm font-semibold transition-colors relative
                                ${tab === t.key
                                  ? 'text-sky-600 border-b-2 border-sky-500'
                                  : 'text-slate-400 hover:text-slate-600'}`}>
              {t.label}
              {t.count > 0 && (
                <span className={`ml-1.5 text-xs px-1.5 py-0.5 rounded-full font-bold
                                  ${tab === t.key
                                    ? 'bg-sky-100 text-sky-600'
                                    : 'bg-slate-100 text-slate-500'}`}>
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center py-12 text-slate-400 text-sm">
              Loading…
            </div>
          ) : (
            <>
              {/* ── Received ── */}
              {tab === 'received' && (
                received.length === 0
                  ? <Empty icon="📭" text="No pending requests" />
                  : received.map(req => (
                    <div key={req.id}
                         className="flex items-center gap-3 px-5 py-4 border-b border-slate-50">
                      <Avatar name={req.senderName} photo={req.senderPhotoUrl}/>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-slate-800 truncate">
                          {req.senderName}
                        </p>
                        <p className="text-xs text-slate-400">
                          {formatDistanceToNow(new Date(req.createdAt), { addSuffix: true })}
                        </p>
                      </div>
                      <div className="flex gap-2 flex-shrink-0">
                        <button onClick={() => handleAccept(req)}
                                className="px-3 py-1.5 rounded-lg bg-sky-500 text-white
                                           text-xs font-bold hover:bg-sky-600 transition-colors">
                          ✅ Accept
                        </button>
                        <button onClick={() => handleReject(req)}
                                className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600
                                           text-xs font-bold hover:bg-slate-200 transition-colors">
                          ✕ Reject
                        </button>
                      </div>
                    </div>
                  ))
              )}

              {/* ── Sent ── */}
              {tab === 'sent' && (
                sent.length === 0
                  ? <Empty icon="📤" text="No pending sent requests" />
                  : sent.map(req => (
                    <div key={req.id}
                         className="flex items-center gap-3 px-5 py-4 border-b border-slate-50">
                      <Avatar name={req.receiverName} photo={req.receiverPhotoUrl}/>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-slate-800 truncate">
                          {req.receiverName}
                        </p>
                        <p className="text-xs text-slate-400">
                          Sent {formatDistanceToNow(new Date(req.createdAt), { addSuffix: true })}
                        </p>
                      </div>
                      <button onClick={() => handleCancel(req)}
                              className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600
                                         text-xs font-bold hover:bg-red-50 hover:text-red-500
                                         transition-colors flex-shrink-0">
                        Cancel
                      </button>
                    </div>
                  ))
              )}

              {/* ── Blocked ── */}
              {tab === 'blocked' && (
                blocked.length === 0
                  ? <Empty icon="🚫" text="No blocked users" />
                  : blocked.map(u => (
                    <div key={u.id}
                         className="flex items-center gap-3 px-5 py-4 border-b border-slate-50">
                      <Avatar name={u.displayName} photo={u.profilePhotoUrl}/>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-semibold text-slate-800 truncate">
                          {u.displayName}
                        </p>
                        <p className="text-xs text-slate-400">Blocked</p>
                      </div>
                      <button onClick={() => handleUnblock(u)}
                              className="px-3 py-1.5 rounded-lg bg-slate-100 text-slate-600
                                         text-xs font-bold hover:bg-sky-50 hover:text-sky-600
                                         transition-colors flex-shrink-0">
                        Unblock
                      </button>
                    </div>
                  ))
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function Avatar({ name, photo }) {
  const ini = name ? (name.trim().split(' ').length >= 2
    ? (name.trim().split(' ')[0][0] + name.trim().split(' ')[1][0]).toUpperCase()
    : name.trim()[0].toUpperCase()) : '?';
  return (
    <div className="w-10 h-10 rounded-full bg-sky-100 text-sky-700 font-bold text-sm
                    flex items-center justify-center flex-shrink-0 overflow-hidden">
      {photo
        ? <img src={photo} alt="" className="w-full h-full object-cover"/>
        : ini}
    </div>
  );
}

function Empty({ icon, text }) {
  return (
    <div className="flex flex-col items-center justify-center py-16 gap-2">
      <span className="text-4xl">{icon}</span>
      <p className="text-sm text-slate-400">{text}</p>
    </div>
  );
}
