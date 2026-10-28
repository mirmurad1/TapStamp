import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { TimeSelect } from './time-select';

describe('TimeSelect', () => {
  it('offers 24 hours without AM/PM and keeps the other part when one changes', () => {
    const onChange = vi.fn();
    render(<TimeSelect value="14:30" onChange={onChange} label="Start" />);
    const [hours, minutes] = screen.getAllByRole('combobox') as HTMLSelectElement[];
    expect(hours?.options).toHaveLength(24);
    expect(hours?.options[23]?.value).toBe('23');
    expect(screen.queryByText(/AM|PM/)).toBeNull();

    fireEvent.change(hours!, { target: { value: '17' } });
    expect(onChange).toHaveBeenLastCalledWith('17:30');
    fireEvent.change(minutes!, { target: { value: '05' } });
    expect(onChange).toHaveBeenLastCalledWith('14:05');
  });

  it('keeps an existing minute that is not on the 5-minute step', () => {
    render(<TimeSelect value="09:07" onChange={() => undefined} label="Opens" />);
    const minutes = screen.getAllByRole('combobox')[1] as HTMLSelectElement;
    expect(minutes.value).toBe('07');
  });
});
