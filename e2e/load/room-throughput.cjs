// Real authenticated STOMP clients; opt-in because this creates users and messages.
const { Client } = require('@stomp/stompjs');
const WebSocket = require('ws');
const { performance } = require('node:perf_hooks');
const { setTimeout: delay } = require('node:timers/promises');

const base = process.env.BASE_URL || 'http://localhost';
const clients = Number(process.env.CLIENTS || 50);
const rate = Number(process.env.RATE || 1000);
const seconds = Number(process.env.SECONDS || 10);
const bytes = Number(process.env.MESSAGE_BYTES || 256);
const run = process.env.RUN_ID || String(Date.now());
const publisher = process.env.PUBLISH !== 'false';
const total = rate * seconds;
const password = 'load-test-password1';
const sockets = [];
const stats = [];
const firstArrival = new Float64Array(total);
const lastArrival = new Float64Array(total);
const latencies = new Uint32Array(60001);
let maxLatency = 0;
let maxIngressLatency = 0;
let maxAfterIngressLatency = 0;
let protocolErrors = 0;
let disconnects = 0;
const closeCodes = {};
let measuring = false;

for (const [name, value] of Object.entries({ clients, rate, seconds, bytes })) {
  if (!Number.isInteger(value) || value < 1) throw new Error(`Invalid ${name}`);
}
if (clients > 1500 || bytes > 1800) throw new Error('CLIENTS <= 1500 and MESSAGE_BYTES <= 1800 required');

