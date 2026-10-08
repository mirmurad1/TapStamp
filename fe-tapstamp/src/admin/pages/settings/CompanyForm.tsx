import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { companyApi, type CompanyContent, type Localized } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { toLocalized } from '../../lib/localized';

const COMPANY_KEY = ['settings-company'] as const;
const TIME_ZONES = ['Asia/Baku', 'Asia/Tbilisi', 'Europe/Istanbul', 'Europe/Moscow', 'Asia/Dubai', 'Europe/London', 'UTC'] as const;

/** 3 dildə mətn sahəsi: az məcburi, en və ru istəyə bağlı (boşdursa müştəriyə az göstərilir). */
export function LocalizedFields({
  id,
  legend,
  value,
  onChange,
  required,
  maxLength = 120,
}: {
  id: string;
  legend: string;
  value: Localized;
  onChange: (next: Localized) => void;
  required?: boolean;
  maxLength?: number;
}) {
  const { t } = useTranslation('admin');
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium">{legend}</legend>
      {SUPPORTED_LANGUAGES.map((lang) => (
        <div key={lang} className="flex items-center gap-2">
          <label htmlFor={`${id}-${lang}`} className="w-8 shrink-0 text-xs font-semibold uppercase text-muted-foreground">
            {lang}
          </label>
          <Input
            id={`${id}-${lang}`}
            value={value[lang] ?? ''}
            required={required && lang === 'az'}
            maxLength={maxLength}
            placeholder={lang === 'az' ? undefined : t('settings.company.translationPlaceholder')}
            onChange={(event) => onChange({ ...value, [lang]: event.target.value })}
          />
        </div>
      ))}
    </fieldset>
  );
}

export function CompanyCard() {
  const { t } = useTranslation('admin');
  const company = useQuery({ queryKey: COMPANY_KEY, queryFn: companyApi.get });
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Building className="size-5 text-primary" aria-hidden="true" />
          {t('settings.company.title')}
        </CardTitle>
        <CardDescription>{t('settings.company.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <ApiErrorAlert error={company.error} />
        {company.isPending ? <InlineLoading /> : null}
        {company.data ? <CompanyForm initial={company.data} /> : null}
      </CardContent>
    </Card>
  );
}

function CompanyForm({ initial }: { initial: CompanyContent }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [name, setName] = useState<Localized>({ az: initial.name?.az ?? '', en: initial.name?.en, ru: initial.name?.ru });
  const [description, setDescription] = useState<Localized>({
    az: initial.description?.az ?? '',
    en: initial.description?.en,
    ru: initial.description?.ru,
  });
  const [timezone, setTimezone] = useState(initial.timezone ?? 'Asia/Baku');
  const [defaultLanguage, setDefaultLanguage] = useState(initial.defaultLanguage ?? 'az');
  const [brandColor, setBrandColor] = useState(initial.brandColor ?? '#7C3AED');
  const [smsSender, setSmsSender] = useState(initial.smsSenderName ?? '');
  const zones = TIME_ZONES.includes(timezone as (typeof TIME_ZONES)[number]) ? TIME_ZONES : [timezone, ...TIME_ZONES];

  const save = useMutation({
    mutationFn: () =>
      companyApi.update({
        name: toLocalized(name) ?? { az: '' },
        description: toLocalized(description),
        timezone,
        defaultLanguage,
        brandColor,
        smsSenderName: smsSender.trim() || undefined,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(COMPANY_KEY, updated);
      void queryClient.invalidateQueries({ queryKey: ['public-company'] });
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={submit}>
      {save.isSuccess ? <Alert tone="success">{t('admin:settings.saved')}</Alert> : null}
      <ApiErrorAlert error={save.error} />
      <LocalizedFields id="company-name" legend={t('admin:settings.company.name')} value={name} onChange={setName} required />
      <LocalizedFields
        id="company-description"
        legend={t('admin:settings.company.about')}
        value={description}
        onChange={setDescription}
        maxLength={500}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id="company-timezone" label={t('admin:settings.company.timezone')}>
          <Select value={timezone} onChange={(event) => setTimezone(event.target.value)}>
            {zones.map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </Select>
        </Field>
        <Field id="company-language" label={t('admin:settings.company.defaultLanguage')}>
          <Select value={defaultLanguage} onChange={(event) => setDefaultLanguage(event.target.value)}>
            {SUPPORTED_LANGUAGES.map((lang) => (
              <option key={lang} value={lang}>
                {t(`common:language.${lang}`)}
              </option>
            ))}
          </Select>
        </Field>
        <Field id="company-color" label={t('admin:settings.company.brandColor')} hint={t('admin:settings.company.brandColorHint')}>
          <div className="flex items-center gap-3">
            <input
              type="color"
              value={brandColor}
              onChange={(event) => setBrandColor(event.target.value)}
              className="h-11 w-16 cursor-pointer rounded-lg border border-border bg-transparent"
              aria-label={t('admin:settings.company.brandColor')}
            />
            <code className="text-sm uppercase">{brandColor}</code>
          </div>
        </Field>
        <Field id="company-sms" label={t('admin:settings.company.smsSender')} hint={t('admin:settings.company.smsSenderHint')}>
          <Input value={smsSender} maxLength={11} onChange={(event) => setSmsSender(event.target.value)} />
        </Field>
      </div>
      <Button type="submit" className="self-start" disabled={save.isPending || !name.az.trim()}>
        {t('common:actions.save')}
      </Button>
    </form>
  );
}
