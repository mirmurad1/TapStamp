import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import * as React from 'react';

import { cn } from '@/shared/lib/cn';

// Uzun (məs. rus dilində) mətnlər sətrə keçə bilsin deyə whitespace-nowrap yoxdur; minimum toxunma sahəsi 44px
const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-full px-5 py-2 text-center text-sm font-medium leading-snug transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground hover:bg-primary/90',
        accent: 'bg-accent text-accent-foreground hover:bg-accent/90',
        outline: 'border border-border bg-card text-card-foreground hover:bg-muted',
        ghost: 'text-foreground hover:bg-muted',
      },
      size: {
        default: 'min-h-11',
        sm: 'min-h-9 px-3 text-xs',
        lg: 'min-h-12 px-6 text-base',
        icon: 'size-11 px-0 py-0',
      },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, type, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button';
    return (
      <Comp
        ref={ref}
        type={asChild ? undefined : (type ?? 'button')}
        className={cn(buttonVariants({ variant, size }), className)}
        {...props}
      />
    );
  },
);
Button.displayName = 'Button';
