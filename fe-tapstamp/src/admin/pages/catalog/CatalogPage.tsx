import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ArrowDown,
  ArrowUp,
  BookOpen,
  Download,
  ExternalLink,
  EyeOff,
  ImageIcon,
  Pencil,
  Plus,
  QrCode as QrCodeIcon,
  Trash2,
} from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { companyToday, formatCurrency, formatDayMonth } from '@/shared/lib/format';
import { prepareImage } from '@/shared/lib/image';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';
import { QrCode } from '@/shared/ui/QrCode';

import {
  CATALOG_KEY,
  catalogApi,
  type CatalogCategory,
  type CatalogItem,
  type CatalogTitle,
  type CatalogView,
  type Localized,
} from '../../api/adminApi';
import { ApiErrorAlert } from '../../components/ApiErrorAlert';
import { InlineLoading } from '../../components/FullPageState';
import { useBranchNames } from '../../lib/branches';
import { toLocalized } from '../../lib/localized';
import { LocalizedFields } from '../settings/CompanyForm';

const TITLES: readonly CatalogTitle[] = ['MENU', 'SERVICES', 'PRICES'];
const MAX_CATEGORIES = 30;
const MAX_ITEMS = 300;
/** Backend-in həddi ilə eyni: PNG və ya JPEG, 2 MB-a qədər. */
const IMAGE_MAX_BYTES = 2 * 1024 * 1024;
/** Qiymət: 0–100000, ən çox iki onluq (vergül də qəbul olunur). */
const PRICE = /^\d{1,6}([.,]\d{1,2})?$/;
const DATE_INPUT = 'min-w-0 appearance-none [&::-webkit-date-and-time-value]:text-left';
/** Sətirdəki kiçik ikon düymələri (40 px — barmaqla toxunmaq üçün kifayətdir, sətri hündür etmir). */
const ICON_BUTTON = 'size-10';

type Editing =
  | { kind: 'category'; category?: CatalogCategory }
  | { kind: 'item'; categoryId: string; item?: CatalogItem };

/**
 * Menyu və ya xidmətlər (şirkət sahibi, əlavə CATALOG): bölmənin açarı və adı, kateqoriyalar və onların məhsulları.
 * Sıra yuxarı/aşağı düymələri ilə dəyişir (telefonda da rahatdır); hər dəyişiklik bütün kataloqu yeniləyir.
 */
export function CatalogPage() {
  const { t } = useTranslation('admin');
  const catalog = useQuery({ queryKey: CATALOG_KEY, queryFn: catalogApi.get });
  const [editing, setEditing] = useState<Editing>();
  const categories = catalog.data?.categories ?? [];
  const itemCount = categories.reduce((sum, category) => sum + (category.items?.length ?? 0), 0);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-2xl font-semibold">{t('catalog.title')}</h1>
          <p className="mt-1 max-w-2xl text-muted-foreground">{t('catalog.subtitle')}</p>
        </div>
        {categories.length > 0 ? (
          <Button onClick={() => setEditing({ kind: 'category' })} disabled={categories.length >= MAX_CATEGORIES}>
            <Plus className="size-4" aria-hidden="true" />
            {t('catalog.addCategory')}
          </Button>
        ) : null}
      </div>
      <ApiErrorAlert error={catalog.error} />
      {catalog.isPending ? <InlineLoading /> : null}
      {catalog.data ? <SettingsCard data={catalog.data} /> : null}
      {catalog.data && categories.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-start gap-3 py-8">
            <BookOpen className="size-6 text-primary" aria-hidden="true" />
            <p className="font-medium">{t('catalog.empty.title')}</p>
            <p className="max-w-xl text-sm text-muted-foreground">{t('catalog.empty.hint')}</p>
            <Button onClick={() => setEditing({ kind: 'category' })}>
              <Plus className="size-4" aria-hidden="true" />
              {t('catalog.addCategory')}
            </Button>
          </CardContent>
        </Card>
      ) : null}
      {categories.map((category, index) => (
        <CategoryCard
          key={category.id}
          category={category}
          index={index}
          order={categories.map((c) => c.id ?? '')}
          canAddItem={itemCount < MAX_ITEMS}
          onEdit={(target) => setEditing(target)}
        />
      ))}
      {categories.length > 0 ? (
        <p className="text-xs text-muted-foreground">{t('catalog.limits', { categories: MAX_CATEGORIES, items: MAX_ITEMS })}</p>
      ) : null}
      {editing?.kind === 'category' ? (
        <CategoryDialog category={editing.category} onClose={() => setEditing(undefined)} />
      ) : null}
      {editing?.kind === 'item' && catalog.data ? (
        <ItemDialog
          categories={categories}
          categoryId={editing.categoryId}
          item={editing.item}
          onClose={() => setEditing(undefined)}
        />
      ) : null}
    </div>
  );
}

