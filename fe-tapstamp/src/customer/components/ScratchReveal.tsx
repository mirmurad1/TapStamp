import { useCallback, useEffect, useRef, useState, type PointerEvent, type ReactNode } from 'react';

import { cn } from '@/shared/lib/cn';

/** Neçə faizi silinəndə örtük özü açılır. */
const REVEAL_THRESHOLD = 0.5;
const BRUSH_RADIUS = 20;

/**
 * "Poz-qazan" örtüyü: məzmunun üstü gümüşü təbəqə ilə bağlıdır, barmaqla (siçanla) silinən yerdən altı görünür.
 * Təxminən yarısı silinəndə örtük yumşaq şəkildə yox olur və {@code onReveal} çağırılır. Klaviatura və ekran oxuyucu
 * istifadəçiləri üçün fokusda görünən "aç" düyməsi var; açılmamış məzmun ekran oxuyucudan gizlədilir.
 */
export function ScratchReveal({
  revealed,
  onReveal,
  revealLabel,
  hiddenLabel,
  className,
  children,
}: {
  revealed: boolean;
  onReveal: () => void;
  revealLabel: string;
  hiddenLabel: string;
  className?: string;
  children: ReactNode;
}) {
  const container = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const last = useRef<{ x: number; y: number }>();
  const [fading, setFading] = useState(false);

  // Örtüyün çəkilməsi: gümüşü gradient, nazik parıltı xətləri və ortada izah
  const paint = useCallback(() => {
    const element = canvas.current;
    const box = container.current;
    if (!element || !box) {
      return;
    }
    const ratio = window.devicePixelRatio || 1;
    const { width, height } = box.getBoundingClientRect();
    element.width = Math.round(width * ratio);
    element.height = Math.round(height * ratio);
    const context = element.getContext('2d');
    if (!context) {
      return;
    }
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    // Mat açıq-boz fon
    context.fillStyle = '#c4c5c7';
    context.fillRect(0, 0, width, height);
    // Giloş naxışı: bir-birini təkrarlayan dalğalı nazik xətlər (poz-qazan təbəqəsi kimi)
    context.lineWidth = 0.8;
    context.strokeStyle = 'rgba(70, 72, 78, 0.32)';
    for (let line = -6; line < height / 4 + 6; line++) {
      const base = line * 4;
      context.beginPath();
      for (let x = 0; x <= width; x += 2) {
        const y = base + 6 * Math.sin(x / 23 + line * 0.35) + 3 * Math.sin(x / 9 - line * 0.6);
        if (x === 0) {
          context.moveTo(x, y);
        } else {
          context.lineTo(x, y);
        }
      }
      context.stroke();
    }
    // Yüngül dən (toz) effekti — sabit toxumla, hər dəfə eyni görünür
    let seed = 7;
    const random = () => {
      seed = (seed * 16807) % 2147483647;
      return seed / 2147483647;
    };
    for (let i = 0; i < (width * height) / 40; i++) {
      context.fillStyle = random() > 0.5 ? 'rgba(255, 255, 255, 0.35)' : 'rgba(60, 60, 66, 0.18)';
      context.fillRect(random() * width, random() * height, 1, 1);
    }
  }, []);

  // Ölçü dəyişəndə (dialoqun açılma animasiyası, ekranın dönməsi) — silinməyə başlanmayıbsa örtük yenidən çəkilir
  const touched = useRef(false);
  useEffect(() => {
    const box = container.current;
    if (revealed || !box) {
      return;
    }
    paint();
    if (typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(() => {
      if (!touched.current) {
        paint();
      }
    });
    observer.observe(box);
    return () => observer.disconnect();
  }, [revealed, paint]);

  const reveal = useCallback(() => {
    setFading(true);
    // "transitionend" gəlməyə bilər (azaldılmış hərəkət, gizli tab) — örtük hər halda silinir
    setTimeout(() => setFading(false), 700);
    navigator.vibrate?.(15);
    onReveal();
  }, [onReveal]);

  const point = (event: PointerEvent<HTMLCanvasElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  };

  const scratch = (to: { x: number; y: number }) => {
    const context = canvas.current?.getContext('2d');
    if (!context) {
      return;
    }
    context.globalCompositeOperation = 'destination-out';
    // Fırça tam qeyri-şəffaf olmalıdır: "destination-out" örtüyü fırçanın şəffaflığı qədər silir
    context.strokeStyle = '#000000';
    context.lineCap = 'round';
    context.lineJoin = 'round';
    context.lineWidth = BRUSH_RADIUS * 2;
    context.beginPath();
    const from = last.current ?? to;
    context.moveTo(from.x, from.y);
    context.lineTo(to.x, to.y);
    context.stroke();
    last.current = to;
  };

  /** Silinmiş hissənin payı (hər 8-ci pikselin şəffaflığına görə). */
  const clearedShare = () => {
    const element = canvas.current;
    const context = element?.getContext('2d');
    if (!element || !context || element.width === 0) {
      return 0;
    }
    const { data } = context.getImageData(0, 0, element.width, element.height);
    let cleared = 0;
    let total = 0;
    for (let i = 3; i < data.length; i += 32) {
      total++;
      if (data[i] === 0) {
        cleared++;
      }
    }
    return total ? cleared / total : 0;
  };

  const stop = () => {
    if (!drawing.current) {
      return;
    }
    drawing.current = false;
    last.current = undefined;
    if (clearedShare() >= REVEAL_THRESHOLD) {
      reveal();
    }
  };

  return (
    <div ref={container} className={cn('relative overflow-hidden rounded-xl', className)}>
      <div aria-hidden={!revealed}>{children}</div>
      {!revealed || fading ? (
        <>
          <canvas
            ref={canvas}
            aria-hidden="true"
            className={cn(
              'absolute inset-0 size-full cursor-pointer touch-none select-none transition-opacity duration-500 motion-reduce:transition-none',
              fading ? 'pointer-events-none opacity-0' : 'opacity-100',
            )}
            onTransitionEnd={() => setFading(false)}
            onPointerDown={(event) => {
              drawing.current = true;
              touched.current = true;
              event.currentTarget.setPointerCapture(event.pointerId);
              scratch(point(event));
            }}
            onPointerMove={(event) => {
              if (drawing.current) {
                scratch(point(event));
              }
            }}
            onPointerUp={stop}
            onPointerCancel={stop}
          />
          {!revealed ? (
            <>
              <span className="sr-only">{hiddenLabel}</span>
              <button
                type="button"
                onClick={reveal}
                className="sr-only rounded-full bg-primary px-4 py-2 text-sm font-medium text-primary-foreground focus:not-sr-only focus:absolute focus:left-1/2 focus:top-1/2 focus:-translate-x-1/2 focus:-translate-y-1/2"
              >
                {revealLabel}
              </button>
            </>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
