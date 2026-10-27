import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ProtectedRoute } from '../auth/ProtectedRoute';
import type { Me } from '../lib/types';

jest.mock('../auth/AuthContext', () => ({ useAuth: jest.fn() }));
const mockAuth = useAuth as jest.Mock;
const me = (role: Me['role']): Me => ({ id: '1', email: 'a@b.com', fullName: 'A', role, emailVerified: true, avatarUrl: null, themePreference: 'system', onboardingCompleted: false });

const setup = (path: string) => render(
  <MemoryRouter initialEntries={[path]}>
    <Routes>
      <Route path="/user/login" element={<p>user login</p>} />
      <Route path="/admin/login" element={<p>admin login</p>} />
      <Route element={<ProtectedRoute role="ADMIN" />}><Route path="/admin" element={<p>admin area</p>} /></Route>
      <Route element={<ProtectedRoute role="USER" />}><Route path="/app" element={<p>user area</p>} /></Route>
    </Routes>
  </MemoryRouter>,
);

describe('ProtectedRoute', () => {
  it('sends anonymous visitors to the matching login page', () => {
    mockAuth.mockReturnValue({ me: null, isLoading: false });
    setup('/admin');
    expect(screen.getByText('admin login')).toBeInTheDocument();
  });
  it('keeps a normal user out of the admin area', () => {
    mockAuth.mockReturnValue({ me: me('USER'), isLoading: false });
    setup('/admin');
    expect(screen.getByText('user area')).toBeInTheDocument();
  });
  it('lets an admin in', () => {
    mockAuth.mockReturnValue({ me: me('ADMIN'), isLoading: false });
    setup('/admin');
    expect(screen.getByText('admin area')).toBeInTheDocument();
  });
  it('shows a status while the session is being checked', () => {
    mockAuth.mockReturnValue({ me: null, isLoading: true });
    setup('/app');
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
