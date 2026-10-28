import { useTranslation } from 'react-i18next';

import { cn } from '@/shared/lib/cn';

import { Select } from './input';

const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));
const MINUTE_STEP = 5;
const MINUTES = Array.from({ length: 60 / MINUTE_STEP }, (_, index) => String(index * MINUTE_STEP).padStart(2, '0'));

interface TimeSelectProps {
  /** "HH:mm" (24 saat). */
  value: string;
  onChange: (value: string) => void;
  /** Ekran oxuyucusu üçün sahənin adı (məs. "Başlama saatı"). */
  label: string;
  className?: string;
}

/**
 * Həmişə 24 saatlıq saat seçimi (saat və dəqiqə siyahıları). Brauzerin {@code <input type="time">} sahəsi formatı
 * əməliyyat sisteminin regional ayarlarından götürür və AM/PM göstərə bilir — burada bu olmur. Dəqiqələr 5-5 artır;
 * mövcud dəyər bu addıma düşmürsə, o da siyahıda qalır.
 */
export function TimeSelect({ value, onChange, label, className }: TimeSelectProps) {
  const { t } = useTranslation('common');
  const [hour = '00', minute = '00'] = value.split(':');
  const minutes = MINUTES.includes(minute) ? MINUTES : [...MINUTES, minute].sort();
  return (
    <span role="group" aria-label={label} className={cn('inline-flex items-center gap-0.5', className)}>
      <Select
        value={hour}
        onChange={(event) => onChange(`${event.target.value}:${minute}`)}
        aria-label={`${label}: ${t('time.hours')}`}
        className="w-14 bg-[position:right_0.25rem_center] pl-2 pr-5 tabular-nums"
      >
        {HOURS.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
      <span aria-hidden="true" className="font-semibold">
        :
      </span>
      <Select
        value={minute}
        onChange={(event) => onChange(`${hour}:${event.target.value}`)}
        aria-label={`${label}: ${t('time.minutes')}`}
        className="w-14 bg-[position:right_0.25rem_center] pl-2 pr-5 tabular-nums"
      >
        {minutes.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </Select>
    </span>
  );
}
