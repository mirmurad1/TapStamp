import * as DialogPrimitive from '@radix-ui/react-dialog';
import { ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';

import { cn } from '@/shared/lib/cn';

interface SheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  /** Başlığın üstündəki ikon (brend rənginin açıq fonunda). */
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
  /** Açılanda fokus avtomatik daxili düyməyə keçməsin (məs. örtük altındakı "Kodu aç" düyməsi görünməsin). */
  preventAutoFocus?: boolean;
}

/**
 * Aşağıdan qalxan vərəq (bottom sheet): telefonda dialoqdan daha təbiidir, arxadakı səhifə görünür qalır.
 * Radix Dialog üzərindədir — fokus içəridə saxlanılır, Esc və fona klik bağlayır.
 */
export function Sheet({ open, onOpenChange, title, description, icon, children, className, preventAutoFocus }: SheetProps) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="animate-sheet-fade fixed inset-0 z-40 bg-black/45 backdrop-blur-[2px]" />
        <DialogPrimitive.Content
          onOpenAutoFocus={preventAutoFocus ? (event) => event.preventDefault() : undefined}
          className={cn(
            'animate-sheet-up fixed inset-x-0 bottom-0 z-50 mx-auto flex max-h-[92dvh] w-full max-w-md flex-col gap-3 overflow-y-auto',
            'rounded-t-[28px] bg-card px-5 pt-2.5 pb-[max(1.5rem,env(safe-area-inset-bottom))] text-center text-card-foreground shadow-[0_-20px_40px_rgb(0_0_0/0.15)]',
            'focus-visible:outline-none',
            className,
          )}
        >
          <span className="mx-auto mb-1 h-1 w-10 shrink-0 rounded-full bg-border" aria-hidden="true" />
          {icon ? (
            <span className="mx-auto grid size-13 place-items-center rounded-2xl bg-primary/10 text-primary [&_svg]:size-6" aria-hidden="true">
              {icon}
            </span>
          ) : null}
          <DialogPrimitive.Title className="text-xl font-extrabold tracking-tight">{title}</DialogPrimitive.Title>
          {description ? (
            <DialogPrimitive.Description className="text-sm text-muted-foreground">{description}</DialogPrimitive.Description>
          ) : (
            <DialogPrimitive.Description className="sr-only">{title}</DialogPrimitive.Description>
          )}
          {children}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
}

/** Ana səhifədəki təklif siyahısının sətri (dəvət, bildirişlər, quraşdırma): klik vərəqi açır. */
export function OfferRow({ icon, title, hint, onClick }: { icon: ReactNode; title: string; hint: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center gap-3 px-4 py-3.5 text-left transition-colors hover:bg-muted/60 focus-visible:bg-muted/60 focus-visible:outline-none"
    >
      <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary/10 text-primary [&_svg]:size-5" aria-hidden="true">
        {icon}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-semibold">{title}</span>
        <span className="block text-[13px] leading-snug text-muted-foreground">{hint}</span>
      </span>
      <ChevronRight className="size-4 shrink-0 text-muted-foreground/60" aria-hidden="true" />
    </button>
  );
}
