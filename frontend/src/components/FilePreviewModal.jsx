import { useState, useEffect, useCallback } from 'react';
import { files as filesApi } from '../services/api';
import toast from 'react-hot-toast';

/**
 * Full-screen preview modal for images, videos, PDFs, audio, and text/code files.
 *
 * Props:
 *   file     – FileMessageResponse object
 *   onClose  – callback to close the modal
 */
export default function FilePreviewModal({ file, onClose }) {
  const [url,     setUrl]     = useState(null);
  const [loading, setLoading] = useState(true);
  const [error,   setError]   = useState(null);

  // Classify file into a preview strategy
  const strategy = getPreviewStrategy(file.contentType);

  const loadUrl = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { data } = await filesApi.previewUrl(file.id);
      setUrl(data.url);
    } catch (e) {
      setError(String(e));
      toast.error('Could not load preview.');
    } finally {
      setLoading(false);
    }
  }, [file.id]);

  useEffect(() => {
    loadUrl();
  }, [loadUrl]);

  // Close on Escape
  useEffect(() => {
    const handler = e => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [onClose]);

  const handleDownload = async () => {
    try {
      const { data } = await filesApi.downloadUrl(file.id);
      const a = document.createElement('a');
      a.href = data.url; a.download = file.originalFileName; a.click();
    } catch (e) {
      if (String(e).toLowerCase().includes('forbidden') || String(e).includes('403')) {
        toast.error('This file cannot be downloaded.');
      } else {
        toast.error('Download failed: ' + e);
      }
    }
  };

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-[300] flex flex-col"
      style={{ background: 'rgba(0,0,0,0.92)' }}
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}>

      {/* ── Top bar ── */}
      <div className="flex items-center justify-between px-5 py-3 flex-shrink-0"
           style={{ background: 'rgba(255,255,255,0.07)', borderBottom: '1px solid rgba(255,255,255,0.1)' }}>

        {/* File name + meta */}
        <div className="flex items-center gap-3 min-w-0">
          <span className="text-2xl flex-shrink-0">{categoryEmoji(file.category)}</span>
          <div className="min-w-0">
            <p className="text-white font-semibold text-sm truncate">{file.originalFileName}</p>
            <p className="text-white/45 text-xs">
              {formatBytes(file.fileSizeBytes)}
              {file.senderName && ` · by ${file.senderName}`}
            </p>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-2 flex-shrink-0 ml-4">
          {/* Permission badge */}
          {file.downloadPermission && file.downloadPermission !== 'CAN_DOWNLOAD' && (
            <span className="text-[10px] font-bold px-2 py-1 rounded-full"
                  style={{
                    background: file.downloadPermission === 'VIEW_ONLY'
                      ? 'rgba(239,68,68,0.25)' : 'rgba(234,179,8,0.25)',
                    border: file.downloadPermission === 'VIEW_ONLY'
                      ? '1px solid rgba(239,68,68,0.5)' : '1px solid rgba(234,179,8,0.5)',
                    color: file.downloadPermission === 'VIEW_ONLY' ? '#fca5a5' : '#fde68a',
                  }}>
              {file.downloadPermission === 'VIEW_ONLY' ? '👁 View Only' : '🛡 Admin Download'}
            </span>
          )}

          {/* Download (disabled if VIEW_ONLY) */}
          {file.downloadPermission !== 'VIEW_ONLY' && (
            <button
              onClick={handleDownload}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold
                         text-white transition-all"
              style={{ background: 'rgba(255,255,255,0.15)', border: '1px solid rgba(255,255,255,0.25)' }}>
              ⬇ Download
            </button>
          )}

          {/* Open in new tab */}
          {url && (
            <a href={url} target="_blank" rel="noopener noreferrer"
               className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold
                          text-white transition-all"
               style={{ background: 'rgba(255,255,255,0.1)', border: '1px solid rgba(255,255,255,0.2)' }}>
              ↗ Open
            </a>
          )}

          {/* Close */}
          <button
            onClick={onClose}
            className="w-9 h-9 rounded-full flex items-center justify-center text-white/70
                       hover:text-white hover:bg-white/15 transition-all text-lg font-light">
            ✕
          </button>
        </div>
      </div>

      {/* ── Content area ── */}
      <div className="flex-1 overflow-auto flex items-center justify-center p-4">

        {loading && (
          <div className="text-center">
            <div className="w-12 h-12 border-4 border-white/20 border-t-white rounded-full
                            animate-spin mx-auto mb-4"/>
            <p className="text-white/60 text-sm">Loading preview…</p>
          </div>
        )}

        {error && !loading && (
          <div className="text-center py-12">
            <span className="text-5xl">⚠️</span>
            <p className="text-white/60 text-sm mt-4">Could not load preview.</p>
            <button onClick={loadUrl}
                    className="mt-3 text-sky-400 hover:text-sky-300 text-sm underline">
              Retry
            </button>
          </div>
        )}

        {!loading && !error && url && (
          <>
            {strategy === 'image' && (
              <img src={url} alt={file.originalFileName}
                   className="max-w-full max-h-full object-contain rounded-lg shadow-2xl"
                   style={{ maxHeight: 'calc(100vh - 120px)' }}/>
            )}

            {strategy === 'video' && (
              <video src={url} controls autoPlay={false}
                     className="max-w-full rounded-lg shadow-2xl"
                     style={{ maxHeight: 'calc(100vh - 120px)' }}/>
            )}

            {strategy === 'audio' && (
              <div className="text-center">
                <span className="text-8xl block mb-6">🎵</span>
                <p className="text-white font-semibold text-lg mb-4">{file.originalFileName}</p>
                <audio src={url} controls className="w-80"/>
              </div>
            )}

            {strategy === 'pdf' && (
              <iframe src={url}
                      title={file.originalFileName}
                      className="w-full rounded-lg shadow-2xl"
                      style={{ height: 'calc(100vh - 120px)', minWidth: '700px' }}/>
            )}

            {strategy === 'none' && (
              <div className="text-center py-12">
                <span className="text-7xl">{categoryEmoji(file.category)}</span>
                <p className="text-white font-semibold text-lg mt-4">{file.originalFileName}</p>
                <p className="text-white/50 text-sm mt-1 mb-6">{file.contentType}</p>
                {file.downloadPermission !== 'VIEW_ONLY' ? (
                  <button
                    onClick={handleDownload}
                    className="px-6 py-3 rounded-xl font-bold text-white text-sm"
                    style={{ background: 'rgba(14,165,233,0.8)', border: '1px solid rgba(56,189,248,0.5)' }}>
                    ⬇ Download to open
                  </button>
                ) : (
                  <p className="text-red-300/80 text-sm">This file is view-only and cannot be downloaded.</p>
                )}
              </div>
            )}
          </>
        )}
      </div>

      {/* Caption */}
      {file.caption && (
        <div className="px-5 py-3 flex-shrink-0 text-center"
             style={{ borderTop: '1px solid rgba(255,255,255,0.1)' }}>
          <p className="text-white/60 text-sm italic">"{file.caption}"</p>
        </div>
      )}
    </div>
  );
}

// ── helpers ───────────────────────────────────────────────────────────────────

function getPreviewStrategy(contentType) {
  if (!contentType) return 'none';
  if (contentType.startsWith('image/'))  return 'image';
  if (contentType.startsWith('video/'))  return 'video';
  if (contentType.startsWith('audio/'))  return 'audio';
  if (contentType === 'application/pdf') return 'pdf';
  return 'none';
}

function categoryEmoji(category) {
  switch (category) {
    case 'IMAGE':    return '🖼';
    case 'VIDEO':    return '🎬';
    case 'DOCUMENT': return '📄';
    case 'AUDIO':    return '🎵';
    case 'ARCHIVE':  return '🗜';
    default:         return '📎';
  }
}

function formatBytes(b) {
  if (!b) return '—';
  if (b < 1024)        return `${b} B`;
  if (b < 1024 ** 2)   return `${(b / 1024).toFixed(1)} KB`;
  if (b < 1024 ** 3)   return `${(b / 1024 ** 2).toFixed(1)} MB`;
  return                     `${(b / 1024 ** 3).toFixed(2)} GB`;
}
