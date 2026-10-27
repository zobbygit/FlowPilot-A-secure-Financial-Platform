import { useEffect, useRef, useState } from 'react';

export const prefersReducedMotion = (): boolean => typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
export const isSmallScreen = (): boolean => typeof window !== 'undefined' && window.matchMedia('(max-width: 768px)').matches;

export function webglAvailable(): boolean {
  try {
    const c = document.createElement('canvas');
    return Boolean(window.WebGLRenderingContext && (c.getContext('webgl2') || c.getContext('webgl')));
  } catch { return false; }
}

/** True while the element is (nearly) on screen. Used to pause 3D render loops that nobody can see. */
export function useInView<T extends Element>(margin = '200px'): [React.RefObject<T | null>, boolean, boolean] {
  const ref = useRef<T | null>(null);
  const [inView, setInView] = useState(false);
  const [everSeen, setEverSeen] = useState(false);
  useEffect(() => {
    const el = ref.current;
    if (!el || typeof IntersectionObserver === 'undefined') { setInView(true); setEverSeen(true); return; }
    const io = new IntersectionObserver(([e]) => { setInView(e.isIntersecting); if (e.isIntersecting) setEverSeen(true); }, { rootMargin: margin });
    io.observe(el);
    return () => io.disconnect();
  }, [margin]);
  return [ref, inView, everSeen];
}

/** 0 → 1 as a (tall) section scrolls through the viewport. Passive listener; never prevents or alters native scrolling. */
export function useSectionProgress<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T | null>(null);
  const [p, setP] = useState(0);
  useEffect(() => {
    let raf = 0;
    const update = () => {
      raf = 0;
      const el = ref.current; if (!el) return;
      const r = el.getBoundingClientRect(), total = r.height - window.innerHeight;
      setP(total <= 0 ? 0 : Math.min(1, Math.max(0, -r.top / total)));
    };
    const onScroll = () => { if (!raf) raf = requestAnimationFrame(update); };
    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll, { passive: true });
    return () => { window.removeEventListener('scroll', onScroll); window.removeEventListener('resize', onScroll); if (raf) cancelAnimationFrame(raf); };
  }, []);
  return [ref, p];
}
