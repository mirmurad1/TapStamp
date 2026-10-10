import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Building, ImageUp, Stamp, Trash2 } from 'lucide-react';
import { useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { companyApi, contextApi, type CompanyContent, type Localized } from '../../api/adminApi';
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
        {company.data ? <LogoField /> : null}
        {company.data ? <CompanyForm initial={company.data} /> : null}
      </CardContent>
    </Card>
  );
}

/** Tələblər backend-dəki CompanyLogoService ilə eynidir (server də yoxlayır). */
const LOGO_MAX_BYTES = 256 * 1024;
const LOGO_MIN_SIZE = 192;
const LOGO_MAX_SIZE = 1024;

async function logoFits(file: File): Promise<boolean> {
  if (file.size > LOGO_MAX_BYTES || !['image/png', 'image/jpeg'].includes(file.type)) {
    return false;
  }
  try {
    const bitmap = await createImageBitmap(file);
    const { width, height } = bitmap;
    bitmap.close();
    return width === height && width >= LOGO_MIN_SIZE && width <= LOGO_MAX_SIZE;
  } catch {
    return false;
  }
}

/** Müştəri tətbiqinin başlığındakı loqo: önizləmə, yükləmə və silmə; tələblər altda yazılır. */
function LogoField() {
  const { t } = useTranslation('admin');
  const queryClient = useQueryClient();
  const input = useRef<HTMLInputElement>(null);
  const [invalid, setInvalid] = useState(false);
  const publicCompany = useQuery({ queryKey: ['public-company'], queryFn: contextApi.company });
  const logoUrl = publicCompany.data?.logoUrl;
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['public-company'] });
  const upload = useMutation({ mutationFn: companyApi.uploadLogo, onSuccess: refresh });
  const remove = useMutation({ mutationFn: companyApi.removeLogo, onSuccess: refresh });
  const busy = upload.isPending || remove.isPending;

  const choose = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) {
      return;
    }
    remove.reset();
    const fits = await logoFits(file);
    setInvalid(!fits);
    if (fits) {
      upload.mutate(file);
    }
  };
  const removeLogo = () => {
    upload.reset();
    setInvalid(false);
    remove.mutate();
  };

  return (
    <section className="mb-6 flex flex-col gap-3 border-b border-border pb-6" aria-labelledby="company-logo-title">
      <h3 id="company-logo-title" className="text-sm font-medium">
        {t('settings.company.logo.title')}
      </h3>
      <div className="flex flex-wrap items-center gap-4">
        <span className="grid size-16 shrink-0 place-items-center overflow-hidden rounded-full bg-primary text-primary-foreground">
          {logoUrl ? (
            <img src={logoUrl} alt={t('settings.company.logo.current')} className="size-full object-cover" />
          ) : (
            <Stamp className="size-7" aria-label={t('settings.company.logo.defaultIcon')} />
          )}
        </span>
        <div className="flex flex-wrap gap-2">
          <input ref={input} type="file" accept="image/png,image/jpeg" className="hidden" onChange={(event) => void choose(event)} />
          <Button type="button" variant="outline" disabled={busy} onClick={() => input.current?.click()}>
            <ImageUp aria-hidden="true" />
            {logoUrl ? t('settings.company.logo.replace') : t('settings.company.logo.upload')}
          </Button>
          {logoUrl ? (
            <Button type="button" variant="ghost" disabled={busy} onClick={removeLogo}>
              <Trash2 aria-hidden="true" />
              {t('settings.company.logo.remove')}
            </Button>
          ) : null}
        </div>
      </div>
      <p className="text-sm text-muted-foreground">{t('settings.company.logo.hint')}</p>
      <p className="text-sm font-medium">{t('settings.company.logo.requirements')}</p>
      {invalid ? <Alert tone="error">{t('settings.company.logo.invalid')}</Alert> : null}
      {upload.isSuccess ? <Alert tone="success">{t('settings.company.logo.uploaded')}</Alert> : null}
      {remove.isSuccess ? <Alert tone="success">{t('settings.company.logo.removed')}</Alert> : null}
      <ApiErrorAlert error={upload.error ?? remove.error} />
    </section>
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
