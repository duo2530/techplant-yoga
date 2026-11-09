// 排队功能验收数据：node .agents/skills/verify-yoga/scripts/queue.mjs <setup|status|cancel <手机号>|cleanup>
// 只操作本地开发库；会员 ID 固定在 1999000000000000001～005，手机号 1990000000x，课程为明天 15:00 的团课。
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const API = process.env.UAT_API_URL || 'http://localhost:8080';
const MYSQL_CONTAINER = process.env.UAT_MYSQL_CONTAINER || 'yoga-mysql';
const PASSWORD = 'yoga1234';
const PASSWORD_HASH = '$2a$10$3STsWqV/E3dCLbLtS0qy3OznVw1eBCbP.94Sb.Ny5kAothF1QwMdy';
const STORE_ID = '1856739201475235901';
const COURSE_ID = '1856739201475235801';
const COACH_ID = '1856739201475237001';
const CLASSROOM_ID = '1856739201475236001';
const MAX_PERSONS = 3;

const MEMBERS = [
  { id: '1999000000000000001', phone: '19900000001', name: '验收会员A', role: 'reserve' },
  { id: '1999000000000000002', phone: '19900000002', name: '验收会员B', role: 'reserve' },
  { id: '1999000000000000003', phone: '19900000003', name: '验收会员C', role: 'reserve' },
  { id: '1999000000000000004', phone: '19900000004', name: '验收会员D', role: 'queue' },
  { id: '1999000000000000005', phone: '19900000005', name: '验收体验', role: 'you' },
];
const MEMBER_IDS = MEMBERS.map((m) => m.id).join(',');

function sql(statement) {
  const out = execFileSync('docker', ['exec', '-i', MYSQL_CONTAINER, 'mysql', '-uroot', '-p123456',
    '--default-character-set=utf8mb4', '-N', '-B', 'yoga'], { input: statement, encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'ignore'] });
  return out.trim() ? out.trim().split('\n').map((line) => line.split('\t')) : [];
}

async function call(method, path, token, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const json = await res.json();
  if (json.code !== 200) throw new Error(`${method} ${path} -> ${json.code} ${json.msg}`);
  return json;
}

async function adminToken() {
  return (await call('POST', '/login', null, { username: 'admin', password: 'admin123' })).token;
}

async function memberToken(phone) {
  return (await call('POST', '/api/auth/login', null, { phone, password: PASSWORD })).data.token;
}

function tomorrowAt(hour) {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(hour)}:00:00`;
}

function findSchedule() {
  const rows = sql(`SELECT id FROM t_schedule WHERE classroom_id=${CLASSROOM_ID} AND coach_id=${COACH_ID}
    AND start_time='${tomorrowAt(15)}' AND max_persons=${MAX_PERSONS} LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function cardOf(memberId) {
  return sql(`SELECT id FROM t_member_card WHERE member_id=${memberId} LIMIT 1;`)[0][0];
}

function cleanup() {
  const scheduleIds = sql(`SELECT DISTINCT schedule_id FROM t_reservation WHERE member_id IN (${MEMBER_IDS});`)
    .map((r) => r[0]);
  const own = findSchedule();
  if (own && !scheduleIds.includes(own)) scheduleIds.push(own);
  sql(`DELETE FROM t_member_card_transaction WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_reservation WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member_card WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member WHERE id IN (${MEMBER_IDS});`);
  for (const id of scheduleIds) {
    const others = sql(`SELECT COUNT(*) FROM t_reservation WHERE schedule_id=${id};`)[0][0];
    if (others === '0') sql(`DELETE FROM t_schedule WHERE id=${id};`);
  }
  console.log(`已清理验收会员、会员卡、预约与流水；删除排课 ${scheduleIds.length} 节。`);
}

