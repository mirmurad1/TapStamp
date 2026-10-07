import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, Nfc, Repeat } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Select } from '@/shared/ui/input';
import { QrCode } from '@/shared/ui/QrCode';

import { simulatorApi, type SimulatedTap } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';

import { SIMULATOR_TAGS_KEY } from './simulatorKeys';

/**
 * NFC simulyatoru (yalnız dev): çip seçilir, "Toxun" imzalı link yaradır. Link bu brauzerdə açıla və ya QR ilə
 * telefonda skan edilə bilər; "eyni linki təkrar aç" replay qorunmasını nümayiş etdirir.
 */
export function SimulatorPage() {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const tags = useQuery({ queryKey: SIMULATOR_TAGS_KEY, queryFn: simulatorApi.tags });
  const [selected, setSelected] = useState<string>();
  const [last, setLast] = useState<SimulatedTap>();
  const tagId = selected ?? tags.data?.[0]?.id;
  const tag = tags.data?.find((candidate) => candidate.id === tagId);
  const tap = useMutation({
    mutationFn: () => simulatorApi.tap(tagId ?? ''),
    onSuccess: (result) => {
      setLast(result);
      void queryClient.invalidateQueries({ queryKey: SIMULATOR_TAGS_KEY });
    },
  });
  const url = last ? `${window.location.origin}${last.path}` : undefined;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-bold">{t('simulator.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('simulator.subtitle')}</p>
      </div>
      <ApiErrorAlert error={tags.error ?? tap.error} />
      {tags.isPending ? <InlineLoading /> : null}
      {tags.data ? (
        <Card>
          <CardContent className="flex flex-wrap items-end gap-4 pt-6">
            <Field id="simulator-tag" label={t('simulator.tag')}>
              <Select
                value={tagId}
                onChange={(event) => {
                  setSelected(event.target.value);
                  setLast(undefined);
                }}
              >
                {tags.data.map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {`${resolveText(candidate.branchName, lang)} · ${candidate.label} · ${candidate.uid}`}
                  </option>
                ))}
              </Select>
            </Field>
            <Button size="lg" disabled={!tagId || tap.isPending} onClick={() => tap.mutate()}>
              <Nfc aria-hidden="true" />
              {t('simulator.tap')}
            </Button>
            {tag ? (
              <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                <Badge>{t(`simulator.types.${tag.type}`)}</Badge>
                {!tag.active ? <Badge tone="danger">{t('simulator.disabled')}</Badge> : null}
                <span>{t('simulator.lastCounter', { counter: tag.lastCounter })}</span>
              </div>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
      {last && url ? (
        <Card>
          <CardHeader>
            <CardTitle>{t('simulator.link')}</CardTitle>
            <CardDescription>{t('simulator.counter', { counter: last.counter })}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-6 md:grid-cols-[1fr_auto]">
            <div className="flex min-w-0 flex-col gap-3">
              <code className="break-all rounded-xl bg-muted p-3 text-xs" data-testid="simulator-url">
                {url}
              </code>
              <div className="flex flex-wrap gap-2">
                <Button asChild>
                  <a href={last.path} target="_blank" rel="noopener noreferrer">
                    <ExternalLink aria-hidden="true" />
                    {t('simulator.open')}
                  </a>
                </Button>
                <Button asChild variant="outline">
                  <a href={last.path} target="_blank" rel="noopener noreferrer">
                    <Repeat aria-hidden="true" />
                    {t('simulator.replay')}
                  </a>
                </Button>
              </div>
              <Alert>{t('simulator.replayHint')}</Alert>
            </div>
            <div className="flex flex-col items-center gap-2">
              <QrCode value={url} label={t('simulator.qrLabel')} className="w-48 rounded-xl" />
              <p className="max-w-48 text-center text-xs text-muted-foreground">{t('simulator.qr')}</p>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
