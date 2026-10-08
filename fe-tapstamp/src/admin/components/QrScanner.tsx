import jsQR from 'jsqr';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';

interface BarcodeDetectorLike {
  detect(source: CanvasImageSource): Promise<{ rawValue: string }[]>;
}

declare global {
  interface Window {
    BarcodeDetector?: new (options: { formats: string[] }) => BarcodeDetectorLike;
  }
}

/**
 * Kamera ilə QR skaneri (planşet və telefon üçün arxa kamera). Brauzerin öz BarcodeDetector-u varsa (Chrome, Edge,
 * Android) o, yoxdursa (Safari) jsQR işlədilir. Kod tanınan kimi {@code onResult} çağırılır və kamera dayanır.
 */
export function QrScanner({ onResult }: { onResult: (value: string) => void }) {
  const { t } = useTranslation('admin');
  const video = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState(false);
  const onResultRef = useRef(onResult);
  onResultRef.current = onResult;

  useEffect(() => {
    let stream: MediaStream | undefined;
    let frame = 0;
    let stopped = false;
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d', { willReadFrequently: true });
    const detector = window.BarcodeDetector ? new window.BarcodeDetector({ formats: ['qr_code'] }) : undefined;

    const finish = (value: string) => {
      stopped = true;
      onResultRef.current(value);
    };

    const scan = async () => {
      const element = video.current;
      if (stopped || !element) {
        return;
      }
      if (element.readyState >= 2 && element.videoWidth > 0) {
        try {
          if (detector) {
            const codes = await detector.detect(element);
            if (codes[0]?.rawValue) {
              finish(codes[0].rawValue);
              return;
            }
          } else if (context) {
            canvas.width = element.videoWidth;
            canvas.height = element.videoHeight;
            context.drawImage(element, 0, 0);
            const image = context.getImageData(0, 0, canvas.width, canvas.height);
            const code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
            if (code?.data) {
              finish(code.data);
              return;
            }
          }
        } catch {
          // Növbəti kadrda yenidən cəhd edilir
        }
      }
      frame = window.requestAnimationFrame(() => void scan());
    };

    navigator.mediaDevices
      ?.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((media) => {
        if (stopped) {
          media.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = media;
        if (video.current) {
          video.current.srcObject = media;
          void video.current.play();
        }
        frame = window.requestAnimationFrame(() => void scan());
      })
      .catch(() => setError(true));
    if (!navigator.mediaDevices) {
      setError(true);
    }

    return () => {
      stopped = true;
      window.cancelAnimationFrame(frame);
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  if (error) {
    return <Alert tone="error">{t('giftDesk.cameraError')}</Alert>;
  }
  return (
    <div className="relative overflow-hidden rounded-2xl bg-black">
      <video ref={video} className="aspect-square w-full object-cover" muted playsInline />
      <div className="pointer-events-none absolute inset-[18%] rounded-2xl border-4 border-white/80" aria-hidden="true" />
    </div>
  );
}
