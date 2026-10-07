import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeAll, describe, expect, it } from 'vitest';

import { adminI18n } from '@/admin/i18n';

import { ConfirmProvider, useConfirm } from './ConfirmProvider';

function Harness() {
  const confirm = useConfirm();
  const [result, setResult] = useState<string>('—');
  return (
    <>
      <button type="button" onClick={() => void confirm({ message: 'Kart sıfırlansın?', danger: true }).then((ok) => setResult(String(ok)))}>
        sına
      </button>
      <output>{result}</output>
    </>
  );
}

describe('təsdiq dialoqu (window.confirm əvəzinə)', () => {
  beforeAll(async () => {
    await adminI18n.changeLanguage('az');
  });

  it('tətbiqin dialoqunda göstərilir: Ləğv et — false, Davam et — true', async () => {
    const user = userEvent.setup();
    render(
      <ConfirmProvider>
        <Harness />
      </ConfirmProvider>,
    );

    await user.click(screen.getByRole('button', { name: 'sına' }));
    expect(await screen.findByRole('dialog', { name: 'Təsdiq edin' })).toHaveTextContent('Kart sıfırlansın?');
    await user.click(screen.getByRole('button', { name: 'Ləğv et' }));
    expect(await screen.findByText('false')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'sına' }));
    await user.click(await screen.findByRole('button', { name: 'Davam et' }));
    expect(await screen.findByText('true')).toBeInTheDocument();
  });
});
