import { randomUUID } from 'node:crypto';
import bcrypt from 'bcryptjs';
import mysql from 'mysql2/promise';

// Intentionally fixed to the isolated compose.api-test.yml database.
// Never point this fixture helper at a development or production database.
export async function seedEntryScenario() {
  const db = await mysql.createConnection({
    host: '127.0.0.1', port: 13306,
    user: 'luckydrop_test', password: 'local-test-only',
    database: 'luckydrop_api_test',
  });
  const run = randomUUID().replaceAll('-', '').slice(0, 12);
  const email = `entry-${run}@example.invalid`;
  const password = 'ApiTest-only-123!';
  const hash = `{bcrypt}${await bcrypt.hash(password, 10)}`;
  const today = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());

  try {
    await db.beginTransaction();
    async function addUser(prefix, role) {
      const [result] = await db.execute(
        `INSERT INTO user (name, nickname, email, password, phone, role, status,
          auth_provider, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', 'LOCAL', NOW(), NOW())`,
        ['API test', `${prefix}${run}`, prefix === 'u' ? email : `host-${run}@example.invalid`,
          hash, '01000000000', role],
      );
      return result.insertId;
    }
    const userId = await addUser('u', 'USER');
    const hostId = await addUser('h', 'HOST');
    await db.execute(
      'INSERT INTO ticket_wallet (user_id, balance, created_at, updated_at) VALUES (?, 10, NOW(), NOW())',
      [userId],
    );
    await db.execute(
      `INSERT INTO ticket_ledger (user_id, type, amount, reason, ref_type, idempotency_key, created_at, updated_at)
       VALUES (?, 'EARN', 10, 'API_TEST_SETUP', 'TEST', ?, NOW(), NOW())`,
      [userId, `setup-${run}`],
    );
    // Synthetic prerequisite: rewards already claimed. Reward behavior is a separate test.
    for (const [code, period] of [['DRAW_FIRST', 'LIFETIME'], ['DRAW_DAILY', today]]) {
      const [missions] = await db.execute('SELECT id FROM mission WHERE code = ?', [code]);
      if (missions.length !== 1) throw new Error(`Required mission missing: ${code}. Start the api-test server first.`);
      await db.execute(
        `INSERT INTO user_mission (user_id, mission_id, period_key, progress_count,
          completed_at, rewarded_at, created_at, updated_at)
         VALUES (?, ?, ?, 1, NOW(), NOW(), NOW(), NOW())`,
        [userId, missions[0].id, period],
      );
    }
    const [inventory] = await db.execute(
      "INSERT INTO inventory (brand, name, shippable) VALUES ('API TEST', ?, false)",
      [`Fixture ${run}`],
    );
    const [draw] = await db.execute(
      `INSERT INTO draw (user_id, inventory_id, title, winner_count, ticket_cost_entry,
        start_at, end_at, status, end_at_changed, created_at, updated_at)
       VALUES (?, ?, ?, 1, 2, DATE_SUB(NOW(), INTERVAL 1 DAY),
         DATE_ADD(NOW(), INTERVAL 1 DAY), 'ACTIVE', false, NOW(), NOW())`,
      [hostId, inventory.insertId, `API test ${run}`],
    );
    await db.execute(
      `INSERT INTO draw_metrics (draw_id, bookmark_count, entry_count, participant_count, created_at, updated_at)
       VALUES (?, 0, 0, 0, NOW(), NOW())`,
      [draw.insertId],
    );
    await db.commit();
    return { email, password, userId, drawId: draw.insertId };
  } catch (error) {
    await db.rollback();
    throw error;
  } finally {
    await db.end();
  }
}
