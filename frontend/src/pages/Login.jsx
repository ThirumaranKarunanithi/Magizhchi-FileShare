import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

export default function Login() {
  const navigate  = useNavigate();
  const { login } = useAuth();

  const [step,       setStep]       = useState(1);   // 1 = enter identifier, 2 = enter OTP
  const [identifier, setIdentifier] = useState('');
  const [otp,        setOtp]        = useState('');
  const [loading,    setLoading]    = useState(false);
  const [error,      setError]      = useState('');
  const [resendTimer, setResendTimer] = useState(0);

  // ── Step 1: send OTP ────────────────────────────────────────────────────────
  const handleSendOtp = async e => {
    e.preventDefault();
    if (!identifier.trim()) { setError('Please enter your mobile number or email.'); return; }
    setError(''); setLoading(true);
    try {
      await auth.loginSendOtp({ identifier: identifier.trim() });
      setStep(2);
      startResendCountdown(60);
      toast.success('Verification code sent!');
    } catch (msg) {
      setError(msg);
    } finally { setLoading(false); }
  };

  // ── Step 2: verify OTP ──────────────────────────────────────────────────────
  const handleVerify = async e => {
    e.preventDefault();
    if (!otp.trim()) { setError('Please enter the verification code.'); return; }
    setError(''); setLoading(true);
    try {
      const { data } = await auth.loginVerify({ identifier: identifier.trim(), code: otp.trim() });
      login(data);
      navigate('/');
    } catch (msg) {
      setError(msg);
    } finally { setLoading(false); }
  };

  const handleResend = async () => {
    if (resendTimer > 0) return;
    try {
      await auth.loginSendOtp({ identifier });
      startResendCountdown(60);
      toast.success('Code resent!');
    } catch (msg) { toast.error(msg); }
  };

  const startResendCountdown = secs => {
    setResendTimer(secs);
    const t = setInterval(() => {
      setResendTimer(v => { if (v <= 1) { clearInterval(t); return 0; } return v - 1; });
    }, 1000);
  };

  return (
    <div className="gradient-bg min-h-screen flex items-center justify-center p-4">
      <div className="w-full max-w-sm">

        {/* Logo + heading */}
        <div className="text-center mb-6">
          <div className="w-16 h-16 mx-auto mb-3 rounded-2xl bg-white/20 flex items-center justify-center">
            <span className="text-3xl">📂</span>
          </div>
          <h1 className="text-2xl font-bold text-white">Magizhchi Share</h1>
          <p className="text-white/80 text-sm mt-1">Files. Shared. Instantly.</p>
        </div>

        {/* Glass card */}
        <div className="glass-card">
          <h2 className="text-lg font-bold text-slate-800 mb-4">
            {step === 1 ? 'Sign In' : 'Enter Verification Code'}
          </h2>

          {step === 1 ? (
            <form onSubmit={handleSendOtp} className="space-y-4">
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">
                  Mobile number or email
                </label>
                <input
                  className="input"
                  value={identifier}
                  onChange={e => setIdentifier(e.target.value)}
                  placeholder="+91 9876543210 or you@example.com"
                  autoFocus
                />
              </div>
              {error && <p className="error-banner">{error}</p>}
              <button className="btn-primary" disabled={loading}>
                {loading ? 'Sending…' : 'Send Verification Code →'}
              </button>
            </form>
          ) : (
            <form onSubmit={handleVerify} className="space-y-4">
              <p className="text-sm text-slate-500">
                Code sent to <strong>{identifier}</strong>
              </p>
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">
                  6-digit code
                </label>
                <input
                  className="input text-center text-xl tracking-widest font-bold"
                  value={otp}
                  onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="000000"
                  maxLength={6}
                  autoFocus
                />
              </div>
              {error && <p className="error-banner">{error}</p>}
              <button className="btn-primary" disabled={loading}>
                {loading ? 'Verifying…' : 'Verify & Sign In →'}
              </button>
              <div className="flex items-center justify-between text-sm">
                <button type="button" className="text-sky-600 hover:underline"
                        onClick={() => { setStep(1); setOtp(''); setError(''); }}>
                  ← Change number
                </button>
                <button type="button"
                        className={`text-sky-600 hover:underline ${resendTimer > 0 ? 'opacity-40 cursor-not-allowed' : ''}`}
                        onClick={handleResend} disabled={resendTimer > 0}>
                  {resendTimer > 0 ? `Resend in ${resendTimer}s` : 'Resend code'}
                </button>
              </div>
            </form>
          )}
        </div>

        {/* Register link */}
        <p className="text-center text-white/80 text-sm mt-4">
          Don't have an account?{' '}
          <Link to="/register" className="text-white font-bold underline">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
}
