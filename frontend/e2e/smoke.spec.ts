import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => { await page.addInitScript(() => { sessionStorage.setItem('fp-curtain-seen', '1'); localStorage.setItem('fp-cookie-consent', JSON.stringify({ choice: 'rejected', version: 1 })); }); });

test('landing page renders its story and works without WebGL', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('everything money');
  expect(await page.getByRole('heading', { level: 2 }).count()).toBeGreaterThanOrEqual(12);
});

test('theme toggle switches and persists', async ({ page }) => {
  await page.goto('/');
  const html = page.locator('html');
  const before = await html.getAttribute('class');
  await page.getByRole('button', { name: /switch to (dark|light) theme/i }).first().click();
  expect(await html.getAttribute('class')).not.toBe(before);
  await page.reload();
  expect(await html.getAttribute('class')).not.toBe(before);
});

test('registration validates before sending anything', async ({ page }) => {
  await page.goto('/user/register');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByText('Enter your full name.')).toBeVisible();
  await expect(page.getByText('You must accept the Terms of Service and Privacy Policy.')).toBeVisible();
});

test('protected pages send anonymous visitors to login', async ({ page }) => {
  for (const [path, login] of [['/app/dashboard', '/user/login'], ['/admin', '/admin/login']] as const) {
    await page.goto(path);
    await expect(page).toHaveURL(new RegExp(login));
  }
});

test('legal pages exist and the cookie banner is dismissible', async ({ page }) => {
  await page.addInitScript(() => localStorage.removeItem('fp-cookie-consent'));
  await page.goto('/legal/privacy');
  await expect(page.getByRole('heading', { name: 'Privacy Policy' })).toBeVisible();
  await page.getByRole('button', { name: 'Reject' }).click();
  await expect(page.getByRole('dialog', { name: 'Cookie preferences' })).toHaveCount(0);
});
