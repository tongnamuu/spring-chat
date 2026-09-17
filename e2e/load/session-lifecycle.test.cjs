const { test } = require('node:test');
const assert = require('node:assert/strict');
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');
const { setTimeout: delay } = require('node:timers/promises');
const base = process.env.BASE_URL || 'http://localhost';
const password = 'LifecycleTest123!';

async function api(path, cookie, data, method = 'POST') {
  const res = await fetch(base + path, {
    method, headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: JSON.stringify(data), signal: AbortSignal.timeout(10000)
  });
  assert.ok(res.ok, `${path}: ${res.status}`);
  return res;
}

async function login(username) {
  const res = await api('/api/auth/login', null, { username, password });
  return res.headers.getSetCookie().map(value => value.split(';')[0]).join('; ');
}

async function until(predicate) {
  const deadline = Date.now() + 10000;
  while (!predicate()) {
    assert.ok(Date.now() < deadline, 'Timed out waiting for socket lifecycle');
    await delay(50);
  }
}

for (const action of ['logout', 'withdraw']) {
  test(`${action} closes receive-only sockets on both WS nodes`, { timeout: 45000 }, async () => {
    const clients = [];
    async function connect(cookie, endpoint, roomId) {
      const state = { closed: false, messages: [] };
      const client = new Client({
        webSocketFactory: () => new WebSocket(base.replace(/^http/, 'ws') + endpoint + '/websocket', { headers: { Cookie: cookie } }),
        reconnectDelay: 0, heartbeatIncoming: 0, heartbeatOutgoing: 0,
        onWebSocketClose: () => { state.closed = true; }
      });
      clients.push(client);
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('Connect timeout')), 10000);
        client.onConnect = () => {
          client.subscribe(`/sub/chat/room/${roomId}`, frame => {
            const batch = JSON.parse(frame.body);
            state.messages.push(...batch.messages.map(m => m.content));
          });
          clearTimeout(timer);
          resolve();
        };
        client.onStompError = () => { clearTimeout(timer); reject(new Error('STOMP error')); };
        client.activate();
      });
      return { client, state };
    }
    try {
      const suffix = Date.now().toString(36);
      const username = `lc_${action}_${suffix}`;
      const observer = `lc_other_${suffix}`;
      for (const name of [username, observer]) await api('/api/auth/signup', null, { username: name, nickname: 'Lifecycle', password });
      const firstCookie = await login(username);
      const secondCookie = action === 'withdraw' ? await login(username) : firstCookie;
      const otherCookie = await login(observer);
      const room = await (await api('/api/rooms', otherCookie, { title: 'Lifecycle test', roomType: 'GROUP_PUBLIC', maxCapacity: 10 })).json();
      await api('/api/rooms/join', firstCookie, { inviteCode: room.inviteCode });
      const a = await connect(firstCookie, '/ws-stomp-a', room.roomId);
      const b = await connect(secondCookie, '/ws-stomp-b', room.roomId);
      const other = await connect(otherCookie, '/ws-stomp-b', room.roomId);
      const send = content => other.client.publish({ destination: '/pub/chat/message', body: JSON.stringify({ roomId: room.roomId, messageType: 'TALK', content }) });
      send('before');
      await until(() => [a, b, other].every(c => c.state.messages.includes('before')));
      if (action === 'logout') await api('/api/auth/logout', firstCookie, {});
      else await api('/api/users/me', firstCookie, { password }, 'DELETE');
      await until(() => a.state.closed && b.state.closed);
      assert.equal(other.state.closed, false);
      send('after');
      await until(() => other.state.messages.includes('after'));
      assert.equal(a.state.messages.includes('after'), false);
      assert.equal(b.state.messages.includes('after'), false);
    } finally {
      await Promise.all(clients.map(client => client.deactivate({ force: true })));
    }
  });
}
