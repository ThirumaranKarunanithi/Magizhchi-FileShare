import { useState, useEffect } from 'react';

function initials(name) {
  if (!name) return '?';
  const parts = name.trim().split(/\s+/);
  return parts.length >= 2
    ? (parts[0][0] + parts[1][0]).toUpperCase()
    : parts[0][0].toUpperCase();
}

/**
 * Avatar — shows a photo when available, falls back to initials on error or when absent.
 *
 * Props:
 *   name      string   — used for initials fallback
 *   photoUrl  string?  — photo src; falls back to initials if missing or fails to load
 *   size      'xs'|'sm'|'md'|'lg'|'xl'   default 'md'
 *   shape     'circle'|'rounded'          default 'circle'
 *   className string   — extra Tailwind classes applied to the wrapper
 */
export default function Avatar({ name, photoUrl, size = 'md', shape = 'circle', className = '' }) {
  const [imgError, setImgError] = useState(false);

  // Reset error state when the photoUrl changes (e.g. after a profile update)
  useEffect(() => {
    setImgError(false);
  }, [photoUrl]);

  const sizeMap = {
    xs:  'w-6  h-6  text-[10px]',
    sm:  'w-8  h-8  text-xs',
    md:  'w-10 h-10 text-sm',
    lg:  'w-11 h-11 text-sm',
    xl:  'w-14 h-14 text-base',
  };

  const shapeClass = shape === 'rounded' ? 'rounded-xl' : 'rounded-full';
  const sizeClass  = sizeMap[size] ?? sizeMap.md;
  const showPhoto  = photoUrl && !imgError;

  return (
    <div
      className={`avatar flex-shrink-0 overflow-hidden select-none
                  ${sizeClass} ${shapeClass} ${className}`}
    >
      {showPhoto ? (
        <img
          src={photoUrl}
          alt={name ?? ''}
          className={`w-full h-full object-cover ${shapeClass}`}
          onError={() => setImgError(true)}
        />
      ) : (
        initials(name)
      )}
    </div>
  );
}
