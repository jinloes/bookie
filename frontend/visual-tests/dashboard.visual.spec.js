import { expect, test } from '@playwright/test';

test('dashboard visual baseline', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const url = route.request().url();
    if (
      url.includes('/api/v2/transactions') ||
      url.includes('/api/v2/inbox') ||
      url.includes('/api/properties') ||
      url.includes('/api/counterparties')
    ) {
      await route.fulfill({ json: [] });
      return;
    }
    if (url.includes('/api/outlook/status')) {
      await route.fulfill({ json: { connected: false } });
      return;
    }
    if (url.includes('/api/receipts/settings')) {
      await route.fulfill({ json: { folderBase: '' } });
      return;
    }
    await route.fulfill({ status: 204, body: '' });
  });

  await page.goto('/');
  await page.waitForLoadState('networkidle');
  await expect(page).toHaveScreenshot('dashboard.png', { fullPage: true });
});
