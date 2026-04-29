/**
 * MobilePhoneGate — full-screen redirect shown when the user opens the web
 * app on a phone. The web layout (chat list + chat window side-by-side) is
 * not designed for sub-768 px viewports; phone users get a much better
 * experience in the native Android app.
 *
 * Tablets (iPad portrait/landscape, large Android tablets) are NOT gated —
 * the layout adapts via responsive breakpoints in Sidebar / ChatWindow.
 *
 * The "Continue to web anyway" escape hatch sets a session-storage flag so
 * the user isn't forced through the gate on every navigation. We use
 * sessionStorage (not localStorage) so the gate re-appears on a fresh
 * browser session, which is what we want for accidental dismissals.
 */
const BYPASS_KEY = 'msh:phoneGateBypassed';

export function isPhoneGateBypassed() {
  try { return sessionStorage.getItem(BYPASS_KEY) === '1'; }
  catch { return false; }
}

function bypassPhoneGate() {
  try { sessionStorage.setItem(BYPASS_KEY, '1'); }
  catch { /* private mode — fall through, the page will just refuse to render */ }
}

export default function MobilePhoneGate({ onContinueAnyway }) {
  return (
    <div className="fixed inset-0 z-50 flex flex-col items-center justify-center px-6 py-10
                    text-center"
         style={{
           background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)',
         }}>
      {/* Dot texture overlay (matches the rest of the app's chrome) */}
      <div className="absolute inset-0 pointer-events-none"
           style={{
             backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)',
             backgroundSize: '22px 22px',
           }}/>

      {/* Glass card */}
      <div className="relative z-10 w-full max-w-md rounded-3xl p-7 flex flex-col gap-6"
           style={{
             background: 'rgba(255, 255, 255, 0.12)',
             backdropFilter: 'blur(20px)',
             WebkitBackdropFilter: 'blur(20px)',
             border: '1px solid rgba(255, 255, 255, 0.25)',
             boxShadow: '0 8px 48px rgba(0,0,0,0.18), inset 0 1px 0 rgba(255,255,255,0.3)',
           }}>

        {/* Logo */}
        <div className="flex justify-center">
          <img src="/logo.png" alt="Magizhchi Box"
               className="h-16 select-none drop-shadow-xl"
               onError={e => { e.currentTarget.style.display = 'none'; }}/>
        </div>

        <div>
          <h1 className="text-xl font-extrabold text-white">Magizhchi Box works best on desktop or mobile</h1>
          <p className="text-sky-100/80 text-sm mt-3 leading-relaxed">
            The web app is built for laptops, desktops, and tablets. On your
            phone, please use the native Android app for the best experience —
            uploads, downloads, and notifications are tuned for mobile there.
          </p>
        </div>

        {/* Where to go next */}
        <div className="flex flex-col gap-3">
          {/* Android app — replace with the real Play Store URL once published */}
          <a
            href="https://play.google.com/store"
            target="_blank"
            rel="noopener noreferrer"
            className="w-full py-3 rounded-2xl text-sm font-bold text-sky-900
                       transition-all duration-200 active:scale-[0.98]"
            style={{
              background: 'rgba(255,255,255,0.92)',
              boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
            }}>
            📱 Get the Android app
          </a>

          <p className="text-[11px] text-sky-100/70">
            Or open <span className="font-semibold">magizhchi.app</span> on a desktop browser.
          </p>
        </div>

        {/* Escape hatch */}
        <button
          type="button"
          onClick={() => { bypassPhoneGate(); onContinueAnyway?.(); }}
          className="text-xs text-white/70 underline underline-offset-2 hover:text-white">
          Continue to the web anyway
        </button>
      </div>
    </div>
  );
}
