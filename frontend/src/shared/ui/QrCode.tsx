import QRCode from 'qrcode';
import { useMemo } from 'react';

/**
 * QR kod React SVG-si kimi çəkilir (data: URL və innerHTML yoxdur — CSP ilə uyğundur). Ağ fon və 4 modullu
 * kənar boşluq skanerlərin qaranlıq rejimdə də oxuması üçündür.
 */
export function QrCode({ value, label, className }: { value: string; label: string; className?: string }) {
  const { size, path } = useMemo(() => {
    const qr = QRCode.create(value, { errorCorrectionLevel: 'M' });
    const count = qr.modules.size;
    const segments: string[] = [];
    for (let y = 0; y < count; y++) {
      for (let x = 0; x < count; x++) {
        if (qr.modules.get(x, y)) {
          segments.push(`M${x + 4} ${y + 4}h1v1h-1z`);
        }
      }
    }
    return { size: count + 8, path: segments.join('') };
  }, [value]);

  return (
    <svg role="img" aria-label={label} viewBox={`0 0 ${size} ${size}`} className={className} shapeRendering="crispEdges">
      <rect width={size} height={size} fill="#ffffff" />
      <path d={path} fill="#000000" />
    </svg>
  );
}
