import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

// ── Shared design tokens ──────────────────────────────────────────────────────
const BG    = 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)';
const DOTS  = 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)';
const CARD  = {
  background:           'rgba(255,255,255,0.12)',
  backdropFilter:       'blur(28px)',
  WebkitBackdropFilter: 'blur(28px)',
  border:               '1px solid rgba(255,255,255,0.3)',
  boxShadow:            '0 8px 48px rgba(0,0,0,0.2), inset 0 1px 0 rgba(255,255,255,0.38)',
};
const INPUT_IDLE  = '1px solid rgba(255,255,255,0.25)';
const INPUT_FOCUS = '1px solid rgba(255,255,255,0.65)';

function GlassInput({ className = '', ...props }) {
  const [focused, setFocused] = useState(false);
  return (
    <input
      {...props}
      className={`w-full px-4 py-2.5 rounded-xl text-sm text-white outline-none
                  transition-all placeholder-white/30 ${className}`}
      style={{
        background: 'rgba(255,255,255,0.12)',
        border:     focused ? INPUT_FOCUS : INPUT_IDLE,
      }}
      onFocus={e => { setFocused(true); props.onFocus?.(e); }}
      onBlur={e  => { setFocused(false); props.onBlur?.(e); }}
    />
  );
}

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
      setStep(2);
      startCountdown(60);
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
      login(data);
      navigate('/');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  const handleResend = async () => {
    if (resendTimer > 0) return;
    try {
      await auth.loginSendOtp({ identifier });
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
      <div className="absolute top-1/4 left-1/3 w-96 h-96 rounded-full
                      bg-sky-300/20 blur-3xl pointer-events-none"/>
      <div className="absolute bottom-1/4 right-1/4 w-80 h-80 rounded-full
                      bg-blue-200/15 blur-3xl pointer-events-none"/>

      {/* Content */}
      <div className="relative z-10 w-full max-w-sm">

        {/* Logo */}
        <div className="text-center mb-8">
          <img src="/logo.png" alt="Magizhchi Box"
               className="h-28 mx-auto mb-1 drop-shadow-2xl select-none"
               style={{ filter: 'drop-shadow(0 4px 24px rgba(0,0,0,0.25))' }}
               onError={e => {
                 e.currentTarget.style.display = 'none';
                 document.getElementById('logo-fallback-login').style.display = 'flex';
               }}/>
          {/* Fallback if logo.png not found */}
          <div id="logo-fallback-login"
               className="hidden h-20 w-20 mx-auto mb-2 rounded-2xl bg-white/20
                          items-center justify-center text-4xl">
            📂
          </div>
          <p className="text-white/65 text-sm tracking-wide">Files. Shared. Instantly.</p>
        </div>

        {/* Glass card */}
        <div className="rounded-3xl px-7 py-8" style={CARD}>

          {/* Card header */}
          <div className="mb-6">
            <h2 className="text-xl font-extrabold text-white leading-tight">
              {step === 1 ? 'Welcome back' : 'Verify your identity'}
            </h2>
            <p className="text-white/50 text-xs mt-1">
              {step === 1
                ? 'Sign in with your mobile or email'
                : `Code sent to ${identifier}`}
            </p>
          </div>

          {/* Divider */}
          <div className="h-px mb-6" style={{ background: 'rgba(255,255,255,0.15)' }}/>

          {step === 1 ? (
            <form onSubmit={handleSendOtp} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-white/60 mb-1.5 uppercase tracking-wide">
                  Mobile number or email
                </label>
                <GlassInput
                  value={identifier}
                  onChange={e => setIdentifier(e.target.value)}
                  placeholder="+91 9876543210 or you@example.com"
                  autoFocus
                />
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
                {loading ? '⏳ Sending…' : 'Send Verification Code →'}
              </button>
            </form>

          ) : (
            <form onSubmit={handleVerify} className="space-y-4">
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
                  autoFocus
                />
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
                {loading ? '⏳ Verifying…' : 'Verify & Sign In →'}
              </button>

              <div className="flex items-center justify-between pt-1">
                <button type="button"
                        className="text-xs text-white/60 hover:text-white transition-colors"
                        onClick={() => { setStep(1); setOtp(''); setError(''); }}>
                  ← Change number
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
          Don't have an account?{' '}
          <Link to="/register" className="text-white font-bold hover:underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}
