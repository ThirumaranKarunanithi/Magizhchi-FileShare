import { useState, useEffect } from 'react';
import { users, conversations } from '../services/api';
import toast from 'react-hot-toast';

export default function NewGroupModal({ onClose, onCreated }) {
  const [name,      setName]      = useState('');
  const [search,    setSearch]    = useState('');
  const [results,   setResults]   = useState([]);
  const [selected,  setSelected]  = useState([]);
  const [icon,      setIcon]      = useState(null);
  const [loading,   setLoading]   = useState(false);

  useEffect(() => {
    if (search.trim().length < 2) { setResults([]); return; }
    const t = setTimeout(() => {
      users.search(search.trim()).then(r => setResults(r.data)).catch(console.error);
    }, 350);
    return () => clearTimeout(t);
  }, [search]);

  const toggle = (user) => {
    setSelected(prev =>
      prev.find(u => u.id === user.id)
        ? prev.filter(u => u.id !== user.id)
        : [...prev, user]);
  };

  const handleCreate = async () => {
    if (!name.trim())           { toast.error('Group name is required.'); return; }
    if (selected.length === 0)  { toast.error('Add at least one member.'); return; }

    setLoading(true);
    try {
      const fd = new FormData();
      fd.append('data', new Blob([JSON.stringify({
        name: name.trim(),
        memberIds: selected.map(u => u.id),
      })], { type: 'application/json' }));
      if (icon) fd.append('icon', icon);

      const { data } = await conversations.createGroup(fd);
      toast.success('Group created!');
      onCreated(data);
      onClose();
    } catch (e) { toast.error(e); }
    finally { setLoading(false); }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
         onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md max-h-[90vh] flex flex-col"
           onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h2 className="text-base font-bold text-slate-800">New Group</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 text-xl">✕</button>
        </div>

        <div className="flex-1 overflow-y-auto p-5 space-y-4">
          {/* Group icon */}
          <div className="flex items-center gap-4">
            <label className="w-14 h-14 rounded-xl bg-sky-100 flex items-center justify-center
                              cursor-pointer hover:bg-sky-200 transition-colors overflow-hidden border-2 border-dashed border-sky-300">
              {icon
                ? <img src={URL.createObjectURL(icon)} alt="" className="w-full h-full object-cover rounded-xl"/>
                : <span className="text-2xl">👥</span>}
              <input type="file" className="hidden" accept="image/*"
                     onChange={e => setIcon(e.target.files[0])}/>
            </label>
            <div className="flex-1">
              <label className="block text-sm font-semibold text-slate-600 mb-1">Group name</label>
              <input className="input" value={name} onChange={e => setName(e.target.value)}
                     placeholder="e.g. Project Files" autoFocus/>
            </div>
          </div>

          {/* Selected members */}
          {selected.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {selected.map(u => (
                <span key={u.id}
                      className="badge-sky flex items-center gap-1 cursor-pointer"
                      onClick={() => toggle(u)}>
                  {u.displayName} ✕
                </span>
              ))}
            </div>
          )}

          {/* Search members */}
          <div>
            <label className="block text-sm font-semibold text-slate-600 mb-1">Add members</label>
            <input className="input" value={search} onChange={e => setSearch(e.target.value)}
                   placeholder="Search by name or number…"/>
          </div>

          {/* Search results */}
          {results.map(u => {
            const picked = !!selected.find(s => s.id === u.id);
            return (
              <button key={u.id} onClick={() => toggle(u)}
                      className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl
                                  border transition-all text-left
                                  ${picked ? 'border-sky-400 bg-sky-50' : 'border-slate-200 hover:bg-slate-50'}`}>
                <div className="w-8 h-8 avatar text-xs">{u.displayName?.[0]?.toUpperCase()}</div>
                <div>
                  <p className="text-sm font-semibold text-slate-800">{u.displayName}</p>
                  <p className="text-xs text-slate-400">{u.mobileNumber || u.email}</p>
                </div>
                {picked && <span className="ml-auto text-sky-500">✓</span>}
              </button>
            );
          })}
        </div>

        <div className="px-5 py-4 border-t border-slate-100">
          <button onClick={handleCreate} disabled={loading} className="btn-primary">
            {loading ? 'Creating…' : `Create Group (${selected.length} member${selected.length !== 1 ? 's' : ''})`}
          </button>
        </div>
      </div>
    </div>
  );
}