function useCatalogMutation<T>(fn: (input: T) => Promise<CatalogView>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn: fn, onSuccess: (data) => queryClient.setQueryData(CATALOG_KEY, data) });
}

/** Bölmənin açarı (söndürüləndə müştəri görmür) və müştəriyə görünən adı. */
function SettingsCard({ data }: { data: CatalogView }) {
  const { t } = useTranslation('admin');
  const save = useCatalogMutation(({ enabled, title }: { enabled: boolean; title: CatalogTitle }) =>
    catalogApi.settings(enabled, title));
  const enabled = data.enabled ?? true;
  const title = (data.title ?? 'MENU') as CatalogTitle;
  const [qrOpen, setQrOpen] = useState(false);
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-5 sm:p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <label className="flex items-start gap-3 text-sm">
          <input
            type="checkbox"
            checked={enabled}
            disabled={save.isPending}
            onChange={(event) => save.mutate({ enabled: event.target.checked, title })}
            className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]"
          />
          <span>
            <span className="block font-medium">{t('catalog.settings.enabled')}</span>
            <span className="block text-muted-foreground">
              {enabled ? t('catalog.settings.enabledHint') : t('catalog.settings.disabledHint')}
            </span>
          </span>
        </label>
        <Field id="catalog-title" label={t('catalog.settings.titleLabel')} className="sm:w-56">
          <Select value={title} disabled={save.isPending} onChange={(event) => save.mutate({ enabled, title: event.target.value as CatalogTitle })}>
            {TITLES.map((value) => (
              <option key={value} value={value}>
                {t(`catalog.titles.${value}`)}
              </option>
            ))}
          </Select>
        </Field>
        </div>
        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
          <Button variant="outline" size="sm" onClick={() => setQrOpen(true)}>
            <QrCodeIcon className="size-4" aria-hidden="true" />
            {t('catalog.qr.open')}
          </Button>
          <Button asChild variant="ghost" size="sm">
            <a href="/menu" target="_blank" rel="noopener">
              <ExternalLink className="size-4" aria-hidden="true" />
              {t('catalog.qr.preview')}
            </a>
          </Button>
        </div>
      </CardContent>
      <ApiErrorAlert error={save.error} />
      {qrOpen ? <MenuQrDialog onClose={() => setQrOpen(false)} /> : null}
    </Card>
  );
}

/**
 * Stol üçün QR kod: müştəri telefonla skan edib menyunu açır (giriş tələb etmir). Filial seçilərsə, menyu yalnız o
 * filialın məhsulları ilə açılır. SVG yüklənir — çapda keyfiyyət itmir.
 */
function MenuQrDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const { branches } = useBranchNames();
  const [branchId, setBranchId] = useState('');
  const url = `${window.location.origin}/menu${branchId ? `?branch=${branchId}` : ''}`;
  const qrRef = useRef<HTMLDivElement>(null);
  const download = () => {
    const svg = qrRef.current?.querySelector('svg');
    if (!svg) {
      return;
    }
    const blob = new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'menu-qr.svg';
    link.click();
    URL.revokeObjectURL(link.href);
  };
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-sm">
        <DialogHeader>
          <DialogTitle>{t('admin:catalog.qr.title')}</DialogTitle>
          <DialogDescription>{t('admin:catalog.qr.hint')}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col items-center gap-3">
          {branches.length > 1 ? (
            <Field id="qr-branch" label={t('admin:catalog.form.branch')} className="w-full">
              <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                <option value="">{t('admin:catalog.form.allBranches')}</option>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </Select>
            </Field>
          ) : null}
          <div ref={qrRef} className="w-56 rounded-xl border border-border p-2">
            <QrCode value={url} label={t('admin:catalog.qr.title')} className="block size-full" />
          </div>
          <p className="w-full text-center text-xs break-all text-muted-foreground">{url}</p>
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={onClose}>
            {t('common:actions.close')}
          </Button>
          <Button type="button" onClick={download}>
            <Download className="size-4" aria-hidden="true" />
            {t('admin:catalog.qr.download')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Siyahıda bir elementi yuxarı və ya aşağı aparır (yeni sıra). */
function moved(ids: string[], index: number, step: -1 | 1): string[] {
  const next = [...ids];
  const target = index + step;
  [next[index], next[target]] = [next[target] ?? '', next[index] ?? ''];
  return next;
}

function OrderButtons({ index, count, disabled, onMove }: {
  index: number;
  count: number;
  disabled: boolean;
  onMove: (step: -1 | 1) => void;
}) {
  const { t } = useTranslation('admin');
  return (
    <span className="flex gap-0.5">
      <Button variant="ghost" size="icon" className={ICON_BUTTON} disabled={disabled || index === 0} onClick={() => onMove(-1)}
        aria-label={t('catalog.moveUp')}>
        <ArrowUp className="size-4" aria-hidden="true" />
      </Button>
      <Button variant="ghost" size="icon" className={ICON_BUTTON} disabled={disabled || index === count - 1}
        onClick={() => onMove(1)} aria-label={t('catalog.moveDown')}>
        <ArrowDown className="size-4" aria-hidden="true" />
      </Button>
    </span>
  );
}

function CategoryCard({ category, index, order, canAddItem, onEdit }: {
  category: CatalogCategory;
  index: number;
  order: string[];
  canAddItem: boolean;
  onEdit: (target: Editing) => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const confirm = useConfirm();
  const id = category.id ?? '';
  const items = category.items ?? [];
  const reorder = useCatalogMutation((ids: string[]) => catalogApi.reorderCategories(ids));
  const remove = useCatalogMutation(() => catalogApi.deleteCategory(id));
  const onDelete = async () => {
    if (await confirm({ message: t('admin:catalog.deleteCategoryConfirm'), danger: true, confirmLabel: t('common:actions.delete') })) {
      remove.mutate(undefined);
    }
  };
  return (
    <Card>
      <div className="flex flex-wrap items-center gap-3 border-b border-border px-5 py-3 sm:px-6">
        <div className="min-w-0 flex-1">
          <h2 className="flex flex-wrap items-center gap-2 font-semibold">
            <span className="break-words">{resolveText(category.name, lang)}</span>
            {category.active === false ? (
              <Badge>
                <EyeOff className="size-3" aria-hidden="true" />
                {t('admin:catalog.hidden')}
              </Badge>
            ) : null}
          </h2>
          <p className="text-sm text-muted-foreground">{t('admin:catalog.itemCount', { count: items.length })}</p>
        </div>
        <span className="flex items-center gap-0.5">
          <OrderButtons index={index} count={order.length} disabled={reorder.isPending}
            onMove={(step) => reorder.mutate(moved(order, index, step))} />
          <Button variant="ghost" size="icon" className={ICON_BUTTON} onClick={() => onEdit({ kind: 'category', category })}
            aria-label={t('common:actions.edit')}>
            <Pencil className="size-4" aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon" className={ICON_BUTTON} onClick={() => void onDelete()}
            disabled={remove.isPending} aria-label={t('common:actions.delete')}>
            <Trash2 className="size-4" aria-hidden="true" />
          </Button>
        </span>
      </div>
      <ApiErrorAlert error={remove.error ?? reorder.error} />
      <CardContent className="flex flex-col p-0">
        {items.length === 0 ? (
          <p className="px-5 py-4 text-sm text-muted-foreground sm:px-6">{t('admin:catalog.emptyCategory')}</p>
        ) : (
          <ul className="divide-y divide-border">
            {items.map((item, itemIndex) => (
              <ItemRow
                key={item.id}
                item={item}
                index={itemIndex}
                order={items.map((i) => i.id ?? '')}
                categoryId={id}
                onEdit={() => onEdit({ kind: 'item', categoryId: id, item })}
              />
            ))}
          </ul>
        )}
        <div className="border-t border-border px-5 py-3 sm:px-6">
          <Button variant="outline" size="sm" disabled={!canAddItem} onClick={() => onEdit({ kind: 'item', categoryId: id })}>
            <Plus className="size-4" aria-hidden="true" />
            {t('admin:catalog.addItem')}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function ItemRow({ item, index, order, categoryId, onEdit }: {
  item: CatalogItem;
  index: number;
  order: string[];
  categoryId: string;
  onEdit: () => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const confirm = useConfirm();
  const { nameOf } = useBranchNames();
  const reorder = useCatalogMutation((ids: string[]) => catalogApi.reorderItems(categoryId, ids));
  const remove = useCatalogMutation(() => catalogApi.deleteItem(item.id ?? ''));
  const onDelete = async () => {
    if (await confirm({ message: t('admin:catalog.deleteItemConfirm'), danger: true, confirmLabel: t('common:actions.delete') })) {
      remove.mutate(undefined);
    }
  };
  const discount = item.discount;
  const details = [
    item.price == null ? null : formatCurrency(Number(discount?.status === 'ACTIVE' ? discount.salePrice : item.price), lang),
    item.durationMinutes ? t('admin:catalog.minutes', { count: item.durationMinutes }) : null,
  ].filter(Boolean);
  const branch = item.branchId ? nameOf(item.branchId) : undefined;
  return (
    <li className="flex flex-col gap-2 px-5 py-3 sm:px-6">
      {/* Telefonda düymələr mətnin altında bir sırada; geniş ekranda sağda */}
      <div className="flex flex-wrap items-start gap-x-3 gap-y-1 sm:flex-nowrap">
        <Thumbnail url={item.thumbUrl ?? item.imageUrl} />
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2 font-medium">
            <span className="break-words">{resolveText(item.name, lang)}</span>
            {item.available === false ? <Badge tone="warning">{t('admin:catalog.soldOut')}</Badge> : null}
            {branch ? <Badge>{branch}</Badge> : null}
          </p>
          {item.description ? (
            <p className="line-clamp-2 text-sm text-muted-foreground">{resolveText(item.description, lang)}</p>
          ) : null}
          {details.length > 0 ? <p className="mt-0.5 text-sm font-semibold tabular-nums">{details.join(' · ')}</p> : null}
          {discount && item.price != null ? <DiscountSummary price={Number(item.price)} discount={discount} /> : null}
        </div>
        <span className="flex w-full shrink-0 items-center justify-end gap-0.5 sm:w-auto">
          <OrderButtons index={index} count={order.length} disabled={reorder.isPending}
            onMove={(step) => reorder.mutate(moved(order, index, step))} />
          <Button variant="ghost" size="icon" className={ICON_BUTTON} onClick={onEdit} aria-label={t('common:actions.edit')}>
            <Pencil className="size-4" aria-hidden="true" />
          </Button>
          <Button variant="ghost" size="icon" className={ICON_BUTTON} onClick={() => void onDelete()}
            disabled={remove.isPending} aria-label={t('common:actions.delete')}>
            <Trash2 className="size-4" aria-hidden="true" />
          </Button>
        </span>
      </div>
      <ApiErrorAlert error={remove.error ?? reorder.error} />
    </li>
  );
}

type CatalogDiscount = NonNullable<CatalogItem['discount']>;

/** Siyahıda endirim: "−20% · köhnə qiymət · 31 dekabr tarixinədək" və vəziyyəti (planlaşdırılıb, bitib). */
function DiscountSummary({ price, discount }: { price: number; discount: CatalogDiscount }) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const period = discount.status === 'SCHEDULED' && discount.startsOn
    ? t('catalog.discount.startsOn', { date: formatDayMonth(discount.startsOn, lang) })
    : discount.endsOn
      ? t('catalog.discount.until', { date: formatDayMonth(discount.endsOn, lang) })
      : t('catalog.discount.forever');
  return (
    <p className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
      <Badge tone={discount.status === 'ACTIVE' ? 'danger' : 'neutral'}>
        {t('catalog.discount.off', { percent: discount.percent ?? 0 })}
      </Badge>
      {discount.status === 'ACTIVE' ? <s className="text-muted-foreground tabular-nums">{formatCurrency(price, lang)}</s> : null}
      <span className="text-muted-foreground">{period}</span>
      {discount.status !== 'ACTIVE' ? (
        <Badge tone={discount.status === 'EXPIRED' ? 'warning' : 'neutral'}>{t(`catalog.discount.status.${discount.status ?? 'ACTIVE'}`)}</Badge>
      ) : null}
    </p>
  );
}

function Thumbnail({ url, className }: { url?: string | null; className?: string }) {
  return url ? (
    <img src={url} alt="" className={className ?? 'size-14 shrink-0 rounded-xl object-cover ring-1 ring-border'} />
  ) : (
    <span className={className ?? 'grid size-14 shrink-0 place-items-center rounded-xl bg-muted text-muted-foreground'}>
      <ImageIcon className="size-5" aria-hidden="true" />
    </span>
  );
}

function CategoryDialog({ category, onClose }: { category?: CatalogCategory; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const [name, setName] = useState<Localized>({ az: category?.name?.az ?? '', en: category?.name?.en, ru: category?.name?.ru });
  const [active, setActive] = useState(category?.active ?? true);
  const save = useCatalogMutation(() => {
    const body = toLocalized(name) ?? { az: name.az };
    return category?.id ? catalogApi.updateCategory(category.id, body, active) : catalogApi.createCategory(body, active);
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate(undefined, { onSuccess: onClose });
  };
  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>{category ? t('admin:catalog.editCategory') : t('admin:catalog.newCategory')}</DialogTitle>
          <DialogDescription>{t('admin:catalog.categoryHint')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={save.error} />
          <LocalizedFields id="category-name" legend={t('admin:catalog.form.categoryName')} value={name} onChange={setName}
            required maxLength={40} />
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)}
              className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
            <span>
              <span className="block font-medium">{t('admin:catalog.form.visible')}</span>
              <span className="block text-muted-foreground">{t('admin:catalog.form.visibleHint')}</span>
            </span>
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending || !name.az.trim()}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function ItemDialog({ categories, categoryId, item, onClose }: {
  categories: CatalogCategory[];
  categoryId: string;
  item?: CatalogItem;
  onClose: () => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const lang = currentLanguage();
  const queryClient = useQueryClient();
  const { branches } = useBranchNames();
  const [category, setCategory] = useState(item?.categoryId ?? categoryId);
  const [name, setName] = useState<Localized>({ az: item?.name?.az ?? '', en: item?.name?.en, ru: item?.name?.ru });
  const [description, setDescription] = useState<Localized>({
    az: item?.description?.az ?? '',
    en: item?.description?.en,
    ru: item?.description?.ru,
  });
  const [price, setPrice] = useState(item?.price == null ? '' : String(item.price));
  const [duration, setDuration] = useState(item?.durationMinutes ? String(item.durationMinutes) : '');
  const [available, setAvailable] = useState(item?.available ?? true);
  const [branchId, setBranchId] = useState(item?.branchId ?? '');
  const [discount, setDiscount] = useState<DiscountDraft>(() => draftOf(item?.discount));
  // Şəkil: yeni seçilən fayl, köhnənin silinməsi; önizləmə üçün brauzer ünvanı
  const [file, setFile] = useState<File>();
  const [removeImage, setRemoveImage] = useState(false);
  const [fileError, setFileError] = useState(false);
  const [preview, setPreview] = useState<string>();
  useEffect(() => {
    if (!file) {
      setPreview(undefined);
      return undefined;
    }
    const url = URL.createObjectURL(file);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const priceValid = price.trim() === '' || (PRICE.test(price.trim()) && Number(price.replace(',', '.')) <= 100000);
  const durationValid = duration.trim() === '' || (/^\d{1,4}$/.test(duration.trim()) && Number(duration) >= 1 && Number(duration) <= 1440);
  const priceNumber = price.trim() === '' || !priceValid ? undefined : Number(price.trim().replace(',', '.'));
  const discountCheck = checkDiscount(discount, priceNumber);

  const save = useMutation({
    mutationFn: async () => {
      const body = {
        categoryId: category,
        name: toLocalized(name) ?? { az: name.az },
        description: toLocalized(description),
        price: price.trim() === '' ? undefined : Number(price.trim().replace(',', '.')),
        durationMinutes: duration.trim() === '' ? undefined : Number(duration),
        available,
        branchId: branchId || undefined,
        discount: discount.enabled && discountCheck.valid ? toDiscountRequest(discount) : undefined,
      };
      let data: CatalogView;
      let itemId = item?.id;
      if (itemId) {
        data = await catalogApi.updateItem(itemId, body);
      } else {
        // Yeni məhsulun id-si — kateqoriyada əvvəl olmayan yeganə məhsul
        const before = new Set((categories.find((c) => c.id === category)?.items ?? []).map((i) => i.id));
        data = await catalogApi.createItem(body);
        itemId = (data.categories ?? []).find((c) => c.id === category)?.items?.find((i) => !before.has(i.id))?.id;
      }
      if (itemId && file) {
        data = await catalogApi.uploadImage(itemId, file);
      } else if (itemId && removeImage && item?.imageUrl) {
        data = await catalogApi.removeImage(itemId);
      }
      return data;
    },
    onSuccess: (data) => {
      queryClient.setQueryData(CATALOG_KEY, data);
      onClose();
    },
    // Məhsul yaradılıb, amma şəkil alınmayıbsa — siyahı yenilənsin
    onError: () => void queryClient.invalidateQueries({ queryKey: CATALOG_KEY }),
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };
  // İstənilən şəkil (iPhone-un HEIC-i, böyük foto) brauzerdə kiçildilib JPEG-ə çevrilir, sonra yüklənir
  const [preparing, setPreparing] = useState(false);
  const pick = async (picked: File | undefined) => {
    if (!picked) {
      return;
    }
    setPreparing(true);
    const prepared = await prepareImage(picked);
    setPreparing(false);
    const ok = prepared !== null && prepared.size <= IMAGE_MAX_BYTES;
    setFileError(!ok);
    setFile(ok ? prepared : undefined);
    if (ok) {
      setRemoveImage(false);
    }
  };
  const shownImage = preview ?? (removeImage ? undefined : item?.imageUrl ?? undefined);

  return (
    <Dialog open onOpenChange={(open) => (open ? undefined : onClose())}>
      <DialogContent className="max-h-[90vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{item ? t('admin:catalog.editItem') : t('admin:catalog.newItem')}</DialogTitle>
          <DialogDescription>{t('admin:catalog.itemHint')}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={submit}>
          <ApiErrorAlert error={save.error} />
          <Field id="item-category" label={t('admin:catalog.form.category')}>
            <Select value={category} onChange={(event) => setCategory(event.target.value)}>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {resolveText(c.name, lang)}
                </option>
              ))}
            </Select>
          </Field>
          <LocalizedFields id="item-name" legend={t('admin:catalog.form.name')} value={name} onChange={setName} required maxLength={60} />
          <LocalizedFields id="item-description" legend={t('admin:catalog.form.description')} value={description}
            onChange={setDescription} maxLength={200} />
          <div className="grid grid-cols-2 gap-3">
            <Field id="item-price" label={t('admin:catalog.form.price')} hint={t('admin:catalog.form.priceHint')}>
              <Input inputMode="decimal" value={price} onChange={(event) => setPrice(event.target.value)}
                aria-invalid={!priceValid} placeholder="4.50" />
            </Field>
            <Field id="item-duration" label={t('admin:catalog.form.duration')} hint={t('admin:catalog.form.durationHint')}>
              <Input inputMode="numeric" value={duration} onChange={(event) => setDuration(event.target.value)}
                aria-invalid={!durationValid} />
            </Field>
          </div>
          <DiscountFields value={discount} onChange={setDiscount} price={priceNumber} check={discountCheck} />
          {branches.length > 1 ? (
            <Field id="item-branch" label={t('admin:catalog.form.branch')}>
              <Select value={branchId} onChange={(event) => setBranchId(event.target.value)}>
                <option value="">{t('admin:catalog.form.allBranches')}</option>
                {branches.map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name}
                  </option>
                ))}
              </Select>
            </Field>
          ) : null}
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('admin:catalog.form.image')}</legend>
            <div className="flex items-center gap-3">
              <Thumbnail url={shownImage} className={shownImage ? 'size-20 shrink-0 rounded-xl object-cover ring-1 ring-border' : undefined} />
              <div className="flex flex-col items-start gap-2">
                <label className="cursor-pointer text-sm font-medium text-primary underline-offset-2 hover:underline">
                  {shownImage ? t('admin:catalog.form.changeImage') : t('admin:catalog.form.chooseImage')}
                  <input type="file" accept="image/*" className="sr-only"
                    onChange={(event) => {
                      void pick(event.target.files?.[0]);
                      event.target.value = '';
                    }} />
                </label>
                {preparing ? <span className="text-sm text-muted-foreground">{t('admin:catalog.form.imagePreparing')}</span> : null}
                {shownImage ? (
                  <button type="button" className="text-sm text-muted-foreground hover:text-foreground"
                    onClick={() => { setFile(undefined); setRemoveImage(true); }}>
                    {t('admin:catalog.form.removeImage')}
                  </button>
                ) : null}
              </div>
            </div>
            <p className="text-xs text-muted-foreground">{t('admin:catalog.form.imageHint')}</p>
            {fileError ? <Alert tone="error">{t('admin:catalog.form.imageInvalid')}</Alert> : null}
          </fieldset>
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" checked={!available} onChange={(event) => setAvailable(!event.target.checked)}
              className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
            <span>
              <span className="block font-medium">{t('admin:catalog.form.soldOut')}</span>
              <span className="block text-muted-foreground">{t('admin:catalog.form.soldOutHint')}</span>
            </span>
          </label>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={save.isPending || preparing || !name.az.trim() || !priceValid || !durationValid
              || (discount.enabled && !discountCheck.valid)}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

/** Formadakı endirim: faiz və ya yeni qiymət; müddət — həmişəlik və ya tarix aralığı (başlanğıc boş — bu gündən). */
type DiscountDraft = {
  enabled: boolean;
  kind: 'PERCENT' | 'PRICE';
  value: string;
  period: 'FOREVER' | 'RANGE';
  startsOn: string;
  endsOn: string;
};

function draftOf(discount: CatalogDiscount | undefined): DiscountDraft {
  return {
    enabled: Boolean(discount),
    kind: discount?.kind === 'PRICE' ? 'PRICE' : 'PERCENT',
    value: discount?.value == null ? '' : String(discount.value),
    period: discount?.endsOn || discount?.startsOn ? 'RANGE' : 'FOREVER',
    startsOn: discount?.startsOn ?? '',
    endsOn: discount?.endsOn ?? '',
  };
}

function toDiscountRequest(draft: DiscountDraft) {
  return {
    kind: draft.kind,
    value: Number(draft.value.trim().replace(',', '.')),
    startsOn: draft.period === 'RANGE' && draft.startsOn ? draft.startsOn : undefined,
    endsOn: draft.period === 'RANGE' ? draft.endsOn : undefined,
  };
}

type DiscountError = 'noPrice' | 'percent' | 'price' | 'endsOn' | 'range' | 'past';

/** Yoxlama və önizləmə: endirimli qiymət və faiz (server də eyni qaydanı tətbiq edir). */
function checkDiscount(draft: DiscountDraft, price: number | undefined): { valid: boolean; error?: DiscountError; sale?: number; percent?: number } {
  if (!draft.enabled) {
    return { valid: true };
  }
  if (price == null || price <= 0) {
    return { valid: false, error: 'noPrice' };
  }
  const raw = draft.value.trim().replace(',', '.');
  let sale: number;
  let percent: number;
  if (draft.kind === 'PERCENT') {
    if (!/^\d{1,2}$/.test(raw) || Number(raw) < 1) {
      return { valid: false, error: draft.value.trim() ? 'percent' : undefined };
    }
    percent = Number(raw);
    sale = Math.round(price * (100 - percent)) / 100;
  } else {
    if (!PRICE.test(raw) || Number(raw) >= price) {
      return { valid: false, error: draft.value.trim() ? 'price' : undefined };
    }
    sale = Number(raw);
    percent = Math.round((1 - sale / price) * 100);
  }
  if (draft.period === 'RANGE') {
    if (!draft.endsOn) {
      return { valid: false, error: 'endsOn', sale, percent };
    }
    if (draft.startsOn && draft.startsOn > draft.endsOn) {
      return { valid: false, error: 'range', sale, percent };
    }
    if (draft.endsOn < companyToday()) {
      return { valid: true, error: 'past', sale, percent };
    }
  }
  return { valid: true, sale, percent };
}

/** Endirim bölməsi: açar, növ (faiz / yeni qiymət), dəyər, önizləmə və müddət. */
function DiscountFields({ value, onChange, price, check }: {
  value: DiscountDraft;
  onChange: (next: DiscountDraft) => void;
  price: number | undefined;
  check: ReturnType<typeof checkDiscount>;
}) {
  const { t } = useTranslation('admin');
  const lang = currentLanguage();
  const set = (patch: Partial<DiscountDraft>) => onChange({ ...value, ...patch });
  const today = companyToday();
  return (
    <fieldset className="flex flex-col gap-3 rounded-2xl border border-border p-3.5">
      <label className="flex items-start gap-3 text-sm">
        <input type="checkbox" checked={value.enabled} onChange={(event) => set({ enabled: event.target.checked })}
          className="mt-0.5 size-5 shrink-0 accent-[var(--primary)]" />
        <span>
          <span className="block font-medium">{t('catalog.discount.enable')}</span>
          <span className="block text-muted-foreground">{t('catalog.discount.enableHint')}</span>
        </span>
      </label>
      {value.enabled ? (
        <>
          {price == null ? <Alert tone="info">{t('catalog.discount.errors.noPrice')}</Alert> : null}
          <div className="grid grid-cols-2 gap-3">
            <Field id="discount-kind" label={t('catalog.discount.kind')}>
              <Select value={value.kind} onChange={(event) => set({ kind: event.target.value as DiscountDraft['kind'], value: '' })}>
                <option value="PERCENT">{t('catalog.discount.kinds.PERCENT')}</option>
                <option value="PRICE">{t('catalog.discount.kinds.PRICE')}</option>
              </Select>
            </Field>
            <Field id="discount-value" label={value.kind === 'PERCENT' ? t('catalog.discount.percent') : t('catalog.discount.newPrice')}>
              <Input inputMode={value.kind === 'PERCENT' ? 'numeric' : 'decimal'} value={value.value}
                onChange={(event) => set({ value: event.target.value })}
                aria-invalid={check.error === 'percent' || check.error === 'price'}
                placeholder={value.kind === 'PERCENT' ? '20' : '3.60'} />
            </Field>
          </div>
          {check.sale != null && price != null ? (
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <span className="text-muted-foreground">{t('catalog.discount.preview')}</span>
              <span className="font-bold text-primary tabular-nums">{formatCurrency(check.sale, lang)}</span>
              <s className="text-muted-foreground tabular-nums">{formatCurrency(price, lang)}</s>
              <Badge tone="danger">{t('catalog.discount.off', { percent: check.percent ?? 0 })}</Badge>
            </p>
          ) : null}
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">{t('catalog.discount.period')}</legend>
            <div className="flex flex-wrap gap-x-5 gap-y-2 text-sm">
              {(['FOREVER', 'RANGE'] as const).map((period) => (
                <label key={period} className="flex items-center gap-2">
                  <input type="radio" name="discount-period" checked={value.period === period}
                    onChange={() => set({ period })} className="size-4 accent-[var(--primary)]" />
                  {t(`catalog.discount.periods.${period}`)}
                </label>
              ))}
            </div>
            {value.period === 'RANGE' ? (
              <div className="grid grid-cols-2 gap-3">
                <Field id="discount-starts" label={t('catalog.discount.startsOnLabel')} hint={t('catalog.discount.startsOnHint')}>
                  <Input type="date" value={value.startsOn} max={value.endsOn || undefined} className={DATE_INPUT}
                    onChange={(event) => set({ startsOn: event.target.value })} />
                </Field>
                <Field id="discount-ends" label={t('catalog.discount.endsOnLabel')} hint={t('catalog.discount.endsOnHint')}>
                  <Input type="date" value={value.endsOn} min={value.startsOn || today} className={DATE_INPUT}
                    aria-invalid={check.error === 'endsOn' || check.error === 'range'}
                    onChange={(event) => set({ endsOn: event.target.value })} />
                </Field>
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">{t('catalog.discount.foreverHint')}</p>
            )}
          </fieldset>
          {check.error && check.error !== 'noPrice' ? (
            <Alert tone={check.error === 'past' ? 'info' : 'error'}>{t(`catalog.discount.errors.${check.error}`)}</Alert>
          ) : null}
        </>
      ) : null}
    </fieldset>
  );
}
