import { useEffect, useState } from 'react';

/**
 * Mobile interstitial — when the web app is opened on a phone-sized
 * viewport, render a "use desktop or the mobile app" screen instead of
 * the regular React tree.
 *
 * Detection logic:
 *   - innerWidth < 768 px  (Tailwind's md breakpoint)  OR
 *   - user-agent matches a phone identifier
 *   AND the user hasn't explicitly opted to "Continue anyway".
 *
 * Tablets (≥768 px width) get the desktop layout; the layout is responsive
 * enough for that. The escape hatch (`Continue anyway`) is persisted in
 * localStorage so a reload doesn't punish the user once they've dismissed
 * the gate.
 *
 * Drop this around <App/> in main.jsx; it returns its children unchanged
 * when the device passes the desktop check.
 */
const PHONE_UA_RE = /Android|webOS|iPhone|iPod|BlackBerry|IEMobile|Opera Mini/i;
const BYPASS_KEY  = 'msh:dismissedMobileGate';

const PLAY_STORE_URL =
  'https://play.google.com/store/apps/details?id=com.magizhchi.share';

function detectMobile() {
  if (typeof window === 'undefined') return false;
  const narrow      = window.innerWidth < 768;
  const phoneAgent  = PHONE_UA_RE.test(navigator.userAgent || '');
  return narrow || phoneAgent;
}

export default function MobileGate({ children }) {
  const [isMobile, setIsMobile]   = useState(detectMobile);
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem(BYPASS_KEY) === '1'; }
    catch { return false; }
  });

  // Re-check on resize so rotating a tablet (or shrinking devtools) updates
  // the gate without a refresh.
  useEffect(() => {
    const onResize = () => setIsMobile(detectMobile());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  if (!isMobile || dismissed) return children;

  const handleContinueAnyway = () => {
    try { localStorage.setItem(BYPASS_KEY, '1'); } catch { /* storage full */ }
    setDismissed(true);
  };

  return (
    <div className="min-h-screen flex items-center justify-center px-6 py-10"
         style={{
           background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)',
         }}>
      <div className="w-full max-w-sm rounded-3xl shadow-2xl p-7 text-center"
           style={{
             background: 'rgba(255,255,255,0.14)',
             backdropFilter: 'blur(14px)',
             WebkitBackdropFilter: 'blur(14px)',
             border: '1px solid rgba(255,255,255,0.25)',
           }}>

        {/* Logo */}
        <div className="w-16 h-16 mx-auto rounded-2xl flex items-center justify-center text-3xl shadow-lg mb-4"
             style={{
               background: 'rgba(255,255,255,0.20)',
               border: '1px solid rgba(255,255,255,0.30)',
             }}>
          📂
        </div>

        <h1 className="text-white text-xl font-bold mb-2">Magizhchi Box</h1>
        <p className="text-white/85 text-sm leading-relaxed mb-6">
          The web app is built for larger screens.
          <br/>
          Please open it on a <span className="font-semibold">desktop</span>,
          {' '}or use our <span className="font-semibold">mobile app</span> for
          the best experience.
        </p>

        <a
          href={PLAY_STORE_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="block w-full rounded-xl py-3 px-4 mb-3 font-semibold text-sky-700"
          style={{
            background: '#fff',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
          }}>
          📱 Get the Android app
        </a>

        <button
          onClick={handleContinueAnyway}
          className="text-white/80 text-xs underline underline-offset-2 hover:text-white">
          Continue on web anyway
        </button>

        <p className="text-white/60 text-[11px] mt-5 leading-snug">
          Tip: rotating to landscape or using a tablet (≥768 px wide)
          {' '}also unlocks the desktop view.
        </p>
      </div>
    </div>
  );
}
