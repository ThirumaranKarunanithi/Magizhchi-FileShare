import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { auth } from '../services/api';
import { useAuth } from '../context/AuthContext';
import toast from 'react-hot-toast';

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

        {/* ── LEFT: Form column ── */}
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

          {/* ── Step 1: Identifier ── */}
          {step === 1 && (
            <div className="w-full flex flex-col justify-center" style={CARD}>
              <h2 className="text-xl sm:text-2xl font-bold text-gray-900 mb-5 text-center">
                Sign in to your account
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

              <form onSubmit={handleSendOtp} className="space-y-3">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Mobile number or email
                  </label>
                  <input
                    type="text"
                    className="input-field"
                    value={identifier}
                    onChange={e => setIdentifier(e.target.value)}
                    placeholder="+91 9876543210 or you@example.com"
                    autoFocus/>
                </div>

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full mt-1 bg-[#0F172A] hover:bg-[#1E293B] text-white font-bold
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
                  ) : 'Continue →'}
                </button>
              </form>

              <p className="mt-5 text-center text-sm text-gray-500">
                Don't have an account?{' '}
                <Link to="/register" className="text-blue-600 hover:text-blue-700 font-medium">
                  Create one free
                </Link>
              </p>
            </div>
          )}

          {/* ── Step 2: OTP ── */}
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
                          d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z"/>
                  </svg>
                </div>
                <h2 className="text-xl sm:text-2xl font-bold text-gray-900">Enter verification code</h2>
                <p className="text-sm text-gray-500 mt-1">
                  A 6-digit code was sent to<br/>
                  <span className="font-semibold text-gray-700">{identifier}</span>
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

              <form onSubmit={handleVerify} className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1 text-center">
                    6-digit code
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
                      Verifying…
                    </span>
                  ) : 'Sign In →'}
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
                      disabled={loading}
                      className="text-blue-600 hover:text-blue-700 font-medium disabled:opacity-50">
                      Resend code
                    </button>
                  )}
                </p>
              </div>
            </div>
          )}
        </div>

        {/* ── RIGHT: Professional image ── */}
        <div className="hidden md:block w-full md:w-1/2 relative">
          <div
            className="absolute inset-0 rounded-[24px] overflow-hidden shadow-2xl
                       transition-transform duration-500 hover:scale-[1.02]"
            style={{ border: '2px solid rgba(255,255,255,0.4)' }}>
            <img
              src="/login_professionals.png"
              alt="Professionals Collaborating"
              className="w-full h-full object-cover"/>
            <div className="absolute inset-0 bg-gradient-to-t from-[#0284C7]/90
                            via-[#0284C7]/20 to-transparent pointer-events-none"/>
            <div className="absolute bottom-8 left-8 right-8 text-white">
              <h3 className="text-2xl sm:text-3xl font-extrabold mb-2 drop-shadow-lg">
                Secure Collaboration
              </h3>
              <p className="text-base sm:text-lg font-medium text-blue-50 drop-shadow-md leading-relaxed">
                Empowering your team with seamless and secure access to critical data from anywhere.
              </p>
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
