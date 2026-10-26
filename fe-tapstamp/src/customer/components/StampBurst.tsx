import { Check, Gift, MapPin } from 'lucide-react';
import { useEffect, useId, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';

import { currentLanguage } from '@/shared/i18n/setup';
import { cn } from '@/shared/lib/cn';

/** Səhnənin uzunluğu: dalğa → zərbə → sayğac → kart qalxır → möhür xanaya uçur. */
export const STAMP_BURST_MS = 3000;

interface StampBurstProps {
  /** Möhürdən əvvəlki və sonrakı say (kart dolubsa, sonrakı = stampsRequired). */
  before: number;
  after: number;
  stampsRequired: number;
  companyName: string;
  branch: string;
  /** Kartın altındakı mətn: "Hədiyyəyə N möhür qalıb" və ya "Kart doldu!". */
  progressText: string;
  /** Avtomatik keçidə qədər vaxt (zolaq bu müddətdə dolur); verilməyibsə zolaq göstərilmir. */
  redirectMs?: number;
}

const PARTICLES = 22;

function reducedMotion(): boolean {
  return Boolean(window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);
}

/**
 * Möhür vurulma səhnəsi (tam ekran, brend rəngində):
 * 1) toxunuş nöqtəsindən NFC dalğaları yayılır, ekran brend rənginə boyanır;
 * 2) rezin möhür (şirkət, tarix, filial) yuxarıdan zərblə enir — ekran silkələnir, hissəciklər yayılır, telefon titrəyir;
 * 3) iri sayğac köhnə saydan yenisinə fırlanır;
 * 4) aşağıdan kart qalxır, möhür kiçilib yeni xanaya uçur və orada yanır.
 * Animasiya API-si olmayan mühitdə (testlər) və "azaldılmış hərəkət" rejimində birbaşa son vəziyyət göstərilir.
 */
export function StampBurst({ before, after, stampsRequired, companyName, branch, progressText, redirectMs }: StampBurstProps) {
  const { t } = useTranslation('profile');
  const arcId = useId();
  const root = useRef<HTMLDivElement>(null);
  const seal = useRef<HTMLDivElement>(null);
  const roll = useRef<HTMLSpanElement>(null);
  const counter = useRef<HTMLDivElement>(null);
  const sheet = useRef<HTMLDivElement>(null);
  const slots = useRef<HTMLOListElement>(null);
  const [landed, setLanded] = useState(false);
  const [impact, setImpact] = useState(false);

  // Möhürün kənarındakı yazı: ŞİRKƏT ★ TARİX ★ FİLİAL (dairəyə sığması üçün qısaldılır)
  const sealText = useMemo(() => {
    const lang = currentLanguage();
    const date = new Intl.DateTimeFormat(lang, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(new Date());
    const parts = [companyName, date, branch].filter(Boolean).map((part) => part.toLocaleUpperCase(lang));
    return `${parts.join(' ★ ')} ★ `.slice(0, 44);
  }, [companyName, branch]);

  const particles = useMemo(
    () =>
      Array.from({ length: PARTICLES }, (_, i) => {
        const angle = (i / PARTICLES) * Math.PI * 2 + ((i * 7) % 5) * 0.06;
        const distance = 90 + ((i * 37) % 80);
        return {
          x: `${Math.cos(angle) * distance}px`,
          y: `${Math.sin(angle) * distance}px`,
          size: 4 + ((i * 13) % 7),
          square: i % 4 === 0,
          tint: i % 3 === 0,
        };
      }),
    [],
  );

  useEffect(() => {
    const elements = [root.current, seal.current, roll.current, counter.current, sheet.current, slots.current];
    if (elements.some((element) => !element) || typeof root.current?.animate !== 'function' || reducedMotion()) {
      // Animasiyasız: sayğac birbaşa yeni rəqəmdə
      if (roll.current) {
        roll.current.style.transform = 'translateY(-50%)';
      }
      setLanded(true);
      return undefined;
    }
    const [scene, stamp, digits, count, card, list] = elements as [
      HTMLDivElement,
      HTMLDivElement,
      HTMLSpanElement,
      HTMLDivElement,
      HTMLDivElement,
      HTMLOListElement,
    ];
    const timers: number[] = [];
    const animations: Animation[] = [];
    const at = (ms: number, step: () => void) => timers.push(window.setTimeout(step, ms));
    const play = (element: Element, keyframes: Keyframe[], options: KeyframeAnimationOptions) => {
      const animation = element.animate(keyframes, options);
      animations.push(animation);
      return animation;
    };

    // 2) zərbə
    play(
      stamp,
      [
        { transform: 'translateY(-120px) scale(2.6) rotate(-28deg)', opacity: 0, filter: 'blur(8px)' },
        { transform: 'translateY(0) scale(0.9) rotate(-12deg)', opacity: 1, filter: 'blur(0)', offset: 0.7 },
        { transform: 'scale(1.04, 0.96) rotate(-12deg)', offset: 0.85 },
        { transform: 'scale(1) rotate(-12deg)', opacity: 1 },
      ],
      { duration: 560, delay: 380, easing: 'cubic-bezier(.6,0,.9,.5)', fill: 'both' },
    );
    at(700, () => {
      setImpact(true);
      play(
        scene,
        [
          { transform: 'translate(0,0)' },
          { transform: 'translate(-6px,4px)' },
          { transform: 'translate(5px,-3px)' },
          { transform: 'translate(-3px,2px)' },
          { transform: 'translate(0,0)' },
        ],
        { duration: 320 },
      );
      navigator.vibrate?.(30);
    });
    // 3) sayğac
    play(count, [{ opacity: 0, transform: 'translateY(12px)' }, { opacity: 1, transform: 'none' }], {
      duration: 400,
      delay: 900,
      fill: 'both',
    });
    play(digits, [{ transform: 'translateY(0)' }, { transform: 'translateY(-58%)' }, { transform: 'translateY(-50%)' }], {
      duration: 650,
      delay: 1000,
      easing: 'cubic-bezier(.3,1.4,.5,1)',
      fill: 'both',
    });
    // 4) kart qalxır, sonra möhür yeni xanaya uçur
    const rise = play(card, [{ transform: 'translateY(105%)' }, { transform: 'translateY(0)' }], {
      duration: 520,
      delay: 1750,
      easing: 'cubic-bezier(.2,1.1,.3,1)',
      fill: 'both',
    });
    play(count, [{ transform: 'none' }, { transform: 'translateY(-22px) scale(.75)' }], {
      duration: 520,
      delay: 1750,
      easing: 'ease-out',
      fill: 'forwards',
      composite: 'add',
    });
    // Hədəf vərəq tam qalxandan sonra ölçülür (yavaş cihazda da möhür xananın üstünə düşür)
    let cancelled = false;
    rise.finished.then(() => {
      if (cancelled) {
        return;
      }
      const target = list.children[Math.min(after, stampsRequired) - 1]?.getBoundingClientRect();
      const from = stamp.getBoundingClientRect();
      if (!target || from.width === 0) {
        setLanded(true);
        return;
      }
      const dx = target.left + target.width / 2 - (from.left + from.width / 2);
      const dy = target.top + target.height / 2 - (from.top + from.height / 2);
      const scale = target.width / from.width;
      play(stamp, [{ transform: 'scale(1) rotate(-12deg)' }, { transform: `translate(${dx}px,${dy}px) scale(${scale}) rotate(0deg)` }], {
        duration: 560,
        easing: 'cubic-bezier(.5,-.3,.3,1)',
        fill: 'forwards',
      }).onfinish = () => setLanded(true);
    }, () => {
      // Animasiya ləğv olundu (səhifədən çıxış) — heç nə etmirik
    });
    return () => {
      cancelled = true;
      timers.forEach((timer) => window.clearTimeout(timer));
      animations.forEach((animation) => animation.cancel());
    };
  }, [after, stampsRequired]);

  return (
    <div
      ref={root}
      className="fixed inset-0 z-30 overflow-hidden bg-background text-primary-foreground"
      // Səhnə dekorativdir; ekran oxuyucusu üçün başlıq və mətn kartın içindədir
    >
      <div className="stamp-flood absolute inset-0" aria-hidden="true" />
      <div className="absolute top-12 left-1/2" aria-hidden="true">
        {[0, 1, 2].map((ring) => (
          <span key={ring} className="stamp-ripple absolute -mt-5 -ml-5 size-10 rounded-full border-2 border-white" style={{ animationDelay: `${ring * 0.15}s` }} />
        ))}
      </div>
      <div className="mx-auto flex h-full max-w-md flex-col items-center">
        <div className="relative mt-[12dvh] size-44" aria-hidden="true">
          {/* Hissəciklər və zərbə dalğası: möhür masaya dəyən an */}
          {impact ? (
            <div className="absolute top-1/2 left-1/2">
              <span className="stamp-shock absolute -mt-[90px] -ml-[90px] size-[180px] rounded-full border-[10px] border-white" />
              {particles.map((particle, index) => (
                <i
                  key={index}
                  className={cn('stamp-particle absolute', particle.square ? 'rounded-[2px]' : 'rounded-full', particle.tint ? 'bg-[color-mix(in_srgb,var(--primary)_30%,#fff)]' : 'bg-white')}
                  style={{ width: particle.size, height: particle.size, '--x': particle.x, '--y': particle.y } as CSSProperties}
                />
              ))}
            </div>
          ) : null}
          <div ref={seal} className={cn('absolute inset-0 z-10 text-white drop-shadow-[0_10px_18px_rgb(0_0_0/0.25)]', landed && 'opacity-0')}>
            <svg viewBox="0 0 200 200" className="size-full">
              <defs>
                <path id={arcId} d="M100,100 m-72,0 a72,72 0 1,1 144,0 a72,72 0 1,1 -144,0" />
              </defs>
              <circle cx="100" cy="100" r="94" fill="none" stroke="currentColor" strokeWidth="6" />
              <circle cx="100" cy="100" r="84" fill="none" stroke="currentColor" strokeWidth="2" strokeDasharray="3 5" />
              <text fontSize="14.5" fontWeight="800" letterSpacing="3.4" fill="currentColor">
                <textPath href={`#${arcId}`}>{sealText}</textPath>
              </text>
              <circle cx="100" cy="100" r="54" fill="currentColor" />
              <path d="M76 101l16 16 32-34" fill="none" stroke="var(--primary)" strokeWidth="11" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </div>
        </div>
        <div ref={counter} className="mt-6 flex items-baseline gap-1 font-extrabold tracking-tighter" aria-hidden="true">
          <span className="inline-block h-[84px] overflow-hidden text-[84px] leading-[84px]">
            <span ref={roll} className="flex flex-col">
              <span>{before}</span>
              <span>{after}</span>
            </span>
          </span>
          <span className="text-3xl opacity-70">/{stampsRequired}</span>
        </div>
      </div>
      <div
        ref={sheet}
        className="absolute inset-x-0 bottom-0 mx-auto flex max-w-md flex-col items-center gap-2.5 rounded-t-[28px] bg-background px-5 pt-6 pb-[max(1.5rem,env(safe-area-inset-bottom))] text-center text-foreground shadow-[0_-16px_40px_rgb(0_0_0/0.2)]"
      >
        <h1 className="text-2xl font-extrabold tracking-tight">{t('tap.stamped')}</h1>
        {branch ? (
          <p className="flex items-center gap-1 text-sm text-muted-foreground">
            <MapPin className="size-4" aria-hidden="true" />
            {branch}
          </p>
        ) : null}
        <div className="w-full rounded-2xl border border-border bg-card p-3.5 text-left">
          <p className="flex justify-between text-[13px] font-semibold">
            <span>{progressText}</span>
            <span className="font-extrabold text-primary tabular-nums">
              {after}/{stampsRequired}
            </span>
          </p>
          <ol ref={slots} className="mt-2.5 grid grid-cols-5 gap-2" aria-hidden="true">
            {Array.from({ length: stampsRequired }, (_, index) => {
              const isGift = index === stampsRequired - 1;
              const filled = index < (landed ? after : before);
              const fresh = landed && index >= before && index < after;
              return (
                <li
                  key={index}
                  className={cn(
                    'relative grid aspect-square place-items-center rounded-full text-xs font-bold [&_svg]:size-4',
                    filled
                      ? 'bg-primary text-primary-foreground shadow-[inset_0_-3px_0_rgb(0_0_0/0.15)]'
                      : isGift
                        ? 'border-2 border-primary/35 bg-primary/10 text-primary'
                        : 'border-2 border-dashed border-border text-muted-foreground/50',
                    fresh && 'animate-stamp-pop',
                    fresh && isGift && 'animate-gift-bounce',
                  )}
                >
                  {fresh ? <span className="animate-stamp-ring absolute -inset-0.5 rounded-full border-[3px] border-primary" /> : null}
                  {isGift ? <Gift /> : filled ? <Check strokeWidth={3} /> : index + 1}
                </li>
              );
            })}
          </ol>
        </div>
        {/* Sətrin yeri əvvəlcədən ayrılır: sonradan əlavə olunsa vərəq hündürləşib xanaları yuxarı sürüşdürər və
            uçan möhür xananın üstünə düşməz */}
        {redirectMs ? (
          <p className={cn('flex items-center gap-2 text-[12.5px] text-muted-foreground', !landed && 'invisible')}>
            {t('tap.opening')}
            <span className="block h-1 w-14 overflow-hidden rounded-full bg-muted" aria-hidden="true">
              {landed ? <span className="stamp-countdown block h-full bg-primary" style={{ animationDuration: `${redirectMs}ms` }} /> : null}
            </span>
          </p>
        ) : null}
      </div>
    </div>
  );
}
