import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

export default function Register() {
  const navigate  = useNavigate();
  const { login } = useAuth();

  const [step,        setStep]        = useState(1);
  const [form,        setForm]        = useState({ displayName: '', mobileNumber: '', email: '' });
  const [otp,         setOtp]         = useState('');
  const [loading,     setLoading]     = useState(false);
  const [resending,   setResending]   = useState(false);
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
    } catch (msg) {
      const s = String(msg);
      // "Please wait N seconds" = our Redis cooldown fired, meaning an OTP WAS
      // already sent successfully before. Advance to Step 2 so the user can
      // enter the code they already have.
      const waitMatch = s.match(/please wait (\d+) second/i);
      if (waitMatch) {
        setStep(2);
        startCountdown(parseInt(waitMatch[1], 10));
        toast('A code was already sent — check your messages.', { icon: 'ℹ️', duration: 5000 });
      } else {
        // Twilio 20429, invalid number, service down, etc. — no OTP was sent,
        // stay on Step 1 and show the error.
        setError(typeof msg === 'string' ? msg : 'Something went wrong. Please try again.');
      }
    }
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
    if (resendTimer > 0 || resending) return;
    setResending(true);
    try {
      await auth.registerSendOtp({
        displayName:  form.displayName,
        mobileNumber: form.mobileNumber || undefined,
        email:        form.email        || undefined,
      });
      startCountdown(60);
      toast.success('Code resent!');
    } catch (msg) {
      toast.error(typeof msg === 'string' ? msg : 'Could not resend. Please wait a moment.');
    } finally {
      setResending(false);
    }
  };

  const startCountdown = secs => {
    setResendTimer(secs);
    const t = setInterval(() =>
      setResendTimer(v => { if (v <= 1) { clearInterval(t); return 0; } return v - 1; })
    , 1000);
  };

  const CARD = {
    background:           'linear-gradient(135deg, rgba(255,255,255,0.9) 0%, rgba(255,255,255,0.6) 100%)',
    backdropFilter:       'blur(16px)',
    WebkitBackdropFilter: 'blur(16px)',
    border:               '1px solid rgba(255,255,255,0.6)',
    borderRadius:         '24px',
    boxShadow:            '0 20px 40px rgba(0,0,0,0.1)',
    padding:              'clamp(16px, 3vw, 24px)',
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center relative overflow-hidden"
      style={{
        backgroundColor: '#0EA5E9',
        backgroundImage: 'radial-gradient(rgba(255,255,255,0.2) 2px, transparent 2px), linear-gradient(135deg, #38BDF8 0%, #0284C7 100%)',
        backgroundSize: '24px 24px, 100% 100%',
      }}
    >
      <div className="w-full max-w-6xl mx-auto flex flex-col md:flex-row relative z-10
                      items-center md:items-stretch justify-center p-4 md:p-6 gap-6 lg:gap-12">

        {/* ── LEFT: Professional image ── */}
        <div className="hidden md:block w-full md:w-1/2 relative">
          <div
            className="absolute inset-0 rounded-[24px] overflow-hidden shadow-2xl
                       transition-transform duration-500 hover:scale-[1.02]"
            style={{ border: '2px solid rgba(255,255,255,0.4)' }}>
            <img
              src="/signup_professionals.png"
              alt="Professional"
              className="w-full h-full object-cover"/>
            <div className="absolute inset-0 bg-gradient-to-t from-[#0284C7]/90
                            via-[#0284C7]/20 to-transparent pointer-events-none"/>
            <div className="absolute bottom-8 left-8 right-8 text-white">
              <h3 className="text-2xl sm:text-3xl font-extrabold mb-2 drop-shadow-lg">
                Boost Productivity
              </h3>
              <p className="text-base sm:text-lg font-medium text-blue-50 drop-shadow-md leading-relaxed">
                Join thousands of professionals securely managing their workload in the cloud.
              </p>
            </div>
          </div>
        </div>

        {/* ── RIGHT: Form column ── */}
        <div className="w-full md:w-1/2 max-w-md flex flex-col items-center justify-center">

          {/* Logo banner */}
          <div className="text-center mb-3 w-full">
            <div className="flex items-center justify-center bg-white/50 backdrop-blur-sm
                            border border-white/80 rounded-2xl shadow-sm w-full overflow-hidden h-24 sm:h-28">
              <img src="/logo.png" alt="Magizhchi Box"
                   className="w-full h-full object-contain object-center mix-blend-multiply p-3"
                   onError={e => {
                     e.currentTarget.style.display = 'none';
                     e.currentTarget.nextSibling.style.display = 'flex';
                   }}/>
              <div style={{ display: 'none' }}
                   className="w-full h-full items-center justify-center gap-3">
                <span className="text-4xl">📂</span>
                <div>
                  <p className="text-gray-800 font-extrabold text-lg">Magizhchi Box</p>
                  <p className="text-gray-500 text-xs">Secure Cloud Storage, Simplified</p>
                </div>
              </div>
            </div>
          </div>

          {/* ── Step 1: Registration form ── */}
          {step === 1 && (
            <div className="w-full flex flex-col justify-center" style={CARD}>
              <h2 className="text-xl sm:text-2xl font-bold text-gray-900 mb-3 text-center">
                Create your account
              </h2>

              {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg
                                text-red-700 text-sm flex items-start gap-2">
                  <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd"/>
                  </svg>
                  <span>{error}</span>
                </div>
              )}

              <form onSubmit={handleStep1} className="space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Full name</label>
                  <input
                    type="text"
                    className="input-field"
                    value={form.displayName}
                    onChange={e => set('displayName', e.target.value)}
                    placeholder="Your full name"
                    required
                    autoFocus/>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Mobile number <span className="text-gray-400 font-normal text-xs">(required)</span>
                  </label>
                  <input
                    type="tel"
                    className="input-field"
                    value={form.mobileNumber}
                    onChange={e => set('mobileNumber', e.target.value)}
                    placeholder="+91 9876543210"/>
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Email address <span className="text-gray-400 font-normal text-xs">(optional)</span>
                  </label>
                  <input
                    type="email"
                    className="input-field"
                    value={form.email}
                    onChange={e => set('email', e.target.value)}
                    placeholder="you@example.com"
                    autoComplete="email"/>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full mt-4 bg-[#0F172A] hover:bg-[#1E293B] text-white font-bold
                             py-3 px-4 rounded-xl shadow-[0_4px_20px_rgba(15,23,42,0.2)]
                             transition-all duration-200 hover:-translate-y-0.5
                             disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0">
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
                      </svg>
                      Sending code…
                    </span>
                  ) : 'Send OTP →'}
                </button>
              </form>

              <p className="mt-5 text-center text-sm text-gray-500">
                Already have an account?{' '}
                <Link to="/login" className="text-blue-600 hover:text-blue-700 font-medium">
                  Sign in
                </Link>
              </p>
            </div>
          )}

          {/* ── Step 2: OTP verification ── */}
          {step === 2 && (
            <div className="w-full flex flex-col justify-center" style={CARD}>
              <button
                onClick={() => { setStep(1); setOtp(''); setError(''); }}
                className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700
                           mb-4 transition-colors">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7"/>
                </svg>
                Back
              </button>

              <div className="text-center mb-6">
                <div className="inline-flex items-center justify-center w-14 h-14 rounded-full bg-blue-50 mb-3">
                  <svg className="w-7 h-7 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                          d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
                  </svg>
                </div>
                <h2 className="text-xl sm:text-2xl font-bold text-gray-900">Verify your identity</h2>
                <p className="text-sm text-gray-500 mt-1">
                  We sent a 6-digit code to<br/>
                  <span className="font-semibold text-gray-700">
                    {form.mobileNumber || form.email}
                  </span>
                </p>
              </div>

              {error && (
                <div className="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg
                                text-red-700 text-sm flex items-start gap-2">
                  <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd"/>
                  </svg>
                  <span>{error}</span>
                </div>
              )}

              <form onSubmit={handleStep2} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1 text-center">
                    Enter verification code
                  </label>
                  <input
                    type="text"
                    inputMode="numeric"
                    maxLength={6}
                    className="input-field text-center text-2xl font-bold tracking-[0.5em] py-3"
                    value={otp}
                    onChange={e => setOtp(e.target.value.replace(/\D/g,'').slice(0,6))}
                    placeholder="——————"
                    autoComplete="one-time-code"
                    autoFocus/>
                </div>

                <button
                  type="submit"
                  disabled={loading || otp.length !== 6}
                  className="w-full bg-[#0F172A] hover:bg-[#1E293B] text-white font-bold
                             py-3 px-4 rounded-xl shadow-[0_4px_20px_rgba(15,23,42,0.2)]
                             transition-all duration-200 hover:-translate-y-0.5
                             disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:translate-y-0">
                  {loading ? (
                    <span className="flex items-center justify-center gap-2">
                      <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"/>
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
                      </svg>
                      Creating account…
                    </span>
                  ) : 'Verify & Create Account →'}
                </button>
              </form>

              <div className="mt-4 text-center">
                <p className="text-sm text-gray-500">
                  Didn't receive the code?{' '}
                  {resendTimer > 0 ? (
                    <span className="text-gray-400">Resend in {resendTimer}s</span>
                  ) : (
                    <button
                      onClick={handleResend}
                      disabled={loading || resending}
                      className="text-blue-600 hover:text-blue-700 font-medium disabled:opacity-50
                                 disabled:cursor-not-allowed">
                      {resending ? 'Sending…' : 'Resend OTP'}
                    </button>
                  )}
                </p>
              </div>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
