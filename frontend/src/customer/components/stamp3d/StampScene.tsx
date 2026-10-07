import { Star } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';

/**
 * 3D damğa animasiyası. Three.js yalnız burada dinamik yüklənir; WebGL dəstəklənmirsə (köhnə cihaz, test mühiti)
 * sadə möhür izi göstərilir. {@code onImpact} — damğa masaya dəydiyi an (konfeti və yazı onunla başlayır).
 */
export function StampScene({ onImpact }: { onImpact: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const impactRef = useRef(onImpact);
  const [fallback, setFallback] = useState(false);

  useEffect(() => {
    impactRef.current = onImpact;
  }, [onImpact]);

  useEffect(() => {
    let dispose: (() => void) | undefined;
    let cancelled = false;
    const reducedMotion = Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    const giveUp = () => {
      if (!cancelled) {
        setFallback(true);
        impactRef.current();
      }
    };
    import('./scene')
      .then(({ startStampScene }) => {
        if (cancelled || !canvasRef.current) {
          return;
        }
        try {
          dispose = startStampScene(canvasRef.current, { reducedMotion, onImpact: () => impactRef.current() });
        } catch {
          giveUp();
        }
      })
      .catch(giveUp);
    return () => {
      cancelled = true;
      dispose?.();
    };
  }, []);

  if (fallback) {
    return (
      <span
        className="grid size-36 place-items-center rounded-full border-[6px] border-primary text-primary"
        aria-hidden="true"
      >
        <Star className="size-14" fill="currentColor" />
      </span>
    );
  }
  return <canvas ref={canvasRef} className="h-64 w-72 max-w-full" aria-hidden="true" />;
}
