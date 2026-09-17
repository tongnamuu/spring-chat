const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, '../../chat-api/src/main/resources/static/index.html'), 'utf8');
const source = [...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(m => m[1]).join('\n');

function setup() {
  const timers = new Map();
  let nextTimer = 0;
  let latest = 100;
  let requests = 0;
  const element = () => ({ style: {}, classList: { add() {}, remove() {}, toggle() {} }, replaceChildren() {}, focus() {}, setAttribute() {} });
  const context = vm.createContext({
    console: { log() {} }, AbortSignal,
    document: { getElementById: element }, window: { addEventListener() {} },
    requestAnimationFrame: () => 1,
    setTimeout: (callback, ms) => { timers.set(++nextTimer, { callback, ms }); return nextTimer; },
    clearTimeout: id => timers.delete(id),
    fetch: async url => {
      requests++;
      const message = id => ({ roomId: 42, messageId: id, eventId: `event-${id}`, content: `message-${id}`, messageType: 'TALK' });
      const after = Number(new URL(url, 'http://localhost').searchParams.get('lastReceivedMessageId'));
      return { ok: true, json: async () => url.includes('/sync?')
        ? Array.from({ length: Math.min(500, latest - after) }, (_, i) => message(after + i + 1))
        : [message(latest)] };
    }
  });
  vm.runInContext(source, context);
  const run = code => vm.runInContext(code, context);
  run(`currentUser = {userId: 1}; activeRoom = {roomId: 42};
    stompClient = {connected: true, subscribe() {return {unsubscribe() {}}}, disconnect() {}};
    resetRoomMessageState(42); renderMessages = messages => messages.forEach(appendMessage);`);
  return { run, context, timers, setLatest: n => { latest = n; }, requests: () => requests,
    tick: async () => {
      const [id, timer] = timers.entries().next().value;
      assert.equal(timer.ms, 1000);
      timers.delete(id);
      timer.callback();
      await new Promise(resolve => setImmediate(resolve));
    }
  };
}

for (const initial of [true, false]) {
  test(`recovers final message without a live callback (${initial ? 'initial entry' : 'reconnect'})`, async () => {
    const s = setup();
    if (initial) await s.run('selectRoom({roomId: 42, title: "test"})');
    else {
      s.run('getRoomMessageState(42).lastReceivedMessageId = 100; subscribeToRoom(42)');
      await s.run('syncMissedMessages(42)');
    }
    assert.equal(s.run('getRoomMessageState(42).lastReceivedMessageId'), 100);
    s.setLatest(101);
    await s.tick();
    assert.equal(s.run('getRoomMessageState(42).lastReceivedMessageId'), 101);
    assert.equal(s.run('pendingRenderMessages.filter(m => m.messageId === 101).length'), 1);
    await s.tick();
    assert.equal(s.run('pendingRenderMessages.filter(m => m.messageId === 101).length'), 1);
    assert.equal(s.timers.size, 1);
  });
}

test('disconnect and logout cancel polling', async () => {
  const s = setup();
  await s.run('syncMissedMessages(42, true)');
  s.run('disconnectRealtime(); resetToLogin()');
  assert.equal(s.timers.size, 0);
});

test('periodic recovery paginates a large gap and bounds the render queue', async () => {
  const s = setup();
  await s.run('syncMissedMessages(42, true)');
  s.setLatest(1301);
  await s.tick();
  assert.equal(s.run('getRoomMessageState(42).lastReceivedMessageId'), 1301);
  assert.equal(s.run('pendingRenderMessages.length'), 500);
  assert.equal(s.requests(), 5);
});

test('slow old-room response cannot advance cursor or schedule polling after room change', async () => {
  const s = setup();
  let release;
  s.context.fetch = () => new Promise(resolve => { release = resolve; });
  const pending = s.run('syncMissedMessages(42, true)');
  await s.run('syncMissedMessages(42)');
  s.run('roomSelectionVersion++; activeRoom = {roomId: 43}; resetRoomMessageState(43)');
  release({ ok: true, json: async () => [{ roomId: 42, messageId: 101 }] });
  await pending;
  assert.equal(s.run('getRoomMessageState(43).lastReceivedMessageId'), 0);
  assert.equal(s.timers.size, 0);
});

test('failed history request schedules only one retry and recovers', async () => {
  const s = setup();
  const fetch = s.context.fetch;
  s.context.fetch = async () => { throw new Error('temporary failure'); };
  await s.run('syncMissedMessages(42, true)');
  assert.equal(s.timers.size, 1);
  s.context.fetch = fetch;
  await s.tick();
  assert.equal(s.run('getRoomMessageState(42).lastReceivedMessageId'), 100);
  assert.equal(s.timers.size, 1);
});
