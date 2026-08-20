const { test, expect } = require('@playwright/test');
const path = require('path');

test('CHAT-2 password-confirmed account withdrawal returns to login and blocks reuse', async ({ page, request }) => {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  const email = `chat2_${suffix}@example.com`;
  const password = 'chat2-e2e-password1';
  const nickname = 'CHAT-2 검증 사용자';
  const createUser = await request.post('/api/auth/signup', {
    data: { email, nickname, password }
  });
  expect(createUser.status()).toBe(201);

  await page.goto('/');
  await page.locator('#emailInput').fill(email);
  await page.locator('#passwordInput').fill(password);
  await page.locator('#loginButton').click();
  await expect(page.locator('#loginModal')).toBeHidden();
  await expect(page.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');

  await page.locator('#withdrawButton').click();
  await expect(page.locator('#withdrawModal')).toBeVisible();
  await page.locator('#withdrawPasswordInput').fill('wrong-password');
  await page.locator('#withdrawConfirm').check();
  await page.locator('#confirmWithdrawButton').click();
  await expect(page.locator('#withdrawError')).toHaveText('비밀번호가 올바르지 않습니다.');
  expect(await page.evaluate(async () => (await fetch('/api/auth/me')).status)).toBe(200);

  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-2-withdrawal-confirmation.png'),
    fullPage: true
  });
  await page.setViewportSize({ width: 390, height: 844 });
  const mobileModal = await page.locator('#withdrawModal .modal').boundingBox();
  expect(mobileModal).toBeTruthy();
  expect(mobileModal.x).toBeGreaterThanOrEqual(0);
  expect(mobileModal.y).toBeGreaterThanOrEqual(0);
  expect(mobileModal.x + mobileModal.width).toBeLessThanOrEqual(390);
  expect(mobileModal.y + mobileModal.height).toBeLessThanOrEqual(844);
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-2-withdrawal-confirmation-mobile.png'),
    fullPage: true
  });
  await page.setViewportSize({ width: 1280, height: 720 });

  await page.locator('#withdrawPasswordInput').fill(password);
  await page.locator('#confirmWithdrawButton').click();
  await expect(page.locator('#withdrawModal')).toBeHidden();
  await expect(page.locator('#loginModal')).toBeVisible();
  await expect(page.locator('#userBadge')).toHaveText('로그인 필요');
  await expect(page.locator('#emailInput')).toHaveValue('');
  expect(await page.evaluate(async () => (await fetch('/api/auth/me')).status)).toBe(401);
  expect(await page.evaluate(() => stompClient === null)).toBe(true);

  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-2-withdrawal-success.png'),
    fullPage: true
  });

  await page.locator('#emailInput').fill(email);
  await page.locator('#passwordInput').fill(password);
  await page.locator('#loginButton').click();
  await expect(page.locator('#loginModal')).toBeVisible();
  await expect(page.locator('#loginError')).toHaveText('이메일 또는 비밀번호가 올바르지 않습니다.');
});
