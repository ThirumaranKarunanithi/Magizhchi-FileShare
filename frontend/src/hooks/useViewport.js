import { useEffect, useState } from 'react';

/**
 * Reactive viewport hook. Returns the current window width + a few derived
 * booleans so components don't have to repeat the same media-query logic.
 *
 *   isPhone   — narrow + UA looks like a phone (iPhone / Android phone).
 *               iPads are NOT phones; they fall through to isTablet.
 *   isTablet  — 768 ≤ width < 1024 (iPad portrait, small Android tablets).
 *   isDesktop — width ≥ 1024.
 *
 * The phone check combines width AND user-agent on purpose: a desktop user
 * who narrows their browser window past 768 px shouldn't get the "use the
 * mobile app" gate, but every actual phone should — even ones that report
 * a wider innerWidth via a desktop-mode user agent.
 */
export function useViewport() {
  const [width, setWidth] = useState(() =>
    typeof window === 'undefined' ? 1280 : window.innerWidth);

  useEffect(() => {
    const onResize = () => setWidth(window.innerWidth);
    window.addEventListener('resize', onResize);
    window.addEventListener('orientationchange', onResize);
    return () => {
      window.removeEventListener('resize', onResize);
      window.removeEventListener('orientationchange', onResize);
    };
  }, []);

  const ua = typeof navigator === 'undefined' ? '' : navigator.userAgent;
  // Match phones explicitly; iPads identify themselves as "iPad" (or, on
  // iPadOS 13+, as "Macintosh" with maxTouchPoints > 1) — neither matches
  // here, so iPads correctly fall into the tablet bucket.
  const looksLikePhone =
    /iPhone|iPod|Android.*Mobile|Windows Phone|BlackBerry|webOS/i.test(ua);

  const isPhone   = width < 768 && looksLikePhone;
  const isTablet  = !isPhone && width >= 768 && width < 1024;
  const isDesktop = width >= 1024;

  return { width, isPhone, isTablet, isDesktop };
}
