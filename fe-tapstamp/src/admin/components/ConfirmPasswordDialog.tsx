import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

import { Button } from '@/shared/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog';
import { Field } from '@/shared/ui/field';

import { ApiErrorAlert } from './ApiErrorAlert';
import { PasswordInput } from './PasswordInput';

/**
 * Həssas əməliyyatdan əvvəl cari şifrəni soruşur (məs. kampaniyanı təsdiqə göndərmək). Şifrə serverdə yoxlanır —
 * səhv olarsa, xəta dialoqda göstərilir və istifadəçi yenidən cəhd edir.
 */
export function ConfirmPasswordDialog({
  open,
  title,
  description,
  confirmLabel,
  pending,
  error,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  pending: boolean;
  error: unknown;
  onConfirm: (password: string) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation(['auth', 'common']);
  const [password, setPassword] = useState('');
  const close = () => {
    setPassword('');
    onCancel();
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (password) {
      onConfirm(password);
    }
  };
  return (
    <Dialog open={open} onOpenChange={(next) => (next ? undefined : close())}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <ApiErrorAlert error={error} />
          <Field id="confirm-current-password" label={t('auth:stepUp.password')}>
            <PasswordInput
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              required
              autoFocus
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={close}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" disabled={pending || !password}>
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
