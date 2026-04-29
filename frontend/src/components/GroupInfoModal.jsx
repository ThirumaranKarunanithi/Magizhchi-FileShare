import { useState, useEffect } from 'react';
import { conversations, users, connections } from '../services/api';
import { useAuth } from '../context/AuthContext';
import Avatar from './Avatar';
import toast from 'react-hot-toast';

/**
 * GroupInfoModal — slide-in panel for group management.
 *
 * Props:
 *   conversation  – the current GROUP conversation object
 *   onClose()     – called to dismiss
 *   onUpdated()   – called after any membership change (parent can refresh)
 */
export default function GroupInfoModal({ conversation, onClose, onUpdated }) {
  const { currentUser } = useAuth();

  const [members,       setMembers]       = useState([]);
  const [loadingM,      setLoadingM]      = useState(true);
  const [searchQuery,   setSearchQuery]   = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching,     setSearching]     = useState(false);
  const [actionLoading, setActionLoading] = useState(null); // userId being acted on

  // Is the current user an admin of this group?
  const [isAdmin, setIsAdmin] = useState(false);

  // Inline group-name editor — entered by clicking the pencil next to the
  // header name. Admin-only; the input is pre-seeded with the current name
  // so a user can do a quick fix-typo edit without retyping.
  const [editingName,   setEditingName]   = useState(false);
  const [draftName,     setDraftName]     = useState(conversation.name ?? '');
  const [savingName,    setSavingName]    = useState(false);
  const [currentName,   setCurrentName]   = useState(conversation.name ?? '');
  // If the parent passes a new conversation prop, reset our local copy.
  useEffect(() => {
    setCurrentName(conversation.name ?? '');
    setDraftName(conversation.name ?? '');
  }, [conversation.id, conversation.name]);

  const submitNameChange = async () => {
    const trimmed = draftName.trim();
    if (!trimmed) { toast.error('Group name cannot be empty.'); return; }
    if (trimmed === currentName) { setEditingName(false); return; }
    setSavingName(true);
    try {
      const { data } = await conversations.rename(conversation.id, trimmed);
      // Update local view immediately so the user sees the change without
      // a re-mount, then ping the parent so the sidebar's conversation list
      // reflects the new name.
      setCurrentName(data?.name ?? trimmed);
      setEditingName(false);
      toast.success('Group renamed.');
      onUpdated?.();
    } catch (e) {
      toast.error(typeof e === 'string' ? e : 'Could not rename group.');
    } finally {
      setSavingName(false);
    }
  };

  // ── Load members ────────────────────────────────────────────────────────────
  const loadMembers = async () => {
    setLoadingM(true);
    try {
      const { data } = await conversations.members(conversation.id);
      setMembers(data);
      const me = data.find(m => m.userId === currentUser?.id);
      setIsAdmin(me?.role === 'ADMIN');
    } catch (e) {
      toast.error('Could not load members.');
    } finally {
      setLoadingM(false);
    }
  };

  useEffect(() => { loadMembers(); }, [conversation.id]); // eslint-disable-line

  // ── Search connected users to add ───────────────────────────────────────────
  useEffect(() => {
    if (!searchQuery.trim()) { setSearchResults([]); return; }
    const t = setTimeout(async () => {
      setSearching(true);
      try {
        const { data } = await users.search(searchQuery.trim());
        // Keep only CONNECTED users who aren't already members
        const memberIds = new Set(members.map(m => m.userId));
        setSearchResults(
          data.filter(u => u.connectionStatus === 'CONNECTED' && !memberIds.has(u.id))
        );
      } catch { setSearchResults([]); }
      finally { setSearching(false); }
    }, 350);
    return () => clearTimeout(t);
  }, [searchQuery, members]);

  // ── Add member ──────────────────────────────────────────────────────────────
  const handleAdd = async (userId, displayName) => {
    setActionLoading(userId);
    try {
      await conversations.addMember(conversation.id, userId);
      toast.success(`${displayName} added to the group.`);
      setSearchQuery('');
      setSearchResults([]);
      await loadMembers();
      onUpdated?.();
    } catch (e) {
      toast.error(typeof e === 'string' ? e : 'Could not add member.');
    } finally { setActionLoading(null); }
  };

  // ── Promote / Demote ─────────────────────────────────────────────────────────
  const handleRoleChange = async (userId, displayName, currentRole) => {
    const newRole   = currentRole === 'ADMIN' ? 'MEMBER' : 'ADMIN';
    const actionMsg = newRole === 'ADMIN'
      ? `Make ${displayName} an admin of this group?`
      : `Remove admin rights from ${displayName}?`;
    if (!window.confirm(actionMsg)) return;

    setActionLoading(userId);
    try {
      await conversations.setMemberRole(conversation.id, userId, newRole);
      toast.success(
        newRole === 'ADMIN'
          ? `${displayName} is now an admin.`
          : `${displayName} is now a regular member.`
      );
      await loadMembers();
    } catch (e) {
      toast.error(typeof e === 'string' ? e : 'Could not update role.');
    } finally { setActionLoading(null); }
  };

  // ── Remove member ────────────────────────────────────────────────────────────
  const handleRemove = async (userId, displayName) => {
    const isSelf = userId === currentUser?.id;
    const msg = isSelf
      ? 'Leave this group? You will lose access to the conversation.'
      : `Remove ${displayName} from the group?`;
    if (!window.confirm(msg)) return;

    setActionLoading(userId);
    try {
      await conversations.removeMember(conversation.id, userId);
      toast.success(isSelf ? 'You left the group.' : `${displayName} removed.`);
      await loadMembers();
      onUpdated?.();
      if (isSelf) onClose(); // left group → close modal
    } catch (e) {
      toast.error(typeof e === 'string' ? e : 'Could not remove member.');
    } finally { setActionLoading(null); }
  };

  // ── Render ──────────────────────────────────────────────────────────────────
  return (
    // Backdrop
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4"
         style={{ background: 'rgba(0,0,0,0.55)' }}
         onClick={e => { if (e.target === e.currentTarget) onClose(); }}>

      {/* Panel */}
      <div className="w-full max-w-md rounded-2xl flex flex-col overflow-hidden shadow-2xl"
           style={{
             background: 'linear-gradient(160deg, #0c1a2e 0%, #0f2342 50%, #0c1a2e 100%)',
             border: '1px solid rgba(255,255,255,0.12)',
             maxHeight: '85vh',
           }}>

        {/* Dot texture */}
        <div className="absolute inset-0 pointer-events-none rounded-2xl"
             style={{
               backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.06) 1px, transparent 1px)',
               backgroundSize: '20px 20px',
             }}/>

        {/* Header */}
        <div className="relative flex items-center gap-3 px-5 py-4"
             style={{ borderBottom: '1px solid rgba(255,255,255,0.1)' }}>
          {/* Group icon */}
          <div className="w-10 h-10 rounded-xl overflow-hidden flex-shrink-0 bg-white/10
                          flex items-center justify-center text-lg">
            {conversation.iconUrl
              ? <img src={conversation.iconUrl} alt="" className="w-full h-full object-cover"
                     onError={e => { e.currentTarget.style.display='none'; }}/>
              : '👥'}
          </div>
          <div className="flex-1 min-w-0">
            {editingName ? (
              <div className="flex items-center gap-2">
                <input
                  autoFocus
                  value={draftName}
                  onChange={e => setDraftName(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter') submitNameChange();
                    else if (e.key === 'Escape') {
                      setEditingName(false);
                      setDraftName(currentName);
                    }
                  }}
                  maxLength={80}
                  disabled={savingName}
                  className="flex-1 min-w-0 px-2 py-1 rounded-lg text-sm font-bold text-white
                             outline-none focus:ring-2 focus:ring-sky-400/50 disabled:opacity-60"
                  style={{
                    background: 'rgba(255,255,255,0.1)',
                    border: '1px solid rgba(255,255,255,0.18)',
                  }}/>
                <button onClick={submitNameChange} disabled={savingName}
                        title="Save"
                        className="text-sm font-bold w-7 h-7 flex items-center justify-center
                                   rounded-lg text-emerald-300 hover:bg-emerald-400/20
                                   transition-all disabled:opacity-40">
                  {savingName ? '…' : '✓'}
                </button>
                <button onClick={() => { setEditingName(false); setDraftName(currentName); }}
                        disabled={savingName}
                        title="Cancel"
                        className="text-sm font-bold w-7 h-7 flex items-center justify-center
                                   rounded-lg text-white/60 hover:text-white hover:bg-white/10
                                   transition-all disabled:opacity-40">
                  ✕
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <h2 className="text-white font-bold text-base truncate" title={currentName}>
                  {currentName}
                </h2>
                {isAdmin && (
                  <button
                    onClick={() => { setDraftName(currentName); setEditingName(true); }}
                    title="Rename group"
                    className="text-white/50 hover:text-white text-xs w-6 h-6 flex items-center
                               justify-center rounded-md hover:bg-white/10 transition-all flex-shrink-0">
                    ✎
                  </button>
                )}
              </div>
            )}
            <p className="text-xs text-white/50">{members.length} member{members.length !== 1 ? 's' : ''}</p>
          </div>
          <button onClick={onClose}
                  className="w-8 h-8 rounded-xl flex items-center justify-center
                             text-white/60 hover:text-white hover:bg-white/10 transition-all text-sm">
            ✕
          </button>
        </div>

        {/* Scrollable body */}
        <div className="relative flex-1 overflow-y-auto">

          {/* ── Add member search (admin only) ── */}
          {isAdmin && (
            <div className="px-5 py-4" style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
              <p className="text-xs font-semibold text-white/50 uppercase tracking-wide mb-2">
                Add Member
              </p>
              <div className="relative">
                <input
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  placeholder="Search connected users…"
                  className="w-full px-4 py-2.5 rounded-xl text-sm text-white placeholder-white/30
                             outline-none focus:ring-2 focus:ring-sky-400/50"
                  style={{
                    background: 'rgba(255,255,255,0.08)',
                    border: '1px solid rgba(255,255,255,0.15)',
                  }}
                />
                {searching && (
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-white/40 animate-pulse">
                    …
                  </span>
                )}
              </div>

              {/* Search results */}
              {searchResults.length > 0 && (
                <div className="mt-2 rounded-xl overflow-hidden"
                     style={{ border: '1px solid rgba(255,255,255,0.1)' }}>
                  {searchResults.map((u, idx) => (
                    <div key={u.id}
                         className="flex items-center gap-3 px-4 py-2.5"
                         style={{
                           borderBottom: idx < searchResults.length - 1
                             ? '1px solid rgba(255,255,255,0.07)' : 'none',
                           background: 'rgba(255,255,255,0.04)',
                         }}>
                      <Avatar name={u.displayName} photoUrl={u.profilePhotoUrl} size="sm"/>
                      <span className="flex-1 text-sm text-white truncate">{u.displayName}</span>
                      <button
                        onClick={() => handleAdd(u.id, u.displayName)}
                        disabled={actionLoading === u.id}
                        className="text-xs font-bold px-3 py-1 rounded-lg transition-all
                                   disabled:opacity-50"
                        style={{
                          background: 'rgba(56,189,248,0.2)',
                          border: '1px solid rgba(56,189,248,0.4)',
                          color: '#7dd3fc',
                        }}>
                        {actionLoading === u.id ? '…' : '+ Add'}
                      </button>
                    </div>
                  ))}
                </div>
              )}

              {searchQuery.trim() && !searching && searchResults.length === 0 && (
                <p className="text-xs text-white/30 text-center mt-2 py-1">
                  No connected users found matching "{searchQuery}"
                </p>
              )}
            </div>
          )}

          {/* ── Member list ── */}
          <div className="px-5 py-4">
            <p className="text-xs font-semibold text-white/50 uppercase tracking-wide mb-3">
              Members
            </p>

            {loadingM ? (
              <div className="flex justify-center py-8">
                <span className="text-white/40 text-sm animate-pulse">Loading…</span>
              </div>
            ) : (
              <div className="flex flex-col gap-1">
                {members.map(m => {
                  const isSelf        = m.userId === currentUser?.id;
                  const isAdminMember = m.role === 'ADMIN';
                  // Admins can remove anyone; anyone can leave themselves
                  const canRemove     = isAdmin ? true : isSelf;
                  // Only admins can change roles; cannot change your own role
                  const canChangeRole = isAdmin && !isSelf;
                  const isActing      = actionLoading === m.userId;

                  return (
                    <div key={m.userId}
                         className="flex items-center gap-3 px-3 py-2.5 rounded-xl transition-colors group"
                         style={{ background: 'rgba(255,255,255,0.04)' }}
                         onMouseEnter={e => e.currentTarget.style.background = 'rgba(255,255,255,0.08)'}
                         onMouseLeave={e => e.currentTarget.style.background = 'rgba(255,255,255,0.04)'}>

                      <Avatar name={m.displayName} photoUrl={m.profilePhotoUrl} size="sm"/>

                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-sm font-medium text-white truncate">
                            {m.displayName}
                            {isSelf && <span className="text-white/40 font-normal"> (you)</span>}
                          </span>
                          {/* Role badge */}
                          <span className="flex-shrink-0 text-[10px] font-bold px-1.5 py-0.5 rounded-full"
                                style={isAdminMember ? {
                                  background: 'rgba(250,204,21,0.2)',
                                  border: '1px solid rgba(250,204,21,0.35)',
                                  color: '#fde68a',
                                } : {
                                  background: 'rgba(255,255,255,0.1)',
                                  border: '1px solid rgba(255,255,255,0.15)',
                                  color: 'rgba(255,255,255,0.5)',
                                }}>
                            {isAdminMember ? '★ Admin' : 'Member'}
                          </span>
                        </div>
                      </div>

                      {/* Action buttons — visible on row hover */}
                      <div className="flex items-center gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0">

                        {/* Promote / Demote role button */}
                        {canChangeRole && (
                          <button
                            onClick={() => handleRoleChange(m.userId, m.displayName, m.role)}
                            disabled={isActing}
                            title={isAdminMember ? 'Remove admin' : 'Make admin'}
                            className="text-xs font-semibold px-2.5 py-1 rounded-lg
                                       transition-all disabled:opacity-40"
                            style={isAdminMember ? {
                              background: 'rgba(250,204,21,0.12)',
                              border: '1px solid rgba(250,204,21,0.30)',
                              color: '#fcd34d',
                            } : {
                              background: 'rgba(56,189,248,0.15)',
                              border: '1px solid rgba(56,189,248,0.30)',
                              color: '#7dd3fc',
                            }}>
                            {isActing ? '…' : isAdminMember ? '★ Remove Admin' : '☆ Make Admin'}
                          </button>
                        )}

                        {/* Remove / Leave button */}
                        {canRemove && (
                          <button
                            onClick={() => handleRemove(m.userId, m.displayName)}
                            disabled={isActing}
                            title={isSelf ? 'Leave group' : 'Remove member'}
                            className="text-xs font-semibold px-2.5 py-1 rounded-lg
                                       transition-all disabled:opacity-40"
                            style={{
                              background: isSelf ? 'rgba(251,191,36,0.15)' : 'rgba(239,68,68,0.15)',
                              border: isSelf ? '1px solid rgba(251,191,36,0.3)' : '1px solid rgba(239,68,68,0.3)',
                              color: isSelf ? '#fcd34d' : '#fca5a5',
                            }}>
                            {isActing ? '…' : isSelf ? 'Leave' : 'Remove'}
                          </button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="relative px-5 py-3 flex justify-end"
             style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <button onClick={onClose}
                  className="text-sm font-semibold px-4 py-2 rounded-xl text-white/70
                             hover:text-white hover:bg-white/10 transition-all">
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
