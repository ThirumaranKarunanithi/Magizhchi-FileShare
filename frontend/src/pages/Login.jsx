import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

// ── Glass input with animated focus border ────────────────────────────────────
function GlassInput({ label, className = '', ...props }) {
  const [focused, setFocused] = useState(false);
  return (
    <div>
      {label && (
        <label className="block text-[11px] font-bold text-white/55 mb-1.5
                          uppercase tracking-widest">
          {label}
        </label>
      )}
      <input
        {...props}
        className={`w-full px-4 py-3 rounded-xl text-sm text-white outline-none
                    transition-all duration-200 placeholder-white/25 ${className}`}
        style={{
          background: focused ? 'rgba(255,255,255,0.18)' : 'rgba(255,255,255,0.12)',
          border:     focused ? '1px solid rgba(255,255,255,0.65)' : '1px solid rgba(255,255,255,0.25)',
          boxShadow:  focused ? '0 0 0 3px rgba(255,255,255,0.10)' : 'none',
        }}
        onFocus={e => { setFocused(true);  props.onFocus?.(e); }}
        onBlur={e  => { setFocused(false); props.onBlur?.(e);  }}
      />
    </div>
  );
}

// ── Glass card style ──────────────────────────────────────────────────────────
const GLASS = {
  background:           'rgba(255,255,255,0.13)',
  backdropFilter:       'blur(28px)',
  WebkitBackdropFilter: 'blur(28px)',
  border:               '1px solid rgba(255,255,255,0.28)',
  boxShadow:            '0 8px 40px rgba(0,0,0,0.18), inset 0 1px 0 rgba(255,255,255,0.35)',
};

