import { useState } from 'react';
import { users } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

export default function ProfileModal({ onClose }) {
  const { currentUser, updateProfile, refreshMe } = useAuth();

  const [displayName,    setDisplayName]    = useState(currentUser?.displayName || '');
  const [statusMessage,  setStatusMessage]  = useState(currentUser?.statusMessage || '');
  const [loading,        setLoading]        = useState(false);

  const handleSave = async () => {
    if (!displayName.trim()) { toast.error('Name cannot be empty.'); return; }
    setLoading(true);
    try {
      const { data } = await users.updateMe({ displayName: displayName.trim(), statusMessage });
      updateProfile({ displayName: data.displayName, statusMessage: data.statusMessage });
      toast.success('Profile updated!');
      onClose();
    } catch (e) { toast.error(e); }
    finally { setLoading(false); }
  };

  const handlePhotoUpload = async (e) => {
    const file = e.target.files[0];
    e.target.value = '';          // reset so same file can be picked again
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      toast.error('Please choose an image file (JPG, PNG, etc.)');
      return;
    }
    const fd = new FormData();
    fd.append('file', file);
    try {
      const { data } = await users.uploadPhoto(fd);
      updateProfile({ profilePhotoUrl: data.photoUrl });
      toast.success('Photo updated!');
    } catch (err) {
      console.error('Photo upload error:', err);
      toast.error(typeof err === 'string' ? err : (err?.message || 'Photo upload failed'));
    }
  };

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4"
         onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-sm"
           onClick={e => e.stopPropagation()}>

        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100">
          <h2 className="text-base font-bold text-slate-800">Your Profile</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-600 text-xl">✕</button>
        </div>

        <div className="p-5 space-y-5">
          {/* Avatar + upload */}
          <div className="flex flex-col items-center gap-3">
            <label className="relative cursor-pointer group">
              <div className="w-20 h-20 rounded-full overflow-hidden bg-gradient-to-br from-sky-400 to-sky-600
                              flex items-center justify-center text-white text-2xl font-bold shadow-lg">
                {currentUser?.profilePhotoUrl
                  ? <img src={currentUser.profilePhotoUrl}
                         alt=""
                         className="w-full h-full object-cover"
                         onError={e => {
                           // Presigned URL expired — hide the broken <img> so
                           // the gradient + initial-letter fallback (which is
                           // the parent <div>'s text content) shows through,
                           // and refreshMe() to fetch a new URL for the next
                           // render. AuthContext throttles refreshMe to 30 s.
                           e.currentTarget.style.display = 'none';
                           refreshMe();
                         }}/>
                  : currentUser?.displayName?.[0]?.toUpperCase()}
              </div>
              <div className="absolute inset-0 rounded-full bg-black/30 flex items-center justify-center
                              opacity-0 group-hover:opacity-100 transition-opacity">
                <span className="text-white text-xs font-semibold">Change</span>
              </div>
              <input type="file" accept="image/*" className="hidden" onChange={handlePhotoUpload}/>
            </label>
            <p className="text-xs text-slate-400">Click to change photo</p>
          </div>

          {/* Display name */}
          <div>
            <label className="block text-sm font-semibold text-slate-600 mb-1">Display name</label>
            <input className="input" value={displayName}
                   onChange={e => setDisplayName(e.target.value)}/>
          </div>

          {/* Status */}
          <div>
            <label className="block text-sm font-semibold text-slate-600 mb-1">Status</label>
            <input className="input" value={statusMessage}
                   onChange={e => setStatusMessage(e.target.value)}
                   placeholder="What are you sharing today?"/>
          </div>

          {/* Info (read-only) */}
          <div className="bg-slate-50 rounded-xl p-3 space-y-1">
            {currentUser?.mobileNumber && (
              <p className="text-xs text-slate-500">📱 {currentUser.mobileNumber}</p>
            )}
            {currentUser?.email && (
              <p className="text-xs text-slate-500">✉️ {currentUser.email}</p>
            )}
          </div>

          <button onClick={handleSave} disabled={loading} className="btn-primary">
            {loading ? 'Saving…' : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
}
