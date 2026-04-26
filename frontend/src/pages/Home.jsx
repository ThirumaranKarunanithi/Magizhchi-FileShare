import { useState, useEffect } from 'react';
import Sidebar    from '../components/Sidebar';
import ChatWindow from '../components/ChatWindow';
import { connectSocket, disconnectSocket } from '../services/socket';

export default function Home() {
  const [selectedConv, setSelectedConv] = useState(null);

  // Connect WebSocket when app opens, disconnect on unmount
  useEffect(() => {
    connectSocket(
      () => console.log('[WS] Connected to Magizhchi Share'),
      () => console.log('[WS] Disconnected')
    );
    return () => disconnectSocket();
  }, []);

  return (
    <div className="flex h-screen overflow-hidden bg-slate-100">
      {/* Left sidebar: conversation list */}
      <Sidebar selected={selectedConv} onSelect={setSelectedConv}/>

      {/* Right: chat window or empty state */}
      {selectedConv ? (
        <ChatWindow key={selectedConv.id} conversation={selectedConv}/>
      ) : (
        <EmptyState/>
      )}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-5
                    bg-gradient-to-br from-sky-400 via-sky-500 to-sky-600">
      <div className="w-28 h-28 rounded-3xl bg-white/20 flex items-center justify-center shadow-2xl">
        <span className="text-6xl">📂</span>
      </div>
      <div className="text-center max-w-xs">
        <h2 className="text-2xl font-bold text-white">Magizhchi Share</h2>
        <p className="text-white/70 text-sm mt-2">
          Select a user or group from the sidebar to view and share files.
        </p>
      </div>
      <div className="flex flex-wrap justify-center gap-2 mt-1">
        {['🖼 Images', '🎬 Videos', '📄 Documents', '🗜 Archives', '🎵 Audio'].map(f => (
          <span key={f} className="bg-white/20 text-white text-xs font-semibold px-3 py-1.5 rounded-full">
            {f}
          </span>
        ))}
      </div>
    </div>
  );
}
