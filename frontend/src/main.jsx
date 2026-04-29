import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider } from './context/AuthContext';
import MobileGate from './components/MobileGate';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    {/* MobileGate wraps the whole app: on phone-sized viewports it shows a
        "use desktop or the mobile app" interstitial INSTEAD of mounting the
        AuthProvider + Routes. Mounting it outside AuthProvider avoids firing
        a /api/users/me request for visitors who'll never see the app. */}
    <MobileGate>
      <BrowserRouter>
        <AuthProvider>
          <App />
          <Toaster
            position="top-right"
            toastOptions={{
              duration: 3500,
              style: {
                borderRadius: '12px',
                background: '#fff',
                color: '#1e293b',
                fontSize: '14px',
                fontWeight: 500,
                boxShadow: '0 4px 24px rgba(0,0,0,0.10)',
                border: '1px solid #e2e8f0',
              },
              success: {
                iconTheme: { primary: '#0ea5e9', secondary: '#fff' },
              },
              error: {
                iconTheme: { primary: '#ef4444', secondary: '#fff' },
              },
            }}
          />
        </AuthProvider>
      </BrowserRouter>
    </MobileGate>
  </React.StrictMode>
);
