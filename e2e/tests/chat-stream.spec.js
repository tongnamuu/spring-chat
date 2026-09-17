const { test, expect } = require('@playwright/test');

async function prepare(page) {
  await page.goto('/');
  await expect(page.locator('#loginModal')).toBeVisible();
  await page.evaluate(() => {
    currentUser = { userId: 1, nickname: 'Test' };
    activeRoom = { roomId: 42 };
    resetRoomMessageState(42);
    historyLoading = false;
    document.getElementById('messagesContainer').replaceChildren();
    stompClient = {
      connected: true,
      subscribe: (_, callback) => { window.deliver = callback; return { unsubscribe() {} }; }
    };
    subscribeToRoom(42);
  });
}

test('hot-room UI accepts all IDs while bounding DOM, render queue and dedup state', async ({ page }) => {
  await prepare(page);
  const state = await page.evaluate(() => {
    let accepted = 0;
    const remember = rememberMessage;
    rememberMessage = message => {
      const result = remember(message);
      if (result) accepted++;
      return result;
    };
    for (let first = 1; first <= 25000; first += 100) {
      const batch = { roomId: 42, previousMessageId: first - 1,
        messages: Array.from({ length: 100 }, (_, i) => ({
          roomId: 42, messageId: first + i, eventId: `event-${first + i}`,
          senderId: 1, messageType: 'TALK', content: `message-${first + i}`
        })) };
      window.deliver({ body: JSON.stringify(batch) });
      window.deliver({ body: JSON.stringify(batch) });
    }
    return { accepted, cursor: getRoomMessageState(42).lastReceivedMessageId,
      seen: getRoomMessageState(42).seenKeys.size, renderPending: pendingRenderMessages.length };
  });
  expect(state).toEqual({ accepted: 25000, cursor: 25000, seen: 20000, renderPending: 500 });
  await expect(page.locator('#messagesContainer > .msg')).toHaveCount(500);
  await expect(page.locator('#messagesContainer > .msg').last()).toHaveText('message-25000');
});

test('a live batch gap triggers bounded recovery before advancing the cursor', async ({ page }) => {
  const messages = Array.from({ length: 1201 }, (_, i) => ({
    roomId: 42, messageId: i + 1, eventId: `event-${i + 1}`,
    senderId: 1, messageType: 'TALK', content: `message-${i + 1}`
  }));
  const pages = [];
  await page.route('**/api/rooms/42/messages?*', route => route.fulfill({ json: [messages.at(-1)] }));
  await page.route('**/api/rooms/42/sync?*', route => {
    const url = new URL(route.request().url());
    const after = Number(url.searchParams.get('lastReceivedMessageId'));
    expect(url.searchParams.get('throughMessageId')).toBe('1201');
    pages.push(after);
    return route.fulfill({ json: messages.filter(message => message.messageId > after).slice(0, 500) });
  });
  await prepare(page);
  await page.evaluate(message => window.deliver({ body: JSON.stringify({
    roomId: 42, previousMessageId: 1200, messages: [message]
  }) }), messages.at(-1));
  await expect.poll(() => page.evaluate(() => getRoomMessageState(42).lastReceivedMessageId)).toBe(1201);
  expect(pages).toEqual([0, 500, 1000]);
  await expect(page.locator('#messagesContainer > .msg')).toHaveCount(500);
});
