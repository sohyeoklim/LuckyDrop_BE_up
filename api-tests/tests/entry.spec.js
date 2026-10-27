import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { seedEntryScenario } from '../helpers/seed.js';

test('중복 요청: 같은 키로 두 번 응모해도 차감과 응모는 한 번만 반영한다', async ({ request }) => {
  const { email, password, drawId } = await seedEntryScenario();
  const login = await request.post('/api/user/login', { data: { email, password } });
  expect(login.status(), login.ok() ? '' : await login.text()).toBe(200);

  async function readState() {
    const wallet = await request.get('/api/ticket/wallet/me');
    const entries = await request.get('/api/draws/my-entries');
    const ledger = await request.get('/api/ticket/ledger/me');
    expect(wallet.status()).toBe(200);
    expect(entries.status()).toBe(200);
    expect(ledger.status()).toBe(200);
    return {
      wallet: await wallet.json(),
      entries: await entries.json(),
      ledger: await ledger.json(),
    };
  }

  const initial = await readState();
  expect(initial.wallet.balance).toBe(10);
  expect(initial.entries.totalElements).toBe(0);
  expect(initial.ledger.totalElements).toBe(1);

  // Generate the key ONCE: both HTTP requests must reuse this same value.
  const key = randomUUID();
  const entryRequest = {
    headers: { 'Idempotency-Key': key },
    data: { count: 3 },
  };

  await test.step('첫 요청은 성공하고 6장을 차감한다', async () => {
    const first = await request.post(`/api/draws/${drawId}/entry`, entryRequest);
    expect(first.status()).toBe(200);
    expect(await first.json()).toMatchObject({ drawId, spentTicketsAdded: 6, entryTimesAdded: 3 });
  });

  const afterFirst = await readState();
  expect(afterFirst.wallet.balance).toBe(4);
  expect(afterFirst.entries.totalElements).toBe(1);
  expect(afterFirst.entries.content).toHaveLength(1);
  expect(afterFirst.entries.content[0]).toMatchObject({ drawId, entryCount: 3 });
  expect(afterFirst.ledger.totalElements).toBe(2);
  const uses = afterFirst.ledger.content.filter(item => item.type === 'USE');
  expect(uses).toHaveLength(1);
  expect(uses[0]).toMatchObject({ amount: -6, refType: 'DRAW', refId: drawId, idempotencyKey: key });

  await test.step('같은 키와 본문으로 재전송하면 중복 요청 오류', async () => {
    const second = await request.post(`/api/draws/${drawId}/entry`, entryRequest);
    expect(second.status()).toBe(400);
    // A generic 400 could also mean insufficient tickets; verify the actual reason.
    expect(await second.json()).toMatchObject({ message: '이미 처리된 요청입니다.' });
  });

  await test.step('재전송 후에도 잔액 4장, 응모 3회, 사용 원장 1건 유지', async () => {
    const afterSecond = await readState();
    expect(afterSecond.wallet).toEqual(afterFirst.wallet);
    expect(afterSecond.entries.totalElements).toBe(afterFirst.entries.totalElements);
    expect(afterSecond.entries.content).toEqual(afterFirst.entries.content);
    expect(afterSecond.ledger.totalElements).toBe(afterFirst.ledger.totalElements);
    expect(afterSecond.ledger.content).toEqual(afterFirst.ledger.content);
  });
});

