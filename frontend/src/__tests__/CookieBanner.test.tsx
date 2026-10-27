import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { CookieBanner } from '../components/CookieBanner';
import { analyticsAllowed, readConsent } from '../lib/consent';

const setup = () => render(<MemoryRouter><CookieBanner /></MemoryRouter>);

describe('CookieBanner', () => {
  it('shows until a choice is made, and rejecting keeps optional cookies off', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(readConsent()).toBe('rejected');
    expect(analyticsAllowed()).toBe(false);
  });

  it('stays hidden once a valid choice exists', () => {
    window.localStorage.setItem('fp-cookie-consent', JSON.stringify({ choice: 'accepted', version: 1 }));
    setup();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(analyticsAllowed()).toBe(true);
  });

  it('re-prompts when the stored consent is from an older policy version', () => {
    window.localStorage.setItem('fp-cookie-consent', JSON.stringify({ choice: 'accepted', version: 0 }));
    setup();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });
});
