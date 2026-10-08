import { useEffect, useState } from 'react';

/** Verilən ana (ms) qədər qalan saniyələr; hər saniyə yenilənir. */
export function useSecondsUntil(until: number | undefined): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (until === undefined) {
      return undefined;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [until]);
  return until === undefined ? 0 : Math.max(0, Math.ceil((until - now) / 1000));
}