test('티켓 부족: 12장이 필요한 응모를 거절하고 모든 내역을 유지한다', async ({ request }) => {
  const { email, password, drawId } = await seedEntryScenario();
  const login = await request.post('/api/user/login', { data: { email, password } });
  expect(login.status(), login.ok() ? '' : await login.text()).toBe(200);

  // Capture the actual state before the rejected request for comparison afterward.
  async function readState() {
    const wallet = await request.get('/api/ticket/wallet/me');
    const entries = await request.get('/api/draws/my-entries');
    const ledger = await request.get('/api/ticket/ledger/me');
    expect(wallet.status()).toBe(200);
    expect(entries.status()).toBe(200);
    expect(ledger.status()).toBe(200);
    return {
      wallet: await wallet.json(),
      entries: await entries.json(),
      ledger: await ledger.json(),
    };
  }

  const before = await readState();
  expect(before.wallet.balance).toBe(10);
  expect(before.entries.totalElements).toBe(0);
  expect(before.entries.content).toEqual([]);
  expect(before.ledger.totalElements).toBe(1);
  expect(before.ledger.content[0]).toMatchObject({ type: 'EARN', amount: 10 });

  await test.step('10장으로 12장이 필요한 응모를 시도하면 티켓 부족 오류', async () => {
    const response = await request.post(`/api/draws/${drawId}/entry`, {
      headers: { 'Idempotency-Key': randomUUID() },
      data: { count: 6 },
    });
    expect(response.status()).toBe(400);
    expect(await response.json()).toMatchObject({ message: '티켓이 부족합니다.' });
  });

  await test.step('실패 전후 잔액·응모 내역·원장이 동일함을 확인', async () => {
    const after = await readState();
    expect(after.wallet).toEqual(before.wallet);
    expect(after.entries.totalElements).toBe(0);
    expect(after.entries.content).toEqual(before.entries.content);
    expect(after.ledger.totalElements).toBe(before.ledger.totalElements);
    expect(after.ledger.content).toEqual(before.ledger.content);
    expect(after.ledger.content.filter(item => item.type === 'USE')).toEqual([]);
  });
});

test('정상 응모: 티켓 10장에서 6장 차감하고 응모 3회를 기록한다', async ({ request }) => {
  // Arrange: create independent data for every run, then authenticate through the real API.
  const { email, password, userId, drawId } = await seedEntryScenario();
  await test.step('로그인하고 초기 잔액 10장과 응모 이력 없음을 확인', async () => {
    const login = await request.post('/api/user/login', { data: { email, password } });
    expect(login.status(), login.ok() ? '' : await login.text()).toBe(200);
    // Playwright keeps the login response cookies in this request context.
    const wallet = await request.get('/api/ticket/wallet/me');
    expect(wallet.status()).toBe(200);
    expect(await wallet.json()).toMatchObject({ userId, balance: 10 });
    const entries = await request.get('/api/draws/my-entries');
    expect(entries.status()).toBe(200);
    expect((await entries.json()).totalElements).toBe(0);
  });

  const key = randomUUID();
  await test.step('1회당 2장인 드로우에 3회 응모', async () => {
    const response = await request.post(`/api/draws/${drawId}/entry`, {
      headers: { 'Idempotency-Key': key },
      data: { count: 3 },
    });
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({
      drawId, userId, spentTicketsAdded: 6, spentTicketsTotal: 6, entryTimesAdded: 3,
    });
  });

  await test.step('잔액 4장, 응모 3회, 사용 원장 -6장을 각각 조회해 검증', async () => {
    const wallet = await request.get('/api/ticket/wallet/me');
    expect(wallet.status()).toBe(200);
    expect((await wallet.json()).balance).toBe(4);

    const entries = await request.get('/api/draws/my-entries');
    expect(entries.status()).toBe(200);
    const entryPage = await entries.json();
    expect(entryPage.totalElements).toBe(1);
    expect(entryPage.content).toHaveLength(1);
    expect(entryPage.content[0]).toMatchObject({ drawId, entryCount: 3 });

    const ledger = await request.get('/api/ticket/ledger/me');
    expect(ledger.status()).toBe(200);
    const ledgerPage = await ledger.json();
    expect(ledgerPage.totalElements).toBe(2); // Initial grant + one entry debit.
    const uses = ledgerPage.content.filter(item => item.type === 'USE');
    expect(uses).toHaveLength(1);
    expect(uses[0]).toMatchObject({
      amount: -6, reason: 'DRAW_ENTRY', refType: 'DRAW', refId: drawId, idempotencyKey: key,
    });
  });
});
