import { Eye, EyeOff } from 'lucide-react';
import * as React from 'react';
import { useTranslation } from 'react-i18next';

import { Input } from '@/shared/ui/input';

/** Şifrə sahəsi: göstər/gizlət düyməsi ilə (mobil cihazda yazı səhvlərini azaldır). */
export const PasswordInput = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  (props, ref) => {
    const { t } = useTranslation('auth');
    const [visible, setVisible] = React.useState(false);
    return (
      <div className="relative">
        <Input ref={ref} type={visible ? 'text' : 'password'} className="pr-12" {...props} />
        <button
          type="button"
          onClick={() => setVisible((value) => !value)}
          aria-label={visible ? t('admin.hidePassword') : t('admin.showPassword')}
          aria-pressed={visible}
          className="absolute inset-y-0 right-1 my-auto grid size-10 place-items-center rounded-lg text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {visible ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
        </button>
      </div>
    );
  },
);
PasswordInput.displayName = 'PasswordInput';
