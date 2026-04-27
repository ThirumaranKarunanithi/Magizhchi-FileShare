import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

// ── Glass input ───────────────────────────────────────────────────────────────
function GlassInput({ label, hint, className = '', ...props }) {
  const [focused, setFocused] = useState(false);
  return (
    <div>
      {label && (
        <div className="flex items-baseline justify-between mb-1.5">
          <label className="text-[11px] font-bold text-white/55 uppercase tracking-widest">
            {label}
          </label>
          {hint && <span className="text-[10px] text-white/35">{hint}</span>}
        </div>
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
export default function Register() {
  const navigate  = useNavigate();
  const { login } = useAuth();

  const [step,        setStep]        = useState(1);
  const [form,        setForm]        = useState({ displayName: '', mobileNumber: '', email: '' });
  const [otp,         setOtp]         = useState('');
  const [loading,     setLoading]     = useState(false);
  const [error,       setError]       = useState('');
  const [resendTimer, setResendTimer] = useState(0);

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  const handleStep1 = async e => {
    e.preventDefault();
    if (!form.displayName.trim()) { setError('Name is required.'); return; }
    if (!form.mobileNumber.trim() && !form.email.trim()) {
      setError('Please provide a mobile number or email.'); return;
    }
    setError(''); setLoading(true);
    try {
      await auth.registerSendOtp({
        displayName:  form.displayName.trim(),
        mobileNumber: form.mobileNumber.trim() || undefined,
        email:        form.email.trim()        || undefined,
      });
      setStep(2); startCountdown(60);
      toast.success('Verification code sent!');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  const handleStep2 = async e => {
    e.preventDefault();
    if (!otp.trim()) { setError('Please enter the verification code.'); return; }
    setError(''); setLoading(true);
    try {
      const identifier = form.mobileNumber.trim() || form.email.trim();
      const { data } = await auth.registerVerify({ identifier, code: otp.trim() });
      login(data); navigate('/');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  const handleResend = async () => {
    if (resendTimer > 0) return;
    try {
      await auth.registerSendOtp({
        displayName:  form.displayName,
        mobileNumber: form.mobileNumber || undefined,
        email:        form.email        || undefined,
      });
      startCountdown(60); toast.success('Code resent!');
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
      <div className="absolute top-[-10%] left-[-5%] w-[500px] h-[500px] rounded-full
                      pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(56,189,248,0.22) 0%, transparent 70%)' }}/>
      <div className="absolute bottom-[-10%] right-[-5%] w-[450px] h-[450px] rounded-full
                      pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(3,105,161,0.32) 0%, transparent 70%)' }}/>

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
               className="items-center justify-center w-14 h-14 rounded-xl flex-shrink-0">
            <span className="text-3xl">📂</span>
          </div>
          <div>
            <p className="text-white font-extrabold text-base leading-tight">Magizhchi Box</p>
            <p className="text-white/50 text-xs mt-0.5">Secure Cloud Storage, Simplified</p>
          </div>
        </div>

        {/* ─ Step progress ─ */}
        <div className="flex items-center gap-2 px-2">
          {[
            { n: 1, label: 'Your details' },
            { n: 2, label: 'Verify'       },
          ].map((s, i) => (
            <div key={s.n} className="flex items-center gap-2 flex-1">
              <div className="flex items-center gap-1.5 flex-1">
                <div className="w-6 h-6 rounded-full flex items-center justify-center
                                text-[11px] font-bold flex-shrink-0 transition-all"
                     style={step >= s.n ? {
                       background: 'rgba(255,255,255,0.92)',
                       color: '#0369a1',
                     } : {
                       background: 'rgba(255,255,255,0.15)',
                       color: 'rgba(255,255,255,0.4)',
                       border: '1px solid rgba(255,255,255,0.22)',
                     }}>
                  {step > s.n ? '✓' : s.n}
                </div>
                <span className="text-[11px] font-semibold transition-colors"
                      style={{ color: step >= s.n ? 'rgba(255,255,255,0.85)' : 'rgba(255,255,255,0.35)' }}>
                  {s.label}
                </span>
              </div>
              {i === 0 && (
                <div className="h-px flex-1 mx-1 rounded-full"
                     style={{ background: step > 1 ? 'rgba(255,255,255,0.45)' : 'rgba(255,255,255,0.18)' }}/>
              )}
            </div>
          ))}
        </div>

        {/* ─ Form card ─ */}
        <div className="rounded-2xl px-8 py-8" style={GLASS}>

          <div className="mb-6">
            <h2 className="text-white font-extrabold text-xl leading-tight">
              {step === 1 ? 'Create your account' : 'Verify your identity'}
            </h2>
            <p className="text-white/50 text-xs mt-1.5 leading-relaxed">
              {step === 1
                ? 'Fill in your details to get started — it\'s free'
                : `Enter the 6-digit code sent to ${form.mobileNumber || form.email}`}
            </p>
          </div>

          <div className="h-px mb-6" style={{ background: 'rgba(255,255,255,0.15)' }}/>

          {step === 1 ? (
            <form onSubmit={handleStep1} className="space-y-4">
              <GlassInput
                label="Full name"
                value={form.displayName}
                onChange={e => set('displayName', e.target.value)}
                placeholder="Your full name"
                autoFocus/>

              <GlassInput
                label="Mobile number"
                hint="required"
                value={form.mobileNumber}
                onChange={e => set('mobileNumber', e.target.value)}
                placeholder="+91 9876543210"
                type="tel"/>

              <GlassInput
                label="Email address"
                hint="optional"
                value={form.email}
                onChange={e => set('email', e.target.value)}
                placeholder="you@example.com"
                type="email"/>

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
            <form onSubmit={handleStep2} className="space-y-5">
              <GlassInput
                label="6-digit verification code"
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
                {loading ? '⏳ Creating account…' : 'Create Account  →'}
              </button>

              <div className="flex items-center justify-between">
                <button type="button" onClick={() => { setStep(1); setOtp(''); setError(''); }}
                        className="text-xs text-white/50 hover:text-white transition-colors">
                  ← Back
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
            { icon: '🤝', text: 'Connect with teams'    },
            { icon: '📁', text: 'Instant file sharing'  },
            { icon: '🛡',  text: 'Privacy first'         },
            { icon: '🔔', text: 'Real-time alerts'      },
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
          Already have an account?{' '}
          <Link to="/login" className="text-white font-bold hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
