import { useEffect, useState } from 'react';

/**
 * Help dialog shown when desktop notifications are in the "denied" state.
 * Browsers don't let JavaScript reset this — the user has to flip the
 * permission from the URL-bar site-settings panel themselves. This modal
 * walks them through it visually.
 *
 * Props:
 *   onClose — () => void, fires on cancel/close
 *   onTryAgain — () => void, attempts Notification.requestPermission() again
 *                (useful if the user just changed the setting in another tab)
 */
export default function NotificationHelpModal({ onClose, onTryAgain }) {
  // Pick browser-specific copy so the steps match what the user actually sees.
  const browser = (() => {
    const ua = navigator.userAgent;
    if (/Edg\//.test(ua))     return 'edge';
    if (/Chrome\//.test(ua))  return 'chrome';
    if (/Firefox\//.test(ua)) return 'firefox';
    if (/Safari\//.test(ua))  return 'safari';
    return 'other';
  })();

  const [reloading, setReloading] = useState(false);

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  const steps = ({
    chrome: [
      { num: 1, text: 'Click the 🔒 lock / tune icon at the very left of the address bar.' },
      { num: 2, text: 'In the dropdown, find "Notifications".' },
      { num: 3, text: 'Change it from "Block" to "Allow".' },
      { num: 4, text: 'Reload the page so the change takes effect.' },
    ],
    edge: [
      { num: 1, text: 'Click the 🔒 lock icon at the left of the address bar.' },
      { num: 2, text: 'Open "Permissions for this site" → "Notifications".' },
      { num: 3, text: 'Switch from "Block" to "Allow".' },
      { num: 4, text: 'Reload the page.' },
    ],
    firefox: [
      { num: 1, text: 'Click the 🛡 shield icon next to the address bar.' },
      { num: 2, text: 'Choose "More information" → "Permissions".' },
      { num: 3, text: 'Find "Send notifications" and clear "Block".' },
      { num: 4, text: 'Reload the page.' },
    ],
    safari: [
      { num: 1, text: 'Open Safari → Settings (⌘ + ,).' },
      { num: 2, text: 'Go to the "Websites" tab → "Notifications".' },
      { num: 3, text: 'Find this site in the list and switch it to "Allow".' },
      { num: 4, text: 'Reload the page.' },
    ],
    other: [
      { num: 1, text: 'Open this site\'s permissions in your browser settings.' },
      { num: 2, text: 'Find "Notifications" and switch it from Block to Allow.' },
      { num: 3, text: 'Reload the page.' },
    ],
  })[browser];

  const browserLabel = ({
    chrome: 'Chrome', edge: 'Edge', firefox: 'Firefox',
    safari: 'Safari', other: 'your browser',
  })[browser];

  return (
    <div className="fixed inset-0 z-[400] flex items-center justify-center p-4"
         style={{ background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(4px)' }}
         onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="w-full max-w-lg rounded-2xl overflow-hidden"
           style={{
             background: 'linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%)',
             border: '1px solid rgba(255,255,255,0.8)',
             boxShadow: '0 24px 56px rgba(0,0,0,0.35)',
           }}>

        {/* Header */}
        <div className="flex items-start gap-3 px-6 py-4 border-b border-slate-100">
          <div className="w-12 h-12 rounded-xl flex-shrink-0 flex items-center justify-center
                          text-2xl bg-red-50 border border-red-100">🚫</div>
          <div className="flex-1 min-w-0">
            <h3 className="text-gray-900 font-bold text-base leading-tight">
              Notifications are blocked
            </h3>
            <p className="text-gray-600 text-xs mt-0.5">
              {browserLabel} won't let this page ask again until you turn it back on.
              JavaScript can't undo this — it's a browser-side setting only you can change.
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg text-slate-500 hover:bg-slate-100 transition-colors text-lg leading-none"
            title="Close">
            ×
          </button>
        </div>

        {/* Browser badge */}
        <div className="px-6 pt-3">
          <span className="inline-flex items-center gap-1 text-[11px] font-bold
                           px-2 py-0.5 rounded-full bg-sky-100 text-sky-700">
            ✦ Steps for {browserLabel}
          </span>
        </div>

        {/* Steps */}
        <ol className="px-6 py-3 space-y-2.5">
          {steps.map(s => (
            <li key={s.num} className="flex items-start gap-3">
              <span className="w-6 h-6 rounded-full bg-sky-500 text-white text-xs font-bold
                               flex-shrink-0 flex items-center justify-center mt-0.5">
                {s.num}
              </span>
              <span className="text-sm text-slate-800 leading-snug">{s.text}</span>
            </li>
          ))}
        </ol>

        {/* Visual hint — illustrate the lock icon */}
        <div className="mx-6 mb-3 px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50">
          <p className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">
            What to look for
          </p>
          <div className="flex items-center gap-2 text-xs text-slate-700">
            <span className="px-2 py-1 rounded-lg bg-white border border-slate-300 shadow-sm">
              🔒 {window.location.host}
            </span>
            <span className="text-slate-400">←</span>
            <span>Click here, then look for "Notifications"</span>
          </div>
        </div>

        {/* Footer / actions */}
        <div className="px-6 py-4 border-t border-slate-100 flex flex-wrap gap-2 justify-end">
          <button
            onClick={onClose}
            style={{
              padding: '8px 16px', borderRadius: '12px',
              fontSize: '0.8rem', fontWeight: 600, color: '#475569',
              background: '#f1f5f9', border: '1px solid #e2e8f0', cursor: 'pointer',
            }}>
            I'll do it later
          </button>
          <button
            onClick={() => {
              // Re-attempt the prompt. In some browsers this will just resolve
              // 'denied' silently (in which case the modal stays open with no
              // further effect), but in others — Safari, Firefox after the
              // user clears the block — it actually re-shows the dialog.
              try { onTryAgain?.(); } catch {}
            }}
            style={{
              padding: '8px 16px', borderRadius: '12px',
              fontSize: '0.8rem', fontWeight: 700, color: '#0284c7',
              background: '#e0f2fe', border: '1px solid #7dd3fc', cursor: 'pointer',
            }}>
            🔁 Try again
          </button>
          <button
            onClick={() => { setReloading(true); window.location.reload(); }}
            disabled={reloading}
            style={{
              padding: '8px 16px', borderRadius: '12px',
              fontSize: '0.8rem', fontWeight: 700, color: 'white',
              background: '#0F172A', border: 'none',
              cursor: reloading ? 'not-allowed' : 'pointer',
              boxShadow: '0 4px 16px rgba(15,23,42,0.25)',
              opacity: reloading ? 0.6 : 1,
            }}>
            {reloading ? '⏳ Reloading…' : '🔄 Reload page'}
          </button>
        </div>
      </div>
    </div>
  );
}
