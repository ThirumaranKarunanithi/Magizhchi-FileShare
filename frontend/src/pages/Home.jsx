import { useState, useEffect } from 'react';
import Sidebar, { SHARED_WITH_ME_VIEW } from '../components/Sidebar';
import ChatWindow        from '../components/ChatWindow';
import SharedWithMeView from '../components/SharedWithMeView';
import { connectSocket, disconnectSocket } from '../services/socket';
import { useAuth } from '../context/AuthContext';

export default function Home() {
  const { currentUser } = useAuth();
  const [selectedConv, setSelectedConv] = useState(null);

  // Connect the WebSocket only AFTER the user is authenticated.
  // React runs children effects before parent effects, so by the time this
  // effect fires, Sidebar has already registered its subscriptions in
  // activeTopics — meaning onConnect will subscribe them immediately.
  useEffect(() => {
    if (!currentUser?.id) return;
    connectSocket(
      () => console.log('[WS] Connected — user', currentUser.id),
      () => console.log('[WS] Disconnected')
    );
    return disconnectSocket;
  }, [currentUser?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="flex h-screen overflow-hidden bg-slate-100">
      <Sidebar selected={selectedConv} onSelect={setSelectedConv}/>
      {selectedConv?.type === 'SHARED_WITH_ME' ? (
        <SharedWithMeView key="shared-with-me"/>
      ) : selectedConv ? (
        <ChatWindow key={selectedConv.id} conversation={selectedConv}/>
      ) : (
        <EmptyState/>
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div
      className="flex-1 flex items-center justify-center relative overflow-hidden"
      style={{
        background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)',
      }}
    >
      {/* Dot texture overlay */}
      <div
        className="absolute inset-0 pointer-events-none"
        style={{
          backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.18) 1.5px, transparent 1.5px)',
          backgroundSize: '22px 22px',
        }}
      />

      {/* Soft glow blobs */}
      <div className="absolute top-1/4 left-1/4 w-96 h-96 rounded-full
                      bg-sky-300/20 blur-3xl pointer-events-none"/>
      <div className="absolute bottom-1/4 right-1/4 w-80 h-80 rounded-full
                      bg-blue-400/20 blur-3xl pointer-events-none"/>

      {/* Glass card */}
      <div
        className="relative z-10 w-full max-w-sm mx-8 rounded-3xl p-7 flex flex-col gap-5"
        style={{
          background: 'rgba(255, 255, 255, 0.12)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          border: '1px solid rgba(255, 255, 255, 0.25)',
          boxShadow: '0 8px 48px rgba(0,0,0,0.18), inset 0 1px 0 rgba(255,255,255,0.3)',
        }}
      >
        {/* App icon */}
        <div className="flex items-center gap-4">
          <div
            className="w-16 h-16 rounded-2xl flex items-center justify-center flex-shrink-0"
            style={{
              background: 'rgba(255,255,255,0.18)',
              border: '1px solid rgba(255,255,255,0.3)',
              boxShadow: '0 4px 16px rgba(0,0,0,0.12)',
            }}
          >
            <span className="text-3xl">📂</span>
          </div>
          <div>
            <h1 className="text-xl font-extrabold text-white leading-tight tracking-tight">
              Magizhchi Share
            </h1>
            <p className="text-sky-100/80 text-xs font-medium mt-0.5">
              Secure Cloud Storage, Simplified
            </p>
          </div>
        </div>

        {/* Divider */}
        <div className="border-t border-white/15"/>

        {/* Description */}
        <div
          className="rounded-2xl p-4"
          style={{
            background: 'rgba(255,255,255,0.10)',
            border: '1px solid rgba(255,255,255,0.15)',
          }}
        >
          <p className="text-white font-semibold text-sm leading-relaxed">
            Store, access, and share your files from anywhere with complete control.
          </p>
          <p className="text-sky-100/70 text-xs mt-2 leading-relaxed">
            Built for individuals and teams who value security, simplicity, and performance.
            Select a conversation from the sidebar to start sharing files.
          </p>
        </div>

        {/* Feature pills */}
        <div className="flex flex-wrap gap-2">
          {[
            { icon: '🖼', label: 'Images' },
            { icon: '🎬', label: 'Videos' },
            { icon: '📄', label: 'Docs' },
            { icon: '🎵', label: 'Audio' },
            { icon: '🗜', label: 'Archives' },
          ].map(({ icon, label }) => (
            <span
              key={label}
              className="text-xs font-semibold text-white px-3 py-1.5 rounded-full"
              style={{
                background: 'rgba(255,255,255,0.15)',
                border: '1px solid rgba(255,255,255,0.2)',
              }}
            >
              {icon} {label}
            </span>
          ))}
        </div>

        {/* CTA */}
        <button
          className="w-full py-3 rounded-2xl text-sm font-bold text-sky-900
                     transition-all duration-200 hover:scale-[1.02] active:scale-[0.98]"
          style={{
            background: 'rgba(255,255,255,0.92)',
            boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
          }}
          onClick={() => {}}
        >
          ← Select a conversation to begin
        </button>
      </div>
    </div>
  );
}
