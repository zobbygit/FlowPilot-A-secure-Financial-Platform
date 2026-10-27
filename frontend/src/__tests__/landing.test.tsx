import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LandingPage } from '../landing/LandingPage';
import { ThemeProvider } from '../theme/ThemeProvider';

jest.mock('../landing/SceneHost', () => ({ SceneHost: () => <div data-testid="scene" />, FallbackOrb: () => <div /> }));

const setup = () => render(<ThemeProvider><MemoryRouter><LandingPage /></MemoryRouter></ThemeProvider>);

describe('Landing page', () => {
  beforeEach(() => window.sessionStorage.clear());

  it('tells the story in at least 12 sections plus a footer', () => {
    setup();
    expect(screen.getAllByRole('heading', { level: 2 }).length).toBeGreaterThanOrEqual(12);
    expect(screen.getByRole('contentinfo')).toBeInTheDocument();
  });

  it('can always be skipped: the curtain has a Skip button that removes it', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Skip intro' }));
    expect(screen.queryByRole('button', { name: 'Skip intro' })).not.toBeInTheDocument();
    expect(window.sessionStorage.getItem('fp-curtain-seen')).toBe('1');
  });

  it('does not replay the curtain within the same session', () => {
    window.sessionStorage.setItem('fp-curtain-seen', '1');
    setup();
    expect(screen.queryByRole('button', { name: 'Skip intro' })).not.toBeInTheDocument();
  });

  it('footer links lead only to real routes and in-page anchors (no fake social links)', () => {
    window.sessionStorage.setItem('fp-curtain-seen', '1');
    setup();
    const links = within(screen.getByRole('contentinfo')).getAllByRole('link').map((a) => a.getAttribute('href') ?? '');
    expect(links.length).toBeGreaterThan(8);
    links.forEach((h) => expect(h).toMatch(/^(#|\/(user|legal|app)\/)/));
    expect(links.some((h) => /twitter|facebook|instagram|linkedin|x\.com/i.test(h))).toBe(false);
  });

  it('labels product previews as sample data and describes simulated money honestly', () => {
    window.sessionStorage.setItem('fp-curtain-seen', '1');
    setup();
    expect(screen.getAllByText('Sample data').length).toBeGreaterThan(3);
    expect(screen.getByText(/never move money/i)).toBeInTheDocument();
  });
});
