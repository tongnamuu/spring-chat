const { test, expect } = require('@playwright/test');

const room = { roomId: 42, title: '팀 채팅', createdBy: 1, inviteCode: 'ROOM1234', currentCount: 2, maxCapacity: 50, roomType: 'GROUP_PUBLIC' };
const oldMessage = { roomId: 42, messageId: 1, senderId: 1, messageType: 'TALK', content: '강퇴 전 채팅 내역' };

async function prepare(page, owner = false) {
  await page.route('**/api/auth/me', route => route.fulfill({ status: 401, json: {} }));
  await page.route('**/api/rooms/my', route => route.fulfill({ json: [room] }));
  await page.goto('/');
  await expect(page.locator('#loginModal')).toBeVisible();
  await page.evaluate(({ room, owner, oldMessage }) => {
    currentUser = { userId: owner ? 1 : 2, nickname: owner ? '방장' : '멤버' };
    document.getElementById('loginModal').style.display = 'none';
    activeRoom = room;
    resetRoomMessageState(room.roomId);
    historyLoading = false;
    renderRoomList([room]);
    document.getElementById('messagesContainer').replaceChildren();
    document.getElementById('manageMembersBtn').hidden = !owner;
    stompClient = { connected: true, subscribe: (_, callback) => {
      window.deliver = callback;
      return { unsubscribe() { window.unsubscribed = true; } };
    }};
    subscribeToRoom(room.roomId);
    appendMessage(oldMessage);
  }, { room, owner, oldMessage });
  await expect(page.locator('#messagesContainer')).toContainText(oldMessage.content);
}

test('removal clears history, buffered messages, input and subscriptions; late frames cannot restore it', async ({ page }) => {
  await prepare(page);
  await page.evaluate(() => {
    pendingRoomMessages = [{ roomId: 42, messages: [{ content: 'buffered secret' }] }];
    pendingRoomMessageCount = 1;
    document.getElementById('messageInput').value = 'draft';
    removeRoomAccess(42);
    window.deliver({ body: JSON.stringify({ roomId: 42, messageId: 2, messageType: 'TALK', content: 'late secret' }) });
    flushMessages();
  });
  await expect(page.locator('#messagesContainer')).toBeEmpty();
  await expect(page.locator('#roomList')).toBeEmpty();
  await expect(page.locator('#messageInput')).toBeDisabled();
  expect(await page.evaluate(() => ({ room: activeRoom, states: roomMessageState.size,
    buffered: pendingRoomMessages.length, pending: pendingRenderMessages.length, unsubscribed: window.unsubscribed })))
    .toEqual({ room: null, states: 0, buffered: 0, pending: 0, unsubscribed: true });
});

test('an in-flight history response cannot restore revoked room content', async ({ page }) => {
  let release;
  const blocked = new Promise(resolve => { release = resolve; });
  let requested;
  const requestSeen = new Promise(resolve => { requested = resolve; });
  await page.route('**/api/rooms/42/messages?*', async route => {
    requested();
    await blocked;
    await route.fulfill({ json: [oldMessage] });
  });
  await prepare(page);
  await page.evaluate(() => { window.pendingSync = syncMissedMessages(42, true); });
  await requestSeen;
  await page.evaluate(() => removeRoomAccess(42));
  release();
  await page.evaluate(() => window.pendingSync);
  await expect(page.locator('#messagesContainer')).toBeEmpty();
  expect(await page.evaluate(() => activeRoom)).toBeNull();
});

test('forbidden history stops retries and clears cached content', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/rooms/42/messages?*', route => {
    requests++;
    return route.fulfill({ status: 403, json: { code: 'R004' } });
  });
  await prepare(page);
  await page.evaluate(() => syncMissedMessages(42));
  await expect(page.locator('#messagesContainer')).toBeEmpty();
  expect(await page.evaluate(() => activeRoom)).toBeNull();
  expect(requests).toBe(1);
});

test('room reconciliation clears a silent room when notification was missed', async ({ page }) => {
  await prepare(page);
  await page.route('**/api/rooms/my', route => route.fulfill({ json: [] }));
  await page.evaluate(() => loadMyRooms());
  await expect(page.locator('#messagesContainer')).toBeEmpty();
  expect(await page.evaluate(() => activeRoom)).toBeNull();
});

test('owner can remove a member using the management dialog', async ({ page }) => {
  await page.route('**/api/rooms/42/members', route => route.fulfill({ json: [
    { userId: 1, nickname: '방장', role: 'OWNER' },
    { userId: 2, nickname: '참여자', role: 'MEMBER' }
  ] }));
  let deleted = false;
  await page.route('**/api/rooms/42/members/2', route => {
    expect(route.request().method()).toBe('DELETE');
    deleted = true;
    return route.fulfill({ status: 204 });
  });
  await prepare(page, true);
  await page.getByRole('button', { name: '멤버 관리' }).click();
  await expect(page.locator('#membersModal')).toBeVisible();
  await expect(page.locator('#membersList').getByRole('button', { name: '강퇴' })).toHaveCount(1);
  page.once('dialog', dialog => dialog.accept());
  await page.locator('#membersList').getByRole('button', { name: '강퇴' }).click();
  await expect(page.locator('#membersList')).not.toContainText('참여자');
  expect(deleted).toBe(true);
});


test('a socket kick event immediately exits the open room before room-list refresh', async ({ page }) => {
  await prepare(page);
  await page.route('**/api/rooms/42/messages?*', route => route.fulfill({ json: [oldMessage] }));
  await page.evaluate(() => {
    const subscriptions = new Map();
    window.SockJS = function () {};
    window.Stomp = { over: () => ({
      connected: true,
      connect: (_, connected) => connected('connected'),
      subscribe: (destination, callback) => {
        subscriptions.set(destination, callback);
        return { unsubscribe() { window.unsubscribed = true; } };
      }
    }) };
    initWebSocket();
    subscriptions.get('/user/queue/room-events')({ body: JSON.stringify({ type: 'KICKED', roomId: 42 }) });
    // Inspect state synchronously: no polling or HTTP response is needed to exit.
    window.immediateExit = activeRoom === null && pendingRenderMessages.length === 0
      && document.getElementById('messagesContainer').children.length === 0;
  });
  expect(await page.evaluate(() => window.immediateExit)).toBe(true);
  await expect(page.locator('#currentRoomTitle')).toHaveText('채팅방을 선택하세요');
  await expect(page.locator('#currentRoomMeta')).toContainText('방에서 강퇴되었습니다');
  await expect(page.locator('#roomList')).toBeEmpty();
  await expect(page.locator('#messageInput')).toBeDisabled();
  expect(await page.evaluate(() => stompClient.connected)).toBe(true);
});
