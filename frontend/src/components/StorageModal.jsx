import { useState, useEffect } from 'react';
import { storage } from '../services/api';

function fmt(bytes) {
  if (!bytes) return '0 B';
  if (bytes < 1024)          return `${bytes} B`;
  if (bytes < 1024 ** 2)     return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3)     return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return                            `${(bytes / 1024 ** 3).toFixed(2)} GB`;
}

function barColor(pct) {
  if (pct >= 90) return { bar: '#ef4444', text: 'text-red-500',   bg: 'bg-red-500' };
  if (pct >= 70) return { bar: '#f59e0b', text: 'text-amber-500', bg: 'bg-amber-500' };
  return               { bar: '#22c55e', text: 'text-green-500', bg: 'bg-green-500' };
}

function ProgressBar({ pct, colorBg }) {
  return (
    <div className="w-full h-2.5 bg-white/20 rounded-full overflow-hidden">
      <div className={`h-full rounded-full transition-all duration-700 ${colorBg}`}
           style={{ width: `${Math.min(100, pct)}%` }}/>
    </div>
  );
}

const TABS = ['Overview', 'Groups', 'Top Files'];

export default function StorageModal({ onClose }) {
  const [data,       setData]       = useState(null);
  const [loading,    setLoading]    = useState(true);
  const [activeTab,  setActiveTab]  = useState('Overview');

  useEffect(() => {
    storage.usage()
      .then(r => setData(r.data))
      .catch(console.error)
      .finally(() => setLoading(false));
  }, []);

  const colors = data ? barColor(data.usedPercent) : barColor(0);
  const limitGB = data ? (data.limitBytes / 1024 ** 3).toFixed(0) : 5;

  return (
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
         onClick={onClose}>
      <div className="w-full max-w-md rounded-3xl overflow-hidden shadow-2xl flex flex-col"
           style={{
             background: 'linear-gradient(135deg, #0369a1 0%, #0284c7 40%, #0ea5e9 100%)',
             maxHeight: '88vh',
           }}
           onClick={e => e.stopPropagation()}>

        {/* Dot texture */}
        <div className="absolute inset-0 pointer-events-none rounded-3xl"
             style={{
               backgroundImage: 'radial-gradient(circle, rgba(255,255,255,0.15) 1.5px, transparent 1.5px)',
               backgroundSize: '20px 20px',
             }}/>

        <div className="relative z-10 flex flex-col flex-1 min-h-0">
          {/* Header */}
          <div className="flex items-center justify-between px-6 pt-5 pb-4">
            <div>
              <h2 className="text-lg font-extrabold text-white">Storage Usage</h2>
              <p className="text-xs text-white/60 mt-0.5">Free plan · {limitGB} GB total</p>
            </div>
            <button onClick={onClose}
                    className="w-8 h-8 rounded-full flex items-center justify-center
                               text-white/70 hover:text-white hover:bg-white/10 transition-all text-lg">
              ✕
            </button>
          </div>

          {/* Main storage bar */}
          {loading ? (
            <div className="px-6 pb-6 text-center text-white/60 text-sm">Loading…</div>
          ) : data && (
            <div className="px-6 pb-2 flex flex-col flex-1 min-h-0 overflow-hidden">
              {/* ── Fixed: main storage bar ── */}
              <div className="flex-shrink-0">
              <div className="rounded-2xl p-4 mb-4"
                   style={{ background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.2)' }}>
                <div className="flex justify-between items-end mb-2">
                  <span className="text-2xl font-extrabold text-white">{fmt(data.usedBytes)}</span>
                  <span className="text-sm text-white/60">of {fmt(data.limitBytes)}</span>
                </div>
                <ProgressBar pct={data.usedPercent} colorBg={colors.bg}/>
                <div className="flex justify-between mt-1.5">
                  <span className={`text-xs font-bold ${colors.text}`}>
                    {data.usedPercent.toFixed(1)}% used
                  </span>
                  <span className="text-xs text-white/50">
                    {fmt(data.limitBytes - data.usedBytes)} free
                  </span>
                </div>
              </div>

              {/* Warning / upgrade CTA */}
              {data.usedPercent >= 90 && (
                <div className="rounded-xl px-4 py-3 mb-3 text-sm font-semibold text-red-100"
                     style={{ background: 'rgba(239,68,68,0.25)', border: '1px solid rgba(239,68,68,0.4)' }}>
                  🚨 Storage almost full! Upgrade your plan to continue uploading.
                </div>
              )}
              {data.usedPercent >= 70 && data.usedPercent < 90 && (
                <div className="rounded-xl px-4 py-3 mb-3 text-sm font-semibold text-amber-100"
                     style={{ background: 'rgba(245,158,11,0.2)', border: '1px solid rgba(245,158,11,0.35)' }}>
                  ⚠️ You're using {data.usedPercent.toFixed(0)}% of your storage.
                </div>
              )}

              {/* Tabs */}
              <div className="flex gap-1 mb-3">
                {TABS.map(tab => (
                  <button key={tab} onClick={() => setActiveTab(tab)}
                          className={`flex-1 py-1.5 rounded-xl text-xs font-bold transition-all
                                      ${activeTab === tab
                                        ? 'bg-white text-sky-700 shadow'
                                        : 'text-white/70 hover:text-white hover:bg-white/10'}`}>
                    {tab}
                  </button>
                ))}
              </div>
              </div>{/* end flex-shrink-0 fixed section */}

              {/* ── Scrollable tab content ── */}
              <div className="flex-1 min-h-0 overflow-y-auto">

              {/* Tab: Overview */}
              {activeTab === 'Overview' && (
                <div className="space-y-2 pb-2">
                  {[
                    { label: '🗄️ Personal', bytes: data.personalBytes },
                    { label: '👤 Direct Chats', bytes: data.directBytes },
                    { label: '👥 Groups',     bytes: data.groupBytes },
                  ].map(({ label, bytes }) => {
                    const pct = data.limitBytes > 0 ? (bytes * 100 / data.limitBytes) : 0;
                    return (
                      <div key={label} className="rounded-xl px-4 py-3"
                           style={{ background: 'rgba(255,255,255,0.10)', border: '1px solid rgba(255,255,255,0.15)' }}>
                        <div className="flex justify-between items-center mb-1.5">
                          <span className="text-sm font-semibold text-white">{label}</span>
                          <span className="text-xs text-white/60">{fmt(bytes)}</span>
                        </div>
                        <ProgressBar pct={pct} colorBg="bg-sky-300"/>
                      </div>
                    );
                  })}
                  <p className="text-xs text-white/40 text-center pt-1">
                    Includes all uploads regardless of where they were shared
                  </p>
                </div>
              )}

              {/* Tab: Groups */}
              {activeTab === 'Groups' && (
                <div className="space-y-2 pb-2">
                  {data.groupBreakdown.length === 0 ? (
                    <p className="text-sm text-white/50 text-center py-4">No group uploads yet</p>
                  ) : data.groupBreakdown.map(g => {
                    const pct = data.groupBytes > 0 ? (g.usedBytes * 100 / data.groupBytes) : 0;
                    return (
                      <div key={g.conversationId} className="rounded-xl px-4 py-3"
                           style={{ background: 'rgba(255,255,255,0.10)', border: '1px solid rgba(255,255,255,0.15)' }}>
                        <div className="flex justify-between items-center mb-1.5">
                          <span className="text-sm font-semibold text-white truncate max-w-[160px]">
                            👥 {g.name}
                          </span>
                          <span className="text-xs text-white/60">{fmt(g.usedBytes)}</span>
                        </div>
                        <ProgressBar pct={pct} colorBg="bg-indigo-300"/>
                      </div>
                    );
                  })}
                </div>
              )}

              {/* Tab: Top Files */}
              {activeTab === 'Top Files' && (
                <div className="space-y-1.5 pb-2">
                  {data.topFiles.length === 0 ? (
                    <p className="text-sm text-white/50 text-center py-4">No files uploaded yet</p>
                  ) : data.topFiles.map((f, i) => (
                    <div key={f.id} className="flex items-center gap-3 rounded-xl px-3 py-2.5"
                         style={{ background: 'rgba(255,255,255,0.09)', border: '1px solid rgba(255,255,255,0.12)' }}>
                      <span className="text-white/40 text-xs font-bold w-4 flex-shrink-0">
                        {i + 1}
                      </span>
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold text-white truncate">{f.fileName}</p>
                        <p className="text-xs text-white/45">{f.category}</p>
                      </div>
                      <span className="text-xs font-bold text-white/70 flex-shrink-0">
                        {fmt(f.sizeBytes)}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              </div>{/* end scrollable tab content */}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
