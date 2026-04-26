import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Login    from './pages/Login';
import Register from './pages/Register';
import Home     from './pages/Home';

function PrivateRoute({ children }) {
  const { currentUser, loading } = useAuth();
  if (loading) {
    return (
      <div className="gradient-bg min-h-screen flex items-center justify-center">
        <div className="glass-card p-8 flex flex-col items-center gap-4">
          <div className="w-12 h-12 rounded-full bg-gradient-to-br from-sky-400 to-sky-600
                          flex items-center justify-center shadow-lg animate-pulse">
            <span className="text-2xl">📂</span>
          </div>
          <p className="text-slate-500 text-sm font-medium">Loading…</p>
        </div>
      </div>
    );
  }
  return currentUser ? children : <Navigate to="/login" replace />;
}

function PublicRoute({ children }) {
  const { currentUser, loading } = useAuth();
  if (loading) {
    return (
      <div className="gradient-bg min-h-screen flex items-center justify-center">
        <div className="w-12 h-12 rounded-2xl bg-white/20 flex items-center justify-center animate-pulse">
          <span className="text-2xl">📂</span>
        </div>
      </div>
    );
  }
  return currentUser ? <Navigate to="/" replace /> : children;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login"    element={<PublicRoute><Login/></PublicRoute>}/>
      <Route path="/register" element={<PublicRoute><Register/></PublicRoute>}/>
      <Route path="/"         element={<PrivateRoute><Home/></PrivateRoute>}/>
      <Route path="*"         element={<Navigate to="/" replace/>}/>
    </Routes>
  );
}