async function api(path, cookie, data) {
  const response = await fetch(base + path, {
    method: data === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(cookie ? { Cookie: cookie } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data),
    signal: AbortSignal.timeout(15000)
  });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}: ${await response.text()}`);
  return response;
}

async function account(index) {
  const username = `load_${Date.now()}_${index}_${Math.random().toString(36).slice(2, 6)}`;
  await api('/api/auth/signup', null, { username, nickname: `Load ${index}`, password });
  const response = await api('/api/auth/login', null, { username, password });
  const cookie = response.headers.getSetCookie().map(value => value.split(';')[0]).join('; ');
  if (!cookie) throw new Error('No authenticated session cookie');
  return cookie;
}

async function connect(cookie, roomId, index) {
  const state = { count: 0, duplicates: 0, outOfOrder: 0, last: -1, ready: false, seen: new Uint8Array(Math.ceil(total / 8)) };
  stats[index] = state;
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error(`Client ${index} connect timeout`)), 15000);
    const client = new Client({
      debug: process.env.DEBUG_LOAD ? line => console.error(line) : () => {},
      webSocketFactory: () => new WebSocket(base.replace(/^http/, 'ws') + '/ws-stomp/websocket', { headers: { Cookie: cookie } }),
      reconnectDelay: 0,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 10000,
      splitLargeFrames: true,
      maxWebSocketChunkSize: 8192,
      onStompError: frame => { protocolErrors++; reject(new Error(frame.body)); },
      onWebSocketError: error => reject(error),
      onWebSocketClose: event => {
        if (measuring) {
          disconnects++;
          closeCodes[event.code] = (closeCodes[event.code] || 0) + 1;
        }
      },
      onConnect: () => {
        clearTimeout(timeout);
        client.subscribe(`/sub/chat/room/${roomId}`, frame => {
          if (process.env.DEBUG_LOAD) console.error(frame.body);
          const body = JSON.parse(frame.body);
          for (const message of body.messages || [body]) {
            if (message.content === `ready:${run}`) { state.ready = true; continue; }
            if (!message.content?.startsWith(`load:${run}:`)) continue;
            const [, , sequence, timestamp] = message.content.split(':', 5);
            const seq = Number(sequence);
            if (!Number.isInteger(seq) || seq < 0 || seq >= total) { protocolErrors++; continue; }
            const mask = 1 << (seq & 7);
            if (state.seen[seq >> 3] & mask) { state.duplicates++; continue; }
            state.seen[seq >> 3] |= mask;
            if (seq < state.last) state.outOfOrder++;
            state.last = Math.max(seq, state.last);
            state.count++;
            const now = Date.now();
            const latency = Math.max(0, now - Number(timestamp));
            const ingressAt = Date.parse(message.createdAt);
            maxIngressLatency = Math.max(maxIngressLatency, ingressAt - Number(timestamp));
            maxAfterIngressLatency = Math.max(maxAfterIngressLatency, now - ingressAt);
            maxLatency = Math.max(maxLatency, latency);
            latencies[Math.min(60000, latency)]++;
            firstArrival[seq] = firstArrival[seq] ? Math.min(firstArrival[seq], now) : now;
            lastArrival[seq] = Math.max(lastArrival[seq], now);
          }
        });
        resolve(client);
      }
    });
    sockets.push(client);
    client.activate();
  });
}

function percentile(p) {
  const count = latencies.reduce((sum, n) => sum + n, 0);
  let seen = 0;
  for (let i = 0; i < latencies.length; i++) {
    seen += latencies[i];
    if (seen >= count * p && count) return i;
  }
  return null;
}

async function main() {
  const ownerCookie = await account(0);
  const room = process.env.ROOM_ID
    ? { roomId: Number(process.env.ROOM_ID), inviteCode: process.env.INVITE_CODE }
    : await (await api('/api/rooms', ownerCookie, { title: `Load ${run}`, roomType: 'GROUP_PUBLIC', maxCapacity: 1500 })).json();
  console.log(JSON.stringify({ setup: true, run, room, clients, rate, seconds, publisher }));
  if (process.env.ROOM_ID) await api('/api/rooms/join', ownerCookie, { inviteCode: room.inviteCode });
  await connect(ownerCookie, room.roomId, 0);
  for (let start = 1; start < clients; start += 10) {
    const results = await Promise.allSettled(Array.from({ length: Math.min(10, clients - start) }, async (_, offset) => {
      const index = start + offset;
      const cookie = await account(index);
      await api('/api/rooms/join', cookie, { inviteCode: room.inviteCode });
      await connect(cookie, room.roomId, index);
    }));
    const failed = results.find(result => result.status === 'rejected');
    if (failed) throw failed.reason;
  }
  const send = content => sockets[0].publish({ destination: '/pub/chat/message', body: JSON.stringify({
    roomId: room.roomId, messageType: 'TALK', content
  }) });
  // A persisted marker proves the subscription reached the broker, not just CONNECT.
  const readinessDeadline = Date.now() + 30000;
  while (!stats.every(state => state.ready)) {
    if (Date.now() > readinessDeadline) throw new Error(`Only ${stats.filter(state => state.ready).length}/${clients} subscriptions receive persisted markers`);
    send(`ready:${run}`);
    await delay(200);
  }
  const startAt = Number(process.env.START_AT || Date.now() + 1000);
  if (startAt < Date.now()) throw new Error('START_AT elapsed before clients became ready');
  await delay(startAt - Date.now());
  measuring = true;
  let sent = 0;
  const started = performance.now();
  if (publisher) {
    while (sent < total) {
      if (!sockets[0].connected) break;
      const target = Math.min(total, Math.floor((performance.now() - started) * rate / 1000));
      // Yield between bursts so the generator also processes socket receives.
      const end = Math.min(target, sent + 500);
      while (sent < end) {
        const batch = [];
        while (sent < end && batch.length < 100) {
          batch.push({ roomId: room.roomId, messageType: 'TALK',
            content: `load:${run}:${sent}:${Date.now()}:` + 'x'.repeat(bytes) });
          sent++;
        }
        sockets[0].publish({ destination: '/pub/chat/messages', body: JSON.stringify(batch) });
      }
      await delay(10);
    }
  } else {
    await delay(seconds * 1000);
  }
  const offeredSeconds = (performance.now() - started) / 1000;
  const deadline = Date.now() + 30000;
  while (!stats.every(state => state.count === total) && Date.now() < deadline) await delay(100);
  const elapsedSeconds = (performance.now() - started) / 1000;
  measuring = false;
  const received = stats.reduce((sum, state) => sum + state.count, 0);
  let maxSkew = 0;
  for (let i = 0; i < total; i++) maxSkew = Math.max(maxSkew, lastArrival[i] - firstArrival[i]);
  const report = {
    clients, targetRate: rate, offeredRate: publisher ? sent / offeredSeconds : null,
    deliveredPerSecond: received / elapsedSeconds, expectedDeliveries: total * clients,
    received, missing: total * clients - received,
    duplicates: stats.reduce((sum, state) => sum + state.duplicates, 0),
    outOfOrder: stats.reduce((sum, state) => sum + state.outOfOrder, 0),
    p50Ms: percentile(.5), p99Ms: percentile(.99), maxLatencyMs: maxLatency,
    maxIngressLatencyMs: maxIngressLatency, maxAfterIngressLatencyMs: maxAfterIngressLatency,
    maxLocalArrivalSkewMs: maxSkew, disconnects, closeCodes, protocolErrors
  };
  report.passed = report.missing === 0 && report.outOfOrder === 0 && maxLatency <= 1000
    && !disconnects && !protocolErrors && (!publisher || report.offeredRate >= rate * .98);
  console.log(JSON.stringify(report, null, 2));
  if (!report.passed) process.exitCode = 1;
}

main().catch(error => { console.error(error); process.exitCode = 1; })
  .finally(async () => {
    measuring = false;
    await Promise.all(sockets.map(client => client.deactivate({ force: true })));
  });
