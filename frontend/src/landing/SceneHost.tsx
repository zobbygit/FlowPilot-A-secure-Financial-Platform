import { lazy, Suspense, useState } from 'react';
import { useTheme } from '../theme/ThemeProvider';
import { isSmallScreen, prefersReducedMotion, useInView, webglAvailable } from './hooks';
import { DARK, LIGHT } from './Scene3D';

const Scene3D = lazy(() => import('./Scene3D'));

/** Pure-CSS stand-in used when WebGL is missing, the context is lost, or the scene hasn't loaded yet. Same visual language. */
export function FallbackOrb({ className = '' }: { className?: string }) {
  return (
    <div className={`relative mx-auto aspect-square w-full max-w-md ${className}`} aria-hidden="true">
      <div className="fp-orb absolute inset-0 rounded-full" />
      {[0, 1, 2].map((i) => <div key={i} className="absolute rounded-full border border-accent/40" style={{ inset: `${14 + i * 12}%`, transform: `rotate(${i * 38}deg) scaleY(${0.55 + i * 0.12})` }} />)}
      <div className="absolute inset-[42%] rounded-full bg-live/70 blur-md" />
    </div>
  );
}

/** Lazy-mounts the 3D canvas near the viewport, pauses it off-screen, and degrades gracefully. */
export function SceneHost({ kind, progress = 0, className = '' }: { kind: 'hero' | 'universe'; progress?: number; className?: string }) {
  const { resolved } = useTheme();
  const [ref, inView, everSeen] = useInView<HTMLDivElement>('300px');
  const [lost, setLost] = useState(false);
  const ok = !lost && webglAvailable();
  return (
    <div ref={ref} className={className} role="img" aria-label={kind === 'hero' ? 'A translucent sculptural knot with a glowing core and a flowing ribbon of light' : 'Concentric rings of small spheres orbiting a luminous core, representing financial activity'}>
      {ok && everSeen ? (
        <Suspense fallback={<FallbackOrb />}>
          <Scene3D kind={kind} palette={resolved === 'dark' ? DARK : LIGHT} progress={progress} active={inView} small={isSmallScreen()} reduced={prefersReducedMotion()} onLost={() => setLost(true)} />
        </Suspense>
      ) : <FallbackOrb />}
    </div>
  );
}
