import { Check, Copy, Download } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';

interface RecoveryCodesListProps {
  codes: string[];
  onDone: () => void;
  doneLabel: string;
}

/**
 * Bərpa kodları yalnız bir dəfə göstərilir: kopyalamaq və ya fayl kimi yükləmək olar. "Davam et" düyməsi istifadəçi
 * kodları saxladığını təsdiqləyənə qədər aktiv deyil.
 */
export function RecoveryCodesList({ codes, onDone, doneLabel }: RecoveryCodesListProps) {
  const { t } = useTranslation(['auth', 'common']);
  const [acknowledged, setAcknowledged] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = codes.join('\n');

  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
  };

  const download = () => {
    const url = URL.createObjectURL(new Blob([text + '\n'], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = t('auth:mfaSetup.fileName');
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="flex flex-col gap-4">
      <ol className="grid grid-cols-2 gap-2 rounded-xl bg-muted p-4 font-mono text-sm" aria-label={t('auth:mfa.recoveryCode')}>
        {codes.map((code) => (
          <li key={code} className="select-all text-center">
            {code}
          </li>
        ))}
      </ol>
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => void copy()}>
          {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
          {copied ? t('common:actions.copied') : t('common:actions.copy')}
        </Button>
        <Button variant="outline" size="sm" onClick={download}>
          <Download aria-hidden="true" />
          {t('common:actions.download')}
        </Button>
      </div>
      <label className="flex items-start gap-3 text-sm">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(event) => setAcknowledged(event.target.checked)}
          className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
        />
        <span>{t('auth:mfaSetup.acknowledge')}</span>
      </label>
      <Button size="lg" disabled={!acknowledged} onClick={onDone}>
        {doneLabel}
      </Button>
    </div>
  );
}
