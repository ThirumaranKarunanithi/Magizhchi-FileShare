import { format } from 'date-fns';

/**
 * Windows-Explorer-style Properties dialog for files and folders.
 *
 * Props:
 *   target.type === 'file'   → target.msg is a FileMessage-shaped object.
 *                              When the file is a received share, `msg._isReceivedShare`
 *                              is true and `msg._shareData` holds the SharedResource.
 *   target.type === 'folder' → target.folderPath, target.info, target.totalItems,
 *                              target.totalSize, target.pinned.
 *
 * onClose closes the modal.
 */

function fmtBytes(b) {
  if (b == null) return '—';
  if (b < 1024)        return `${b} B`;
  if (b < 1024 ** 2)   return `${(b / 1024).toFixed(1)} KB`;
  if (b < 1024 ** 3)   return `${(b / 1024 ** 2).toFixed(1)} MB`;
  return                     `${(b / 1024 ** 3).toFixed(2)} GB`;
}

function fmtDate(d) {
  if (!d) return '—';
  try { return format(new Date(d), 'd MMM yyyy, h:mm:ss a'); }
  catch { return String(d); }
}

function permissionLabel(p) {
  switch (p) {
    case 'VIEW_ONLY':            return '👁 View only · downloads disabled';
    case 'ADMIN_ONLY_DOWNLOAD':  return '🛡 Admin only · only group admins can download';
    case 'CAN_DOWNLOAD':
    default:                     return '⬇ Anyone with access can download';
  }
}

function extOf(name) {
  if (!name) return '—';
  const i = name.lastIndexOf('.');
  if (i <= 0 || i === name.length - 1) return '—';
  return name.slice(i + 1).toLowerCase();
}

function categoryLabel(c) {
  return ({
    IMAGE:    '🖼 Image',
    VIDEO:    '🎬 Video',
    DOCUMENT: '📄 Document',
    AUDIO:    '🎵 Audio',
    ARCHIVE:  '🗜 Archive',
    OTHER:    '📎 Other',
  }[c] || '—');
}

function Row({ label, children }) {
  return (
    <div className="grid grid-cols-[140px_1fr] gap-3 py-1.5 items-start">
      <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wider pt-0.5">
        {label}
      </div>
      <div className="text-sm text-slate-800 break-words">
        {children ?? <span className="text-slate-400 italic">—</span>}
      </div>
    </div>
  );
}

function FileBody({ msg }) {
  const isReceived = !!msg._isReceivedShare;
  const share      = msg._shareData;
  return (
    <>
      <Row label="Name">
        <span className="font-semibold">{msg.originalFileName}</span>
      </Row>
      <Row label="Extension">
        <span className="font-mono uppercase">{extOf(msg.originalFileName)}</span>
      </Row>
      <Row label="Type">{categoryLabel(msg.category)}</Row>
      <Row label="Status">
        {isReceived
          ? <span className="text-violet-700 font-semibold">📥 Shared with you</span>
          : <span className="text-sky-700 font-semibold">⬆ Uploaded</span>}
      </Row>
      <Row label="Size">{fmtBytes(msg.fileSizeBytes)}</Row>
      <Row label="Description">
        {msg.caption?.trim() || <span className="italic text-slate-400">No description</span>}
      </Row>
      <Row label="Permission">
        {permissionLabel(msg.downloadPermission)}
        {isReceived && share?.permission && (
          <div className="text-xs text-slate-500 mt-0.5">
            Your share access: {share.permission === 'EDITOR' ? '✏️ Edit' : '👁 View'}
          </div>
        )}
      </Row>
      <Row label={isReceived ? 'Shared by' : 'Uploaded by'}>
        {msg.senderName ?? '—'}
      </Row>
      <Row label={isReceived ? 'Shared on' : 'Uploaded on'}>
        {fmtDate(msg.sentAt)}
      </Row>
      {msg.folderPath && (
        <Row label="Folder">
          <span className="font-mono text-xs">{msg.folderPath}</span>
        </Row>
      )}
      {msg.isPinned && (
        <Row label="Pinned">
          <span className="text-amber-600 font-semibold">📌 Yes</span>
        </Row>
      )}
    </>
  );
}

function FolderBody({ folderPath, info, totalItems, totalSize, pinned }) {
  const folderName = folderPath.replace(/\/$/, '').split('/').pop();
  return (
    <>
      <Row label="Name">
        <span className="font-semibold">{folderName}</span>
      </Row>
      <Row label="Path">
        <span className="font-mono text-xs">{folderPath}</span>
      </Row>
      <Row label="Status">
        <span className="text-amber-700 font-semibold">📁 Folder</span>
      </Row>
      <Row label="Description">
        {info?.description?.trim() || <span className="italic text-slate-400">No description</span>}
      </Row>
      <Row label="Items">
        {totalItems} file{totalItems !== 1 ? 's' : ''}
      </Row>
      <Row label="Total size">{fmtBytes(totalSize)}</Row>
      <Row label="Default permission">
        {permissionLabel(info?.defaultPermission)}
      </Row>
      <Row label="Created by">{info?.createdByName ?? '—'}</Row>
      <Row label="Created on">{fmtDate(info?.createdAt)}</Row>
      {pinned && (
        <Row label="Pinned">
          <span className="text-amber-600 font-semibold">📌 Yes</span>
        </Row>
      )}
    </>
  );
}

export default function PropertiesModal({ target, onClose }) {
  if (!target) return null;
  const title  = target.type === 'folder' ? 'Folder properties' : 'File properties';
  const icon   = target.type === 'folder' ? '📁' : '📄';

  return (
    <div className="fixed inset-0 z-[300] flex items-center justify-center"
         style={{ background: 'rgba(0,0,0,0.55)' }}
         onClick={onClose}>
      <div className="w-full max-w-md mx-4 rounded-2xl overflow-hidden"
           onClick={e => e.stopPropagation()}
           style={{
             background: 'linear-gradient(135deg, #ffffff 0%, #f0f9ff 100%)',
             border: '1px solid rgba(255,255,255,0.8)',
             boxShadow: '0 24px 56px rgba(0,0,0,0.35)',
           }}>

        {/* Header */}
        <div className="flex items-center gap-3 px-5 py-4 border-b border-slate-100">
          <div className="w-11 h-11 rounded-xl flex-shrink-0 flex items-center justify-center
                          text-2xl bg-sky-50 border border-sky-100">
            {icon}
          </div>
          <div className="flex-1 min-w-0">
            <h3 className="text-gray-900 font-bold text-base leading-tight">{title}</h3>
            <p className="text-gray-500 text-xs mt-0.5">
              All metadata for this {target.type}
            </p>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg text-slate-500 hover:bg-slate-100 transition-colors text-lg leading-none"
            title="Close">
            ×
          </button>
        </div>

        {/* Body — divider rows */}
        <div className="px-5 py-3 max-h-[60vh] overflow-y-auto divide-y divide-slate-100">
          {target.type === 'file'
            ? <FileBody msg={target.msg}/>
            : <FolderBody
                folderPath={target.folderPath}
                info={target.info}
                totalItems={target.totalItems}
                totalSize={target.totalSize}
                pinned={target.pinned}/>}
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-slate-100 flex justify-end">
          <button
            onClick={onClose}
            style={{
              padding: '8px 18px', borderRadius: '12px',
              fontSize: '0.85rem', fontWeight: 700, color: 'white',
              background: '#0F172A', border: 'none', cursor: 'pointer',
              boxShadow: '0 4px 16px rgba(15,23,42,0.25)',
            }}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}
