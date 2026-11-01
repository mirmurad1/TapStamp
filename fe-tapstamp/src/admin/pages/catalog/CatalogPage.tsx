import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, BookOpen, EyeOff, ImageIcon, Pencil, Plus, Trash2 } from 'lucide-react';
import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { resolveText } from '@/shared/i18n/localized';
import { currentLanguage } from '@/shared/i18n/setup';
import { formatCurrency } from '@/shared/lib/format';
import { Alert } from '@/shared/ui/alert';
import { Badge } from '@/shared/ui/badge';
import { Button } from '@/shared/ui/button';
import { Card, CardContent } from '@/shared/ui/card';
import { useConfirm } from '@/shared/ui/ConfirmProvider';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';
import { Input, Select } from '@/shared/ui/input';

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
  return (
    <Card>
      <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-end sm:justify-between sm:p-6">
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
      </CardContent>
      <ApiErrorAlert error={save.error} />
    </Card>
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
  const details = [
    item.price == null ? null : formatCurrency(Number(item.price), lang),
    item.durationMinutes ? t('admin:catalog.minutes', { count: item.durationMinutes }) : null,
  ].filter(Boolean);
  const branch = item.branchId ? nameOf(item.branchId) : undefined;
  return (
    <li className="flex flex-col gap-2 px-5 py-3 sm:px-6">
      {/* Telefonda düymələr mətnin altında bir sırada; geniş ekranda sağda */}
      <div className="flex flex-wrap items-start gap-x-3 gap-y-1 sm:flex-nowrap">
        <Thumbnail url={item.imageUrl} />
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
  const pick = (picked: File | undefined) => {
    const ok = !picked || ((picked.type === 'image/png' || picked.type === 'image/jpeg') && picked.size <= IMAGE_MAX_BYTES);
    setFileError(!ok);
    setFile(ok ? picked : undefined);
    if (picked && ok) {
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
                  <input type="file" accept="image/png,image/jpeg" className="sr-only"
                    onChange={(event) => pick(event.target.files?.[0])} />
                </label>
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
            <Button type="submit" disabled={save.isPending || !name.az.trim() || !priceValid || !durationValid}>
              {t('common:actions.save')}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
