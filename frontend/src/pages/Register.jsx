import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

export default function Register() {
  const navigate  = useNavigate();
  const { login } = useAuth();

  const [step,     setStep]    = useState(1);
  const [form,     setForm]    = useState({ displayName: '', mobileNumber: '', email: '' });
  const [otp,      setOtp]     = useState('');
  const [loading,  setLoading] = useState(false);
  const [error,    setError]   = useState('');
  const [resendTimer, setResendTimer] = useState(0);

  const set = (k, v) => setForm(f => ({ ...f, [k]: v }));

  // ── Step 1 ──────────────────────────────────────────────────────────────────
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
      startResendCountdown(60);
      toast.success('Verification code sent!');
    } catch (msg) { setError(msg); }
    finally { setLoading(false); }
  };

  // ── Step 2 ──────────────────────────────────────────────────────────────────
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
          <p className="text-white/80 text-sm mt-1">Create your account</p>
        </div>

        <div className="glass-card">
          <h2 className="text-lg font-bold text-slate-800 mb-4">
            {step === 1 ? 'Create Account' : 'Verify your number'}
          </h2>

          {step === 1 ? (
            <form onSubmit={handleStep1} className="space-y-4">
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">Full name</label>
                <input className="input" value={form.displayName}
                       onChange={e => set('displayName', e.target.value)}
                       placeholder="Your name" autoFocus/>
              </div>
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">
                  Mobile number <span className="text-slate-400">(required)</span>
                </label>
                <input className="input" value={form.mobileNumber}
                       onChange={e => set('mobileNumber', e.target.value)}
                       placeholder="+91 9876543210" type="tel"/>
              </div>
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">
                  Email <span className="text-slate-400">(optional)</span>
                </label>
                <input className="input" value={form.email}
                       onChange={e => set('email', e.target.value)}
                       placeholder="you@example.com" type="email"/>
              </div>
              {error && <p className="error-banner">{error}</p>}
              <button className="btn-primary" disabled={loading}>
                {loading ? 'Sending code…' : 'Send Verification Code →'}
              </button>
            </form>
          ) : (
            <form onSubmit={handleStep2} className="space-y-4">
              <p className="text-sm text-slate-500">
                Code sent to <strong>{form.mobileNumber || form.email}</strong>
              </p>
              <div>
                <label className="block text-sm font-semibold text-slate-600 mb-1">
                  6-digit code
                </label>
                <input
                  className="input text-center text-xl tracking-widest font-bold"
                  value={otp}
                  onChange={e => setOtp(e.target.value.replace(/\D/g,'').slice(0,6))}
                  placeholder="000000" maxLength={6} autoFocus/>
              </div>
              {error && <p className="error-banner">{error}</p>}
              <button className="btn-primary" disabled={loading}>
                {loading ? 'Creating account…' : 'Verify & Create Account →'}
              </button>
              <div className="flex justify-between text-sm">
                <button type="button" className="text-sky-600 hover:underline"
                        onClick={() => { setStep(1); setOtp(''); setError(''); }}>
                  ← Back
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

        <p className="text-center text-white/80 text-sm mt-4">
          Already have an account?{' '}
          <Link to="/login" className="text-white font-bold underline">Sign in</Link>
        </p>
      </div>
    </div>
  );
}
