const { test, expect } = require('@playwright/test');

const PASSWORD = 'chat8-e2e-password1';

async function pinWebSocketEndpoint(page, endpoint) {
  await page.evaluate((targetEndpoint) => {
    const OriginalSockJS = window.SockJS;
    window.SockJS = function pinnedSockJS() {
      return new OriginalSockJS(targetEndpoint);
    };
  }, endpoint);
}

async function login(page, username) {
  await page.goto('/');
  await pinWebSocketEndpoint(page, page.__wsEndpoint);
  await page.locator('#usernameInput').fill(username);
  await page.locator('#passwordInput').fill(PASSWORD);
  await page.locator('#loginButton').click();
  await expect(page.locator('#loginModal')).toBeHidden();
  await expect(page.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');
}

async function installMessageRecorder(page, name) {
  await page.evaluate((recorderName) => {
    window[recorderName] = [];
    const originalAppendMessage = window.appendMessage;
    window.appendMessage = message => {
      window[recorderName].push(message);
      originalAppendMessage(message);
    };
  }, name);
}

async function recordedContents(page, recorderName) {
  return page.evaluate((name) => window[name]
    .filter(message => message.messageType === 'TALK')
    .map(message => message.content), recorderName);
}

test('CHAT-8 fans out messages across pinned chat-ws instances and syncs missed reconnect messages', async ({ browser, request }) => {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
  const aliceUsername = `chat8_alice_${suffix}`;
  const bobUsername = `chat8_bob_${suffix}`;

  await expect((await request.post('/api/auth/signup', {
    data: { username: aliceUsername, nickname: 'CHAT-8 Alice', password: PASSWORD }
  })).status()).toBe(201);
  await expect((await request.post('/api/auth/signup', {
    data: { username: bobUsername, nickname: 'CHAT-8 Bob', password: PASSWORD }
  })).status()).toBe(201);

  const aliceContext = await browser.newContext();
  const bobContext = await browser.newContext();
  const alice = await aliceContext.newPage();
  const bob = await bobContext.newPage();
  alice.__wsEndpoint = '/ws-stomp-a';
  bob.__wsEndpoint = '/ws-stomp-b';

  try {
    await login(alice, aliceUsername);
    await login(bob, bobUsername);

    await alice.getByRole('button', { name: /방 개설/ }).click();
    await alice.locator('#newRoomTitle').fill(`CHAT-8 E2E ${suffix}`);
    await alice.locator('#newMaxCapacity').fill('10');
    await alice.locator('#createRoomModal .btn-primary').click();
    await expect(alice.locator('#currentRoomTitle')).toHaveText(`CHAT-8 E2E ${suffix}`);

    const room = await alice.evaluate(() => activeRoom);
    await bob.getByRole('button', { name: /코드로 참여/ }).click();
    await bob.locator('#inviteCodeInput').fill(room.inviteCode);
    await bob.locator('#joinRoomModal .btn-primary').click();
    await expect(bob.locator('#currentRoomTitle')).toHaveText(`CHAT-8 E2E ${suffix}`);

    await installMessageRecorder(alice, '__chat8Messages');
    await installMessageRecorder(bob, '__chat8Messages');

    await alice.evaluate(() => {
      ['cross-node-1', 'cross-node-2', 'cross-node-3'].forEach(content => {
        stompClient.send('/pub/chat/message', {}, JSON.stringify({
          roomId: activeRoom.roomId,
          messageType: 'TALK',
          content
        }));
      });
    });

    await expect.poll(() => recordedContents(alice, '__chat8Messages')).toEqual([
      'cross-node-1',
      'cross-node-2',
      'cross-node-3'
    ]);
    await expect.poll(() => recordedContents(bob, '__chat8Messages')).toEqual([
      'cross-node-1',
      'cross-node-2',
      'cross-node-3'
    ]);

    await bob.evaluate(() => disconnectRealtime());
    await expect(bob.locator('#statusText')).toHaveText('WS Zero-Downtime Connected');

    await alice.evaluate(() => {
      stompClient.send('/pub/chat/message', {}, JSON.stringify({
        roomId: activeRoom.roomId,
        messageType: 'TALK',
        content: 'missed-while-bob-disconnected'
      }));
    });

    await expect.poll(() => recordedContents(alice, '__chat8Messages')).toContain('missed-while-bob-disconnected');
    await bob.evaluate(() => initWebSocket());

    await expect.poll(() => recordedContents(bob, '__chat8Messages'), { timeout: 15_000 }).toContain('missed-while-bob-disconnected');
    const bobContents = await recordedContents(bob, '__chat8Messages');
    expect(bobContents.filter(content => content === 'missed-while-bob-disconnected')).toHaveLength(1);
  } finally {
    await aliceContext.close();
    await bobContext.close();
  }
});
