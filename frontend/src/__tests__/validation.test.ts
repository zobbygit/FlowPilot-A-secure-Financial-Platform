import { passwordSchema, registerSchema, loginSchema } from '../lib/validation';

describe('passwordSchema', () => {
  it.each([['short1A!'], ['alllowercaseletters'], ['NOLOWERCASEHERE']])('rejects %s', (pw) => {
    expect(passwordSchema.safeParse(pw).success).toBe(false);
  });
  it('accepts a long mixed password', () => {
    expect(passwordSchema.safeParse('Tr4vel-Light-Orbit!').success).toBe(true);
  });
});

describe('registerSchema', () => {
  const valid = { fullName: 'Ana Diaz', email: 'ana@example.com', password: 'Tr4vel-Light-Orbit!', confirmPassword: 'Tr4vel-Light-Orbit!', acceptTerms: true };
  it('accepts valid input', () => expect(registerSchema.safeParse(valid).success).toBe(true));
  it('flags a mismatched confirmation on confirmPassword', () => {
    const r = registerSchema.safeParse({ ...valid, confirmPassword: 'different' });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues[0].path).toEqual(['confirmPassword']);
  });
  it('requires terms acceptance', () => expect(registerSchema.safeParse({ ...valid, acceptTerms: false }).success).toBe(false));
});

describe('loginSchema', () => {
  it('rejects malformed email', () => expect(loginSchema.safeParse({ email: 'nope', password: 'x' }).success).toBe(false));
});