async function setup() {
  cleanup();
  const values = MEMBERS.map((m) => `(${m.id}, '${m.phone}', '${PASSWORD_HASH}', '${m.phone.slice(-4)}',
    '${m.name}', 0, 0, 1, 1)`).join(',');
  sql(`INSERT INTO t_member (id, phone, password, phone_last4, name, gender, level, create_by, update_by)
    VALUES ${values};`);

  const admin = await adminToken();
  for (const m of MEMBERS) {
    // 卡号按门店与当前秒生成，同一秒内连续发卡会冲突
    await new Promise((r) => setTimeout(r, 1100));
    await call('POST', '/admin/member-cards', admin, { memberId: m.id, openStoreId: STORE_ID, cardName: '验收团课次卡',
      cardType: 1, courseScope: 1, storeId: STORE_ID, purchaseTimes: 10, giftTimes: 0, validPeriodCount: 3 });
    m.cardId = cardOf(m.id);
    await call('PUT', `/admin/member-cards/${m.cardId}/activate`, admin);
  }

  await call('POST', '/admin/schedules', admin, { storeId: STORE_ID, courseId: COURSE_ID, coachId: COACH_ID,
    classroomId: CLASSROOM_ID, startTime: tomorrowAt(15), endTime: tomorrowAt(16), maxPersons: MAX_PERSONS,
    minPersons: 1 });
  const scheduleId = findSchedule();
  await call('PUT', `/admin/schedules/${scheduleId}/status?status=2`, admin);

  for (const m of MEMBERS.filter((x) => x.role !== 'you')) {
    const path = m.role === 'reserve' ? '/api/reservations' : '/api/reservations/queue';
    await call('POST', path, await memberToken(m.phone),
      { requestId: randomUUID(), scheduleId, memberCardId: m.cardId, persons: 1 });
  }
  console.log(`排课 ${scheduleId}：${tomorrowAt(15)}，最大 ${MAX_PERSONS} 人。`);
  status();
}

function status() {
  const scheduleId = findSchedule();
  if (!scheduleId) return console.log('没有验收排课，先执行 setup。');
  const [[booked, max]] = sql(`SELECT booked_persons, max_persons FROM t_schedule WHERE id=${scheduleId};`);
  console.log(`已预约 ${booked}/${max}`);
  const text = { 1: '已预约', 2: '已取消', 3: '已签到', 4: '排队中', 5: '排队失败' };
  const rows = sql(`SELECT m.name, m.phone, r.status, c.remaining_times FROM t_reservation r
    JOIN t_member m ON m.id=r.member_id JOIN t_member_card c ON c.id=r.member_card_id
    WHERE r.schedule_id=${scheduleId} ORDER BY r.status, r.reservation_time, r.id;`);
  let position = 0;
  for (const [name, phone, code, times] of rows) {
    const tag = code === '4' ? `排队第 ${++position} 位` : text[code];
    console.log(`  ${name} ${phone}  ${tag}  卡剩余 ${times} 次`);
  }
}

async function cancel(phone) {
  const member = MEMBERS.find((m) => m.phone === phone);
  if (!member) throw new Error(`只能取消验收会员：${MEMBERS.map((m) => m.phone).join(' ')}`);
  const scheduleId = findSchedule();
  const rows = sql(`SELECT id FROM t_reservation WHERE schedule_id=${scheduleId} AND member_id=${member.id}
    AND status IN (1,4) LIMIT 1;`);
  if (!rows.length) throw new Error(`${member.name} 在这节课上没有可取消的预约或排队`);
  await call('PUT', `/api/reservations/${rows[0][0]}/cancel`, await memberToken(phone));
  console.log(`${member.name} 已取消。`);
  status();
}

const [command, arg] = process.argv.slice(2);
const actions = { setup, status, cleanup, cancel: () => cancel(arg) };
if (!actions[command]) {
  console.log('用法：node .agents/skills/verify-yoga/scripts/queue.mjs <setup|status|cancel <手机号>|cleanup>');
  process.exit(2);
}
try {
  await actions[command]();
} catch (e) {
  console.error(e.message);
  process.exit(1);
}
