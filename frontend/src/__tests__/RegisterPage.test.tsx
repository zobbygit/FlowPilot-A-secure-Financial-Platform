import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { api } from '../lib/api';
import { RegisterPage } from '../pages/user/RegisterPage';

jest.mock('../lib/api', () => ({ api: { post: jest.fn() }, toApiError: (e: unknown) => e }));
const post = api.post as jest.Mock;

const setup = () => render(
  <MemoryRouter initialEntries={['/user/register']}>
    <Routes>
      <Route path="/user/register" element={<RegisterPage />} />
      <Route path="/user/check-email" element={<h1>Verify your email</h1>} />
    </Routes>
  </MemoryRouter>,
);

describe('RegisterPage', () => {
  beforeEach(() => post.mockReset());

  it('shows field errors and sends nothing when the form is empty', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Enter your full name.')).toBeInTheDocument();
    expect(screen.getByText('You must accept the Terms of Service and Privacy Policy.')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('submits valid data (without the confirmation field) and moves to the check-email screen', async () => {
    post.mockResolvedValue({ data: {} });
    setup();
    await userEvent.type(screen.getByLabelText('Full name'), 'Ana Diaz');
    await userEvent.type(screen.getByLabelText('Email address'), 'ana@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Tr4vel-Light-Orbit!');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Tr4vel-Light-Orbit!');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Verify your email' })).toBeInTheDocument());
    expect(post).toHaveBeenCalledWith('/auth/register', { fullName: 'Ana Diaz', email: 'ana@example.com', password: 'Tr4vel-Light-Orbit!', acceptTerms: true });
  });

  it('shows a server-side failure without losing the form', async () => {
    post.mockRejectedValue({ code: 'RATE_LIMITED', message: 'Too many attempts. Please wait and try again.' });
    setup();
    await userEvent.type(screen.getByLabelText('Full name'), 'Ana Diaz');
    await userEvent.type(screen.getByLabelText('Email address'), 'ana@example.com');
    await userEvent.type(screen.getByLabelText('Password'), 'Tr4vel-Light-Orbit!');
    await userEvent.type(screen.getByLabelText('Confirm password'), 'Tr4vel-Light-Orbit!');
    await userEvent.click(screen.getByRole('checkbox'));
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Too many attempts');
    expect(screen.getByLabelText('Full name')).toHaveValue('Ana Diaz');
  });
});
