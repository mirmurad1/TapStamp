import type { Localized } from '../api/adminApi';

/** Formadakı 3 dilli mətni API formatına çevirir: az boşdursa — yoxdur; boş tərcümələr göndərilmir. */
export function toLocalized(value: Localized | undefined): Localized | undefined {
  if (!value?.az?.trim()) {
    return undefined;
  }
  return { az: value.az.trim(), en: value.en?.trim() || undefined, ru: value.ru?.trim() || undefined };
}
