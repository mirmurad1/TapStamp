import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it } from 'vitest';

import { adminI18n } from '@/admin/i18n';

import { Pagination } from './pagination';
import { usePagination } from './usePagination';

function Harness({ total }: { total: number }) {
  const paging = usePagination();
  return (
    <>
      <output>{`${paging.page}/${paging.size}`}</output>
      <Pagination state={paging} total={total} />
    </>
  );
}

describe('səhifələmə', () => {
  beforeAll(async () => {
    await adminI18n.changeLanguage('az');
  });

  it('default 10 sətir; səhifələr arası keçid və ölçü dəyişəndə birinci səhifəyə qayıdış', async () => {
    const user = userEvent.setup();
    render(<Harness total={57} />);

    expect(screen.getByText('1–10 / 57')).toBeInTheDocument();
    expect(screen.getByText('0/10')).toBeInTheDocument();
    // 6 səhifə: 1, 2, …, 6 (cari səhifənin qonşuları və son səhifə)
    expect(screen.getByRole('button', { name: 'Səhifə 6' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Əvvəlki' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Səhifə 6' }));
    expect(screen.getByText('51–57 / 57')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Növbəti' })).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Əvvəlki' }));
    expect(screen.getByText('41–50 / 57')).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Səhifədə'), '20');
    expect(screen.getByText('0/20')).toBeInTheDocument();
    expect(screen.getByText('1–20 / 57')).toBeInTheDocument();
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['5', '10', '20', '50']);
  });

  it('sətir yoxdursa göstərilmir; bir səhifəlik siyahıda keçid düymələri yoxdur', () => {
    const { rerender } = render(<Harness total={0} />);
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();

    rerender(<Harness total={7} />);
    expect(screen.getByText('1–7 / 7')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Növbəti' })).not.toBeInTheDocument();
  });
});
