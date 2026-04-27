import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

// ── Shared design tokens (matches Login.jsx) ──────────────────────────────────
const BG   = 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)';
const DOTS = 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)';
const CARD = {
  background:           'rgba(255,255,255,0.12)',
  backdropFilter:       'blur(28px)',
  WebkitBackdropFilter: 'blur(28px)',
  border:               '1px solid rgba(255,255,255,0.3)',
  boxShadow:            '0 8px 48px rgba(0,0,0,0.2), inset 0 1px 0 rgba(255,255,255,0.38)',
};

function GlassInput({ className = '', ...props }) {
  const [focused, setFocused] = useState(false);
  return (
    <input
      {...props}
      className={`w-full px-4 py-2.5 rounded-xl text-sm text-white outline-none
                  transition-all placeholder-white/30 ${className}`}
      style={{
        background: 'rgba(255,255,255,0.12)',
        border:     focused ? '1px solid rgba(255,255,255,0.65)' : '1px solid rgba(255,255,255,0.25)',
      }}
      onFocus={e => { setFocused(true); props.onFocus?.(e); }}
      onBlur={e  => { setFocused(false); props.onBlur?.(e); }}
    />
  );
}

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
    if (!form.displayName.trim())   { setError('Name is required.'); return; }
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
      setStep(2);
      startCountdown(60);
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
      login(data);
      navigate('/');
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
      startCountdown(60);
      toast.success('Code resent!');
    } catch (msg) { toast.error(msg); }
  };

  const startCountdown = secs => {
    setResendTimer(secs);
    const t = setInterval(() => {
      setResendTimer(v => { if (v <= 1) { clearInterval(t); return 0; } return v - 1; });
    }, 1000);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden"
         style={{ background: BG }}>

      {/* Blue dot texture */}
      <div className="absolute inset-0 pointer-events-none"
           style={{ backgroundImage: DOTS, backgroundSize: '22px 22px' }}/>

      {/* Soft glow blobs */}
      <div className="absolute top-1/3 right-1/4 w-96 h-96 rounded-full
                      bg-sky-300/20 blur-3xl pointer-events-none"/>
      <div className="absolute bottom-1/4 left-1/4 w-80 h-80 rounded-full
                      bg-blue-200/15 blur-3xl pointer-events-none"/>

      {/* Content */}
      <div className="relative z-10 w-full max-w-sm">

        {/* Logo */}
        <div className="text-center mb-7">
          <img src="/logo.png" alt="Magizhchi Box"
               className="h-24 mx-auto mb-1 drop-shadow-2xl select-none"
               style={{ filter: 'drop-shadow(0 4px 24px rgba(0,0,0,0.25))' }}
               onError={e => {
                 e.currentTarget.style.display = 'none';
                 document.getElementById('logo-fallback-reg').style.display = 'flex';
               }}/>
          <div id="logo-fallback-reg"
               className="hidden h-20 w-20 mx-auto mb-2 rounded-2xl bg-white/20
                          items-center justify-center text-4xl">
            📂
          </div>
          <p className="text-white/65 text-sm tracking-wide">Create your free account</p>
        </div>

        {/* Progress dots */}
        <div className="flex justify-center gap-2 mb-5">
          {[1, 2].map(s => (
            <div key={s}
                 className="h-1.5 rounded-full transition-all duration-300"
                 style={{
                   width: step === s ? '28px' : '8px',
                   background: step === s ? 'rgba(255,255,255,0.9)' : 'rgba(255,255,255,0.3)',
                 }}/>
          ))}
        </div>

        {/* Glass card */}
        <div className="rounded-3xl px-7 py-8" style={CARD}>

          {/* Card header */}
          <div className="mb-6">
            <h2 className="text-xl font-extrabold text-white leading-tight">
              {step === 1 ? 'Create Account' : 'Verify your identity'}
            </h2>
            <p className="text-white/50 text-xs mt-1">
              {step === 1
                ? 'Step 1 of 2 — Your details'
                : `Step 2 of 2 — Code sent to ${form.mobileNumber || form.email}`}
            </p>
          </div>

          <div className="h-px mb-6" style={{ background: 'rgba(255,255,255,0.15)' }}/>

          {step === 1 ? (
            <form onSubmit={handleStep1} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-white/60 mb-1.5 uppercase tracking-wide">
                  Full name
                </label>
                <GlassInput
                  value={form.displayName}
                  onChange={e => set('displayName', e.target.value)}
                  placeholder="Your name"
                  autoFocus/>
              </div>

              <div>
                <label className="block text-xs font-semibold text-white/60 mb-1.5 uppercase tracking-wide">
                  Mobile number
                  <span className="normal-case ml-1 text-white/35">(required)</span>
                </label>
                <GlassInput
                  value={form.mobileNumber}
                  onChange={e => set('mobileNumber', e.target.value)}
                  placeholder="+91 9876543210"
                  type="tel"/>
              </div>

              <div>
                <label className="block text-xs font-semibold text-white/60 mb-1.5 uppercase tracking-wide">
                  Email
                  <span className="normal-case ml-1 text-white/35">(optional)</span>
                </label>
                <GlassInput
                  value={form.email}
                  onChange={e => set('email', e.target.value)}
                  placeholder="you@example.com"
                  type="email"/>
              </div>

              {error && (
                <p className="p-3 rounded-xl text-sm font-medium text-red-100"
                   style={{ background: 'rgba(239,68,68,0.22)', border: '1px solid rgba(239,68,68,0.4)' }}>
                  {error}
                </p>
              )}

              <button
                className="w-full py-3 rounded-xl font-bold text-sky-800 text-sm
                           transition-all hover:scale-[1.02] active:scale-[0.98]
                           disabled:opacity-55 disabled:cursor-not-allowed mt-2"
                style={{ background: 'rgba(255,255,255,0.93)', boxShadow: '0 4px 20px rgba(0,0,0,0.15)' }}
                disabled={loading}>
                {loading ? '⏳ Sending code…' : 'Send Verification Code →'}
              </button>
            </form>

          ) : (
            <form onSubmit={handleStep2} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-white/60 mb-1.5 uppercase tracking-wide">
                  6-digit code
                </label>
                <GlassInput
                  value={otp}
                  onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="0  0  0  0  0  0"
                  className="text-center text-2xl tracking-[0.4em] font-bold"
                  maxLength={6}
                  autoFocus/>
              </div>

              {error && (
                <p className="p-3 rounded-xl text-sm font-medium text-red-100"
                   style={{ background: 'rgba(239,68,68,0.22)', border: '1px solid rgba(239,68,68,0.4)' }}>
                  {error}
                </p>
              )}

              <button
                className="w-full py-3 rounded-xl font-bold text-sky-800 text-sm
                           transition-all hover:scale-[1.02] active:scale-[0.98]
                           disabled:opacity-55 disabled:cursor-not-allowed"
                style={{ background: 'rgba(255,255,255,0.93)', boxShadow: '0 4px 20px rgba(0,0,0,0.15)' }}
                disabled={loading}>
                {loading ? '⏳ Creating account…' : 'Verify & Create Account →'}
              </button>

              <div className="flex items-center justify-between pt-1">
                <button type="button"
                        className="text-xs text-white/60 hover:text-white transition-colors"
                        onClick={() => { setStep(1); setOtp(''); setError(''); }}>
                  ← Back
                </button>
                <button type="button"
                        className={`text-xs transition-colors
                                    ${resendTimer > 0
                                      ? 'text-white/30 cursor-not-allowed'
                                      : 'text-white/60 hover:text-white'}`}
                        onClick={handleResend} disabled={resendTimer > 0}>
                  {resendTimer > 0 ? `Resend in ${resendTimer}s` : 'Resend code'}
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Footer link */}
        <p className="text-center text-white/60 text-sm mt-5">
          Already have an account?{' '}
          <Link to="/login" className="text-white font-bold hover:underline">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
