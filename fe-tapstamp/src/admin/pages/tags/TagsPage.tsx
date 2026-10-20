import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Alert } from '@/shared/ui/alert';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { Pagination } from '@/shared/ui/pagination';
import { usePagination } from '@/shared/ui/usePagination';

import { tagsApi, type NfcTag } from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { useBranchNames } from '../../lib/branches';
import { EventsDialog, TagCard } from './tagParts';

/** Şirkətin çipləri — yalnız baxış. Çipləri platforma quraşdırır və idarə edir. */
export function TagsPage() {
  const { t } = useTranslation(['admin', 'common']);
  const { nameOf } = useBranchNames();
  const paging = usePagination();
  const tags = useQuery({
    queryKey: ['tags', paging.page, paging.size],
    queryFn: () => tagsApi.list({ page: paging.page, size: paging.size }),
    placeholderData: keepPreviousData,
  });
  const items = tags.data?.items ?? [];
  const [eventsOf, setEventsOf] = useState<NfcTag>();

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="font-display text-2xl font-semibold">{t('admin:tags.title')}</h1>
        <p className="mt-1 text-muted-foreground">{t('admin:tags.subtitle')}</p>
      </div>
      <Alert tone="info">{t('admin:tags.managedByPlatform')}</Alert>
      <ApiErrorAlert error={tags.error} />
      {tags.isPending ? <InlineLoading /> : null}
      {tags.data && items.length === 0 ? (
        <Card>
          <CardContent className="py-10 text-center text-sm text-muted-foreground">{t('admin:tags.empty')}</CardContent>
        </Card>
      ) : null}
      <div className="grid gap-4 lg:grid-cols-2">
        {items.map((tag) => (
          <TagCard key={tag.id} tag={tag} branchName={nameOf(tag.branchId)}>
            <Button variant="outline" size="sm" onClick={() => setEventsOf(tag)}>
              <History className="size-4" aria-hidden="true" />
              {t('admin:tags.actions.events')}
            </Button>
          </TagCard>
        ))}
      </div>
      <Pagination state={paging} total={tags.data?.total ?? 0} />
      {eventsOf ? (
        <EventsDialog tag={eventsOf} load={(query) => tagsApi.events(eventsOf.id ?? '', query)} onClose={() => setEventsOf(undefined)} />
      ) : null}
    </div>
  );
}