// ── Main component ────────────────────────────────────────────────────────────
export default function Login() {
  const navigate  = useNavigate();
  const { login } = useAuth();

  const [step,        setStep]        = useState(1);
  const [identifier,  setIdentifier]  = useState('');
  const [otp,         setOtp]         = useState('');
  const [loading,     setLoading]     = useState(false);
  const [error,       setError]       = useState('');
  const [resendTimer, setResendTimer] = useState(0);

  const handleSendOtp = async e => {
    e.preventDefault();
    if (!identifier.trim()) { setError('Please enter your mobile number or email.'); return; }
    setError(''); setLoading(true);
    try {
      await auth.loginSendOtp({ identifier: identifier.trim() });
      setStep(2); startCountdown(60);
      toast.success('Verification code sent!');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  const handleVerify = async e => {
    e.preventDefault();
    if (!otp.trim()) { setError('Please enter the verification code.'); return; }
    setError(''); setLoading(true);
    try {
      const { data } = await auth.loginVerify({ identifier: identifier.trim(), code: otp.trim() });
      login(data); navigate('/');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  const handleResend = async () => {
    if (resendTimer > 0) return;
    try {
      await auth.loginSendOtp({ identifier }); startCountdown(60);
      toast.success('Code resent!');
    } catch (msg) { toast.error(msg); }
  };

  const startCountdown = secs => {
    setResendTimer(secs);
    const t = setInterval(() =>
      setResendTimer(v => { if (v <= 1) { clearInterval(t); return 0; } return v - 1; })
    , 1000);
  };

  return (
    <div className="min-h-screen flex items-center justify-center relative overflow-hidden">

      {/* ── Layer 1: Blue gradient base ── */}
      <div className="absolute inset-0" style={{
        background: 'linear-gradient(150deg, #0c4a6e 0%, #0369a1 35%, #0284c7 65%, #0ea5e9 100%)',
      }}/>

      {/* ── Layer 2: Grid / line-check texture ── */}
      <div className="absolute inset-0 pointer-events-none" style={{
        backgroundImage: [
          'linear-gradient(rgba(255,255,255,0.09) 1px, transparent 1px)',
          'linear-gradient(90deg, rgba(255,255,255,0.09) 1px, transparent 1px)',
        ].join(', '),
        backgroundSize: '26px 26px',
      }}/>

      {/* ── Layer 3: Dot texture on top of grid ── */}
      <div className="absolute inset-0 pointer-events-none" style={{
        backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.22) 1.5px, transparent 1.5px)',
        backgroundSize: '13px 13px',
      }}/>

      {/* ── Soft glow blobs ── */}
      <div className="absolute top-[-10%] right-[-5%] w-[500px] h-[500px] rounded-full
                      pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(56,189,248,0.25) 0%, transparent 70%)' }}/>
      <div className="absolute bottom-[-10%] left-[-5%] w-[450px] h-[450px] rounded-full
                      pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(3,105,161,0.35) 0%, transparent 70%)' }}/>

      {/* ── Cards column ── */}
      <div className="relative z-10 w-full max-w-[420px] mx-4 space-y-3 py-8">

        {/* ─ Logo card ─ */}
        <div className="rounded-2xl px-8 py-5 flex items-center gap-4" style={GLASS}>
          <img src="/logo.png" alt="Magizhchi Box"
               className="h-14 w-14 object-contain select-none flex-shrink-0"
               style={{ filter: 'drop-shadow(0 2px 10px rgba(0,0,0,0.25))' }}
               onError={e => {
                 e.currentTarget.style.display = 'none';
                 e.currentTarget.nextSibling.style.display = 'flex';
               }}/>
          <div style={{ display: 'none' }}
               className="items-center justify-center w-14 h-14 rounded-xl flex-shrink-0"
               onLoad={e => e.currentTarget.style.display = 'flex'}
               id="logo-fb-login">
            <span className="text-3xl">📂</span>
          </div>
          <div>
            <p className="text-white font-extrabold text-base leading-tight">Magizhchi Box</p>
            <p className="text-white/50 text-xs mt-0.5">Secure Cloud Storage, Simplified</p>
          </div>
        </div>

        {/* ─ Form card ─ */}
        <div className="rounded-2xl px-8 py-8" style={GLASS}>

          <div className="mb-6">
            <h2 className="text-white font-extrabold text-xl leading-tight">
              {step === 1 ? 'Welcome back' : 'Enter verification code'}
            </h2>
            <p className="text-white/50 text-xs mt-1.5 leading-relaxed">
              {step === 1
                ? 'Enter your mobile number or email to continue'
                : `A 6-digit code was sent to ${identifier}`}
            </p>
          </div>

          <div className="h-px mb-6" style={{ background: 'rgba(255,255,255,0.15)' }}/>

          {step === 1 ? (
            <form onSubmit={handleSendOtp} className="space-y-5">
              <GlassInput
                label="Mobile number or email"
                value={identifier}
                onChange={e => setIdentifier(e.target.value)}
                placeholder="+91 9876543210 or you@example.com"
                autoFocus/>

              {error && (
                <p className="px-4 py-2.5 rounded-xl text-xs font-semibold text-red-200"
                   style={{ background: 'rgba(239,68,68,0.20)', border: '1px solid rgba(239,68,68,0.35)' }}>
                  ⚠ {error}
                </p>
              )}

              <button
                className="w-full py-3 rounded-xl font-bold text-sky-900 text-sm
                           transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]
                           disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ background: 'rgba(255,255,255,0.94)', boxShadow: '0 4px 20px rgba(0,0,0,0.20)' }}
                disabled={loading}>
                {loading ? '⏳ Sending code…' : 'Continue  →'}
              </button>
            </form>

          ) : (
            <form onSubmit={handleVerify} className="space-y-5">
              <GlassInput
                label="6-digit code"
                value={otp}
                onChange={e => setOtp(e.target.value.replace(/\D/g,'').slice(0,6))}
                placeholder="• • • • • •"
                className="text-center text-3xl tracking-[0.5em] font-bold"
                maxLength={6}
                autoFocus/>

              {error && (
                <p className="px-4 py-2.5 rounded-xl text-xs font-semibold text-red-200"
                   style={{ background: 'rgba(239,68,68,0.20)', border: '1px solid rgba(239,68,68,0.35)' }}>
                  ⚠ {error}
                </p>
              )}

              <button
                className="w-full py-3 rounded-xl font-bold text-sky-900 text-sm
                           transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]
                           disabled:opacity-50 disabled:cursor-not-allowed"
                style={{ background: 'rgba(255,255,255,0.94)', boxShadow: '0 4px 20px rgba(0,0,0,0.20)' }}
                disabled={loading}>
                {loading ? '⏳ Verifying…' : 'Sign In  →'}
              </button>

              <div className="flex items-center justify-between">
                <button type="button" onClick={() => { setStep(1); setOtp(''); setError(''); }}
                        className="text-xs text-white/50 hover:text-white transition-colors">
                  ← Change number
                </button>
                <button type="button" onClick={handleResend} disabled={resendTimer > 0}
                        className={`text-xs transition-colors
                          ${resendTimer > 0 ? 'text-white/25 cursor-not-allowed' : 'text-white/50 hover:text-white'}`}>
                  {resendTimer > 0 ? `Resend in ${resendTimer}s` : 'Resend code'}
                </button>
              </div>
            </form>
          )}
        </div>

        {/* ─ Feature pills row ─ */}
        <div className="flex flex-wrap justify-center gap-2 px-2">
          {[
            { icon: '🔒', text: 'End-to-end secure' },
            { icon: '⚡', text: 'Instant delivery'   },
            { icon: '🗄️', text: '5 GB free'         },
            { icon: '👥', text: 'Team workspaces'    },
          ].map(f => (
            <div key={f.text}
                 className="flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold text-white/80"
                 style={{
                   background: 'rgba(255,255,255,0.10)',
                   border: '1px solid rgba(255,255,255,0.18)',
                   backdropFilter: 'blur(8px)',
                 }}>
              <span>{f.icon}</span>
              <span>{f.text}</span>
            </div>
          ))}
        </div>

        {/* ─ Footer ─ */}
        <p className="text-center text-white/45 text-xs pt-1">
          Don't have an account?{' '}
          <Link to="/register" className="text-white font-bold hover:underline">
            Create one free
          </Link>
        </p>
      </div>
    </div>
  );
}
