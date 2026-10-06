import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { z } from 'zod';

import { SUPPORTED_LANGUAGES } from '@/shared/i18n/languages';
import { Button } from '@/shared/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

import { platformApi } from '../../api/adminApi';
import { useStepUp } from '../../auth/StepUpProvider';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { useValidationMessage } from '../../lib/forms';
import { TENANTS_KEY } from './keys';

const optionalName = z.string().trim().max(200, 'tooLong');
const schema = z.object({
  slug: z.string().trim().regex(/^[a-z][a-z0-9_]{1,30}$/, 'slug'),
  nameAz: z.string().trim().min(1, 'required').max(200, 'tooLong'),
  nameEn: optionalName,
  nameRu: optionalName,
  primaryDomain: z
    .string()
    .trim()
    .toLowerCase()
    .max(253, 'tooLong')
    .regex(/^[a-z0-9-]+(\.[a-z0-9-]+)+$/, 'domain'),
  defaultLanguage: z.enum(SUPPORTED_LANGUAGES),
  timezone: z.string().trim().min(1, 'required').max(64, 'tooLong'),
});
type Values = z.infer<typeof schema>;

/** Yeni şirkət: ayrıca baza və rollar yaradılır (step-up tələb olunur). */
export function ProvisionTenantPage() {
  const { t } = useTranslation(['admin', 'common']);
  const validation = useValidationMessage();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const withStepUp = useStepUp();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      slug: '',
      nameAz: '',
      nameEn: '',
      nameRu: '',
      primaryDomain: '',
      defaultLanguage: 'az',
      timezone: 'Asia/Baku',
    },
  });
  const provision = useMutation({
    mutationFn: (values: Values) =>
      withStepUp(() =>
        platformApi.provision({
          slug: values.slug,
          displayName: { az: values.nameAz, en: values.nameEn || undefined, ru: values.nameRu || undefined },
          primaryDomain: values.primaryDomain,
          defaultLanguage: values.defaultLanguage,
          timezone: values.timezone,
        }),
      ),
    onSuccess: (tenant) => {
      void queryClient.invalidateQueries({ queryKey: TENANTS_KEY });
      void navigate(`/tenants/${tenant.id}`, { replace: true, state: { created: true } });
    },
  });
  const error = (name: keyof Values) => validation(form.formState.errors[name]?.message);

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <Button asChild variant="ghost" className="self-start">
        <Link to="/">
          <ArrowLeft aria-hidden="true" />
          {t('admin:platform.detail.back')}
        </Link>
      </Button>
      <Card>
        <CardHeader>
          <CardTitle>{t('admin:platform.provision.title')}</CardTitle>
          <CardDescription>{t('admin:platform.provision.description')}</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={form.handleSubmit((values) => provision.mutate(values))} className="flex flex-col gap-4" noValidate>
            <ApiErrorAlert error={provision.error} />
            <Field id="slug" label={t('admin:platform.provision.slug')} hint={t('admin:platform.provision.slugHint')} error={error('slug')}>
              <Input {...form.register('slug')} autoComplete="off" autoCapitalize="none" spellCheck={false} className="font-mono" />
            </Field>
            <Field id="name-az" label={t('admin:platform.provision.nameAz')} error={error('nameAz')}>
              <Input {...form.register('nameAz')} lang="az" />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="name-en" label={t('admin:platform.provision.nameEn')} error={error('nameEn')}>
                <Input {...form.register('nameEn')} lang="en" />
              </Field>
              <Field id="name-ru" label={t('admin:platform.provision.nameRu')} error={error('nameRu')}>
                <Input {...form.register('nameRu')} lang="ru" />
              </Field>
            </div>
            <Field
              id="domain"
              label={t('admin:platform.provision.domain')}
              hint={t('admin:platform.provision.domainHint')}
              error={error('primaryDomain')}
            >
              <Input {...form.register('primaryDomain')} inputMode="url" autoCapitalize="none" spellCheck={false} />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="default-language" label={t('admin:platform.provision.language')}>
                <Select {...form.register('defaultLanguage')}>
                  {SUPPORTED_LANGUAGES.map((lang) => (
                    <option key={lang} value={lang} lang={lang}>
                      {t(`common:language.${lang}`)}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field id="timezone" label={t('admin:platform.provision.timezone')} error={error('timezone')}>
                <Input {...form.register('timezone')} autoComplete="off" />
              </Field>
            </div>
            <Button type="submit" size="lg" className="self-start" disabled={provision.isPending}>
              {t('admin:platform.provision.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
