import { useEffect, useRef, useState } from 'react';

import { cn } from '@/shared/lib/cn';

/**
 * 3D damğa animasiyası. Three.js yalnız burada dinamik yüklənir. Damğa vurulub gedəndən sonra masadakı perspektivli
 * iz "qalxaraq" düz 2D möhürə çevrilir. WebGL dəstəklənmirsə (köhnə cihaz, test mühiti) birbaşa 2D möhür göstərilir.
 * {@code onImpact} — damğa masaya dəydiyi an (konfeti və yazı onunla başlayır).
 */
export function StampScene({ onImpact }: { onImpact: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const impactRef = useRef(onImpact);
  const [mode, setMode] = useState<'scene' | 'flat' | 'fallback'>('scene');

  useEffect(() => {
    impactRef.current = onImpact;
  }, [onImpact]);

  useEffect(() => {
    let dispose: (() => void) | undefined;
    let cancelled = false;
    const reducedMotion = Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
    const giveUp = () => {
      if (!cancelled) {
        setMode('fallback');
        impactRef.current();
      }
    };
    import('./scene')
      .then(({ startStampScene }) => {
        if (cancelled || !canvasRef.current) {
          return;
        }
        try {
          dispose = startStampScene(canvasRef.current, {
            reducedMotion,
            onImpact: () => impactRef.current(),
            onDone: () => {
              if (!cancelled) {
                setMode('flat');
              }
            },
          });
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

  if (mode === 'scene') {
    return <canvas ref={canvasRef} className="h-64 w-72 max-w-full" aria-hidden="true" />;
  }
  return (
    <div className="relative h-64 w-72 max-w-full [perspective:700px]" aria-hidden="true">
      <FlatImprint className={cn('absolute left-1/2 top-[60%] -ml-20 -mt-20', mode === 'flat' && 'animate-imprint-flatten')} />
    </div>
  );
}

/** Düz (2D) möhür izi — 3D səhnədəki izlə eyni: qalın halqa, qırıq daxili halqa, ulduz; şirkətin rəngində. */
function FlatImprint({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 160 160" className={cn('size-40 text-primary', className)}>
      <g transform="rotate(-14 80 80)" fill="none" stroke="currentColor">
        <circle cx="80" cy="80" r="70" strokeWidth="9" />
        <circle cx="80" cy="80" r="56" strokeWidth="3" strokeDasharray="9 6" />
        <path
          d="M80 42 L89.5 67 L116 68 L95 84.5 L102.5 110 L80 95 L57.5 110 L65 84.5 L44 68 L70.5 67 Z"
          fill="currentColor"
          stroke="none"
        />
      </g>
    </svg>
  );
}
