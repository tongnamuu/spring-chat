const { test, expect } = require('@playwright/test');
const path = require('path');

test('CHAT-3 signup validates fields, handles duplicates, and returns to login', async ({ page }) => {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  const username = `chat3_${suffix}@example.com`;
  const password = 'SignupE2E123';

  await page.goto('/');
  await page.locator('#signupTab').click();
  await expect(page.locator('#signupPanel')).toBeVisible();
  await expect(page.locator('#loginPanel')).toBeHidden();
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-form-desktop.png'),
    fullPage: true
  });

  await page.locator('#signupUsernameInput').fill('invalid@');
  await page.locator('#signupNicknameInput').fill('A');
  await page.locator('#signupPasswordInput').fill('onlyletters');
  await page.locator('#signupPasswordConfirmInput').fill('onlyletters');
  await page.locator('#signupButton').click();
  await expect(page.locator('#signupUsernameError')).not.toBeEmpty();
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

  await page.locator('#signupUsernameInput').fill(username);
  await page.locator('#signupNicknameInput').fill('CHAT-3 검증 사용자');
  await page.locator('#signupPasswordInput').fill(password);
  await page.locator('#signupPasswordConfirmInput').fill('DifferentPassword123');
  await page.locator('#signupButton').click();
  await expect(page.locator('#signupPasswordConfirmError')).toHaveText('비밀번호가 일치하지 않습니다.');
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-password-mismatch-desktop.png'),
    fullPage: true
  });

  await page.locator('#signupPasswordConfirmInput').fill(password);
  await page.locator('#signupButton').click();
  await expect(page.locator('#loginPanel')).toBeVisible();
  await expect(page.locator('#signupPanel')).toBeHidden();
  await expect(page.locator('#authSuccess')).toHaveText('회원가입이 완료되었습니다. 비밀번호를 입력해 로그인하세요.');
  await expect(page.locator('#usernameInput')).toHaveValue(username);
  await expect(page.locator('#passwordInput')).toHaveValue('');
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-success-desktop.png'),
    fullPage: true
  });

  await page.locator('#signupTab').click();
  await page.locator('#signupUsernameInput').fill(username);
  await page.locator('#signupNicknameInput').fill('중복 사용자');
  await page.locator('#signupPasswordInput').fill(password);
  await page.locator('#signupPasswordConfirmInput').fill(password);
  await page.locator('#signupButton').click();
  await expect(page.locator('#signupUsernameError')).toHaveText('이미 사용 중인 아이디입니다.');
  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-3-signup-duplicate-desktop.png'),
    fullPage: true
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.locator('#signupUsernameInput').fill('mobile@');
  await page.locator('#signupNicknameInput').fill('A');
  await page.locator('#signupPasswordInput').fill('short1');
  await page.locator('#signupPasswordConfirmInput').fill('short1');
  await page.locator('#signupButton').click();
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
