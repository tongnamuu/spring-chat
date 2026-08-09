const { test, expect } = require('@playwright/test');
const path = require('path');

const USERNAME = 'chat1_e2e';
const PASSWORD = 'chat1-e2e-password';
const NICKNAME = 'E2E 검증 사용자';

test('CHAT-1 Redis session login, shared WS identity, refresh, and logout', async ({ page, request, context }) => {
  const createUser = await request.post('/api/users', {
    data: { username: USERNAME, nickname: NICKNAME, password: PASSWORD }
  });
  expect([200, 400]).toContain(createUser.status());

  await page.goto('/');
  await expect(page.locator('#loginModal')).toBeVisible();
  expect(await page.evaluate(async () => (await fetch('/ws-stomp/info')).status)).toBe(401);
  await page.locator('#usernameInput').fill(USERNAME);
  await page.locator('#passwordInput').fill(PASSWORD);
  await page.locator('#loginButton').click();

  await expect(page.locator('#loginModal')).toBeHidden();
  await expect(page.locator('#userBadge')).toContainText(NICKNAME);
  await expect(page.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');

  const authenticatedUser = await page.evaluate(async () => {
    const response = await fetch('/api/auth/me');
    return { status: response.status, body: await response.json() };
  });
  expect(authenticatedUser.status).toBe(200);
  expect(authenticatedUser.body.username).toBe(USERNAME);

  const sessionCookie = (await context.cookies()).find(cookie => cookie.name === 'CHAT_SESSION');
  expect(sessionCookie).toBeTruthy();
  expect(sessionCookie.httpOnly).toBe(true);
  expect(sessionCookie.sameSite).toBe('Lax');

  const e2eRoom = page.locator('.room-item').filter({ hasText: 'CHAT-1 E2E Room' }).first();
  if (await e2eRoom.count()) {
    await e2eRoom.click();
  } else {
    await page.getByRole('button', { name: /방 개설/ }).click();
    await page.locator('#newRoomTitle').fill('CHAT-1 E2E Room');
    await page.locator('#newMaxCapacity').fill('10');
    await page.locator('#createRoomModal .btn-primary').click();
  }
  await expect(page.locator('#currentRoomTitle')).toHaveText('CHAT-1 E2E Room');
  await expect.poll(() => page.evaluate(() => historyLoading)).toBe(false);

  await page.evaluate(() => {
    window.__chat1Messages = [];
    const originalAppendMessage = window.appendMessage;
    window.appendMessage = message => {
      window.__chat1Messages.push(message);
      originalAppendMessage(message);
    };
    stompClient.send('/pub/chat/message', {}, JSON.stringify({
      roomId: activeRoom.roomId,
      senderId: 999999,
      senderName: 'forged-user',
      messageType: 'TALK',
      content: 'server-authenticated-message'
    }));
  });

  await expect.poll(() => page.evaluate(() => window.__chat1Messages.length)).toBeGreaterThan(0);
  const received = await page.evaluate(() => window.__chat1Messages.at(-1));
  expect(received.senderId).toBe(authenticatedUser.body.userId);
  expect(received.senderName).toBe(NICKNAME);
  expect(received.content).toBe('server-authenticated-message');

  await page.screenshot({
    path: path.resolve(__dirname, '../../artifacts/e2e/chat-1-login-success.png'),
    fullPage: true
  });

  await page.reload();
  await expect(page.locator('#loginModal')).toBeHidden();
  await expect(page.locator('#userBadge')).toContainText(NICKNAME);
  await expect(page.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');

  await page.locator('#logoutButton').click();
  await expect(page.locator('#loginModal')).toBeVisible();
  const afterLogout = await page.evaluate(async () => (await fetch('/api/auth/me')).status);
  expect(afterLogout).toBe(401);
  expect(await page.evaluate(async () => (await fetch('/ws-stomp/info')).status)).toBe(401);
  expect(await page.evaluate(() => stompClient === null)).toBe(true);

  await page.locator('#usernameInput').fill(USERNAME);
  await page.locator('#passwordInput').fill(PASSWORD);
  await page.locator('#loginButton').click();
  await expect(page.locator('#loginModal')).toBeHidden();
  await expect(page.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');

  await page.locator('#logoutButton').click();
  await expect(page.locator('#loginModal')).toBeVisible();
});
