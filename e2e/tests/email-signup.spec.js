const { test, expect } = require('@playwright/test');
const path = require('path');

function uniqueAccount(prefix) {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  return {
    email: `${prefix}_${suffix}@example.com`,
    password: 'SignupE2E123'
  };
}

async function openSignup(page) {
  await page.goto('/');
  await page.locator('#signupTab').click();
  await expect(page.locator('#signupPanel')).toBeVisible();
  await expect(page.locator('#loginPanel')).toBeHidden();
}

async function fillSignup(page, { email, nickname, password, passwordConfirm = password }) {
  await page.locator('#signupEmailInput').fill(email);
  await page.locator('#signupNicknameInput').fill(nickname);
  await page.locator('#signupPasswordInput').fill(password);
  await page.locator('#signupPasswordConfirmInput').fill(passwordConfirm);
}

test('shows the email signup form on desktop', async ({ page }) => {
  await openSignup(page);

  await expect(page.locator('#signupEmailInput')).toHaveAttribute('type', 'email');
  await expect(page.locator('#signupNicknameInput')).toBeVisible();
  await expect(page.locator('#signupPasswordInput')).toBeVisible();
  await expect(page.locator('#signupPasswordConfirmInput')).toBeVisible();
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-form-desktop.png'),
    fullPage: true
  });
});

test('shows field validation errors without overflowing desktop or mobile', async ({ page }) => {
  await openSignup(page);
  await fillSignup(page, {
    email: 'invalid@',
    nickname: 'A',
    password: 'onlyletters'
  });
  await page.locator('#signupButton').click();

  await expect(page.locator('#signupEmailError')).not.toBeEmpty();
  await expect(page.locator('#signupNicknameError')).not.toBeEmpty();
  await expect(page.locator('#signupPasswordError')).not.toBeEmpty();
  await page.locator('#loginModal .auth-modal').evaluate(modal => modal.scrollTop = 0);
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-validation-desktop-top.png'),
    fullPage: true
  });
  await page.locator('#loginModal .auth-modal').evaluate(modal => modal.scrollTop = modal.scrollHeight);
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-validation-desktop-bottom.png'),
    fullPage: true
  });

  await page.setViewportSize({ width: 390, height: 844 });
  const mobileModal = await page.locator('#loginModal .auth-modal').boundingBox();
  expect(mobileModal).toBeTruthy();
  expect(mobileModal.x).toBeGreaterThanOrEqual(0);
  expect(mobileModal.y).toBeGreaterThanOrEqual(0);
  expect(mobileModal.x + mobileModal.width).toBeLessThanOrEqual(390);
  expect(mobileModal.y + mobileModal.height).toBeLessThanOrEqual(844);
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-validation-mobile.png'),
    fullPage: true
  });
});

test('rejects mismatched signup passwords', async ({ page }) => {
  const account = uniqueAccount('mismatch');
  await openSignup(page);
  await fillSignup(page, {
    ...account,
    nickname: '비밀번호 검증',
    passwordConfirm: 'DifferentPassword123'
  });
  await page.locator('#signupButton').click();

  await expect(page.locator('#signupPasswordConfirmError')).toHaveText('비밀번호가 일치하지 않습니다.');
  await expect(page.locator('#signupPanel')).toBeVisible();
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-password-mismatch-desktop.png'),
    fullPage: true
  });
});

test('clears every signup field after signup, login, logout, and re-entry', async ({ page }) => {
  const account = uniqueAccount('reset');
  await openSignup(page);
  await fillSignup(page, { ...account, nickname: '폼 초기화 검증' });
  await page.locator('#signupButton').click();

  await expect(page.locator('#loginPanel')).toBeVisible();
  await expect(page.locator('#authSuccess')).toHaveText('회원가입이 완료되었습니다. 비밀번호를 입력해 로그인하세요.');
  await expect(page.locator('#emailInput')).toHaveValue(account.email);
  await expect(page.locator('#passwordInput')).toHaveValue('');
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-success-desktop.png'),
    fullPage: true
  });

  await page.locator('#passwordInput').fill(account.password);
  await page.locator('#loginButton').click();
  await expect(page.locator('#loginModal')).toBeHidden();
  await page.locator('#logoutButton').click();
  await expect(page.locator('#loginModal')).toBeVisible();
  await page.locator('#signupTab').click();

  await expect(page.locator('#signupEmailInput')).toHaveValue('');
  await expect(page.locator('#signupNicknameInput')).toHaveValue('');
  await expect(page.locator('#signupPasswordInput')).toHaveValue('');
  await expect(page.locator('#signupPasswordConfirmInput')).toHaveValue('');
});

test('rejects a duplicate email without clearing the submitted fields', async ({ page, request }) => {
  const account = uniqueAccount('duplicate');
  const createUser = await request.post('/api/auth/signup', {
    data: { ...account, nickname: '기존 사용자' }
  });
  expect(createUser.status()).toBe(201);

  await openSignup(page);
  await fillSignup(page, { ...account, nickname: '중복 사용자' });
  await page.locator('#signupButton').click();

  await expect(page.locator('#signupEmailError')).toHaveText('이미 가입된 이메일입니다.');
  await expect(page.locator('#signupEmailInput')).toHaveValue(account.email);
  await expect(page.locator('#signupNicknameInput')).toHaveValue('중복 사용자');
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-duplicate-desktop.png'),
    fullPage: true
  });
});
