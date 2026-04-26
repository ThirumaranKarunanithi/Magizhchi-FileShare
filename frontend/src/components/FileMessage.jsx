import { useState } from 'react';
import { files } from '../services/api';
import { format } from 'date-fns';
import toast from 'react-hot-toast';

function formatBytes(b) {
  if (!b) return '';
  if (b < 1024)          return `${b} B`;
  if (b < 1024 ** 2)     return `${(b / 1024).toFixed(1)} KB`;
  if (b < 1024 ** 3)     return `${(b / 1024 ** 2).toFixed(1)} MB`;
  return                        `${(b / 1024 ** 3).toFixed(2)} GB`;
}

function categoryStyle(cat) {
  switch (cat) {
    case 'IMAGE':    return { emoji: '🖼',  bg: 'bg-purple-100', text: 'text-purple-600' };
    case 'VIDEO':    return { emoji: '🎬',  bg: 'bg-red-100',    text: 'text-red-600'    };
    case 'DOCUMENT': return { emoji: '📄',  bg: 'bg-blue-100',   text: 'text-blue-600'   };
    case 'AUDIO':    return { emoji: '🎵',  bg: 'bg-green-100',  text: 'text-green-600'  };
    case 'ARCHIVE':  return { emoji: '🗜',  bg: 'bg-yellow-100', text: 'text-yellow-600' };
    default:         return { emoji: '📎',  bg: 'bg-slate-100',  text: 'text-slate-600'  };
  }
}

export default function FileMessage({ message: msg, isMine, onDelete }) {
  const [downloading, setDownloading] = useState(false);

  if (msg.isDeleted) {
    return (
      <div className={`flex ${isMine ? 'justify-end' : 'justify-start'} mb-1`}>
        <span className="text-xs text-slate-400 italic bg-slate-100 px-3 py-1 rounded-full">
          File deleted
        </span>
      </div>
    );
  }

  const style = categoryStyle(msg.category);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      const { data } = await files.downloadUrl(msg.id);
      const a = document.createElement('a');
      a.href = data.url;
      a.download = msg.originalFileName;
      a.click();
    } catch (e) { toast.error('Download failed: ' + e); }
    finally { setDownloading(false); }
  };

  const handleDelete = () => {
    if (window.confirm(`Delete "${msg.originalFileName}"?`)) {
      onDelete(msg.id);
    }
  };

  const bubble = isMine ? 'file-bubble-sent' : 'file-bubble-received';

  return (
    <div className={`flex ${isMine ? 'justify-end' : 'justify-start'} mb-2 group`}>
      <div className="max-w-xs w-full">

        {/* Sender name (only in groups / received) */}
        {!isMine && (
          <p className="text-xs text-slate-500 ml-1 mb-0.5">{msg.senderName}</p>
        )}

        <div className={`${bubble} p-3`}>
          {/* File row */}
          <div className="flex items-center gap-3">
            {/* Category icon */}
            <div className={`w-10 h-10 rounded-xl flex-shrink-0 flex items-center justify-center
                            ${isMine ? 'bg-white/20' : style.bg}`}>
              <span className="text-xl">{style.emoji}</span>
            </div>

            {/* File info */}
            <div className="flex-1 min-w-0">
              <p className={`text-sm font-semibold truncate
                            ${isMine ? 'text-white' : 'text-slate-800'}`}>
                {msg.originalFileName}
              </p>
              <p className={`text-xs ${isMine ? 'text-white/70' : 'text-slate-400'}`}>
                {formatBytes(msg.fileSizeBytes)}
              </p>
            </div>

            {/* Download button */}
            <button onClick={handleDownload} disabled={downloading}
                    className={`p-1.5 rounded-lg flex-shrink-0 transition-all
                                ${isMine
                                  ? 'bg-white/20 hover:bg-white/30 text-white'
                                  : 'bg-sky-50 hover:bg-sky-100 text-sky-600'}`}
                    title="Download">
              {downloading ? '⏳' : '⬇'}
            </button>
          </div>

          {/* Caption */}
          {msg.caption && (
            <p className={`text-sm mt-2 ${isMine ? 'text-white/90' : 'text-slate-700'}`}>
              {msg.caption}
            </p>
          )}

          {/* Timestamp + delete */}
          <div className="flex items-center justify-between mt-2">
            <span className={`text-xs ${isMine ? 'text-white/60' : 'text-slate-400'}`}>
              {format(new Date(msg.sentAt), 'h:mm a')}
            </span>
            {isMine && (
              <button onClick={handleDelete}
                      className="text-xs opacity-0 group-hover:opacity-100
                                 text-white/70 hover:text-red-300 transition-all ml-2">
                🗑
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
