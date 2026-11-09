// 排队功能验收数据：node .agents/skills/verify-yoga/scripts/queue.mjs <setup|status|join <手机号>|cancel <手机号>|cleanup>
// 只操作本地开发库；会员 ID 固定在 1999000000000000001～007，手机号 1990000000x。
// 主排课为明天 15:00 的团课；停止排队排课为今天约 3 小时后开课、已过停止排队时间的团课；
// 排队失败排课紧接停止排队排课之后开课，验收账号排队后被改成已过停止排队时间；已结束排课是今天已开课的团课。
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
const MAIN = { maxPersons: 3, queueLimit: 3, queueStopMinutes: 110 };
const STOPPED = { maxPersons: 1, queueLimit: 3 };
const FAILED = { maxPersons: 1, queueLimit: 2, queueStopMinutes: 10 };
const FINISHED = { maxPersons: 5, queueLimit: 0, queueStopMinutes: 0 };

const MEMBERS = [
  { id: '1999000000000000001', phone: '19900000001', name: '验收会员A', role: 'reserve' },
  { id: '1999000000000000002', phone: '19900000002', name: '验收会员B', role: 'reserve' },
  { id: '1999000000000000003', phone: '19900000003', name: '验收会员C', role: 'reserve' },
  { id: '1999000000000000004', phone: '19900000004', name: '验收会员D', role: 'queue' },
  { id: '1999000000000000006', phone: '19900000006', name: '验收会员E', role: 'queue' },
  { id: '1999000000000000005', phone: '19900000005', name: '验收体验', role: 'you' },
  { id: '1999000000000000007', phone: '19900000007', name: '验收会员F', role: 'spare' },
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

const pad = (n) => String(n).padStart(2, '0');
const format = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
  + `${pad(d.getHours())}:${pad(d.getMinutes())}:00`;

function tomorrowAt(hour) {
  const d = new Date();
  d.setDate(d.getDate() + 1);
  d.setHours(hour, 0, 0, 0);
  return d;
}

/** 今天 3 小时后（取整到 5 分钟）开课、上 1 小时；跨过当天则改为明天 01:00 */
function stoppedSlot() {
  const start = new Date(Date.now() + 3 * 3600000);
  start.setMinutes(Math.ceil(start.getMinutes() / 5) * 5, 0, 0);
  const end = new Date(start.getTime() + 3600000);
  if (end.getDate() !== new Date().getDate() || end.getHours() === 0) {
    const fallback = tomorrowAt(1);
    return { start: fallback, end: new Date(fallback.getTime() + 3600000) };
  }
  return { start, end };
}

/** 排队失败排课：停止排队排课下课后接着上 1 小时，同一教练不能时间重叠；跨过当天则改为明天 02:00 */
function failedSlot(stopped) {
  const start = new Date(stopped.end.getTime());
  const end = new Date(start.getTime() + 3600000);
  if (end.getDate() !== start.getDate() || end.getHours() === 0) {
    const fallback = tomorrowAt(2);
    return { start: fallback, end: new Date(fallback.getTime() + 3600000) };
  }
  return { start, end };
}

/** 已结束排课的真实时间：今天 30 分钟前（向下取整到 5 分钟）开课、上 1 小时；不早于今天 00:00，不晚于今天 23:59 */
function finishedSlot() {
  const now = new Date();
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  let start = new Date(now.getTime() - 30 * 60000);
  start.setMinutes(Math.floor(start.getMinutes() / 5) * 5, 0, 0);
  if (start < midnight) start = midnight;
  let end = new Date(start.getTime() + 3600000);
  const lastMinute = new Date(midnight.getTime() + (24 * 60 - 1) * 60000);
  if (end > lastMinute) end = lastMinute;
  return { start, end };
}

/** 停止排队时间设为比距开课的分钟数更长，保证一造好就已过停止排队时间 */
function stoppedMinutes(start) {
  return Math.min(1440, Math.max(200, Math.ceil((start.getTime() - Date.now()) / 60000) + 30));
}

function findMain() {
  const rows = sql(`SELECT id FROM t_schedule WHERE classroom_id=${CLASSROOM_ID} AND coach_id=${COACH_ID}
    AND start_time='${format(tomorrowAt(15))}' AND max_persons=${MAIN.maxPersons} LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function findStopped() {
  const rows = sql(`SELECT id FROM t_schedule WHERE classroom_id=${CLASSROOM_ID} AND coach_id=${COACH_ID}
    AND max_persons=${STOPPED.maxPersons} AND queue_limit=${STOPPED.queueLimit} AND min_persons=1
    AND queue_stop_minutes>=200 AND start_time BETWEEN NOW() AND '${format(tomorrowAt(1))}' ORDER BY id DESC LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function findFailed() {
  const rows = sql(`SELECT id FROM t_schedule WHERE classroom_id=${CLASSROOM_ID} AND coach_id=${COACH_ID}
    AND max_persons=${FAILED.maxPersons} AND queue_limit=${FAILED.queueLimit} AND min_persons=1
    ORDER BY id DESC LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function findFinished() {
  const rows = sql(`SELECT id FROM t_schedule WHERE classroom_id=${CLASSROOM_ID} AND coach_id=${COACH_ID}
    AND max_persons=${FINISHED.maxPersons} AND queue_limit=${FINISHED.queueLimit} AND min_persons=1
    ORDER BY id DESC LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function cardOf(memberId) {
  return sql(`SELECT id FROM t_member_card WHERE member_id=${memberId} LIMIT 1;`)[0][0];
}

function memberOf(phone) {
  const member = MEMBERS.find((m) => m.phone === phone);
  if (!member) throw new Error(`只能操作验收会员：${MEMBERS.map((m) => m.phone).join(' ')}`);
  return member;
}

function cleanup() {
  const scheduleIds = sql(`SELECT DISTINCT schedule_id FROM t_reservation WHERE member_id IN (${MEMBER_IDS});`)
    .map((r) => r[0]);
  for (const own of [findMain(), findStopped(), findFailed(), findFinished()]) {
    if (own && !scheduleIds.includes(own)) scheduleIds.push(own);
  }
  sql(`DELETE FROM t_member_card_transaction WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_reservation WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member_card WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member WHERE id IN (${MEMBER_IDS});`);
  let deleted = 0;
  for (const id of scheduleIds) {
    const others = sql(`SELECT COUNT(*) FROM t_reservation WHERE schedule_id=${id};`)[0][0];
    if (others === '0') {
      sql(`DELETE FROM t_schedule WHERE id=${id};`);
      deleted += 1;
    }
  }
  console.log(`已清理验收会员、会员卡、预约与流水；删除排课 ${deleted} 节。`);
}

function scheduleBody(start, end, config) {
  return { storeId: STORE_ID, courseId: COURSE_ID, coachId: COACH_ID, classroomId: CLASSROOM_ID,
    startTime: format(start), endTime: format(end), maxPersons: config.maxPersons, minPersons: 1,
    queueLimit: config.queueLimit, queueStopMinutes: config.queueStopMinutes };
}

async function createSchedule(admin, start, end, config, find) {
  await call('POST', '/admin/schedules', admin, scheduleBody(start, end, config));
  const scheduleId = find();
  await call('PUT', `/admin/schedules/${scheduleId}/status?status=2`, admin);
  return scheduleId;
}

async function submit(member, scheduleId, queue) {
  await call('POST', queue ? '/api/reservations/queue' : '/api/reservations', await memberToken(member.phone),
    { requestId: randomUUID(), scheduleId, memberCardId: cardOf(member.id), persons: 1 });
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
    await call('PUT', `/admin/member-cards/${cardOf(m.id)}/activate`, admin);
  }

  const mainId = await createSchedule(admin, tomorrowAt(15), tomorrowAt(16), MAIN, findMain);
  for (const m of MEMBERS.filter((x) => x.role === 'reserve')) await submit(m, mainId, false);
  for (const m of MEMBERS.filter((x) => x.role === 'queue')) await submit(m, mainId, true);

  const slot = stoppedSlot();
  const stop = stoppedMinutes(slot.start);
  const stoppedId = await createSchedule(admin, slot.start, slot.end, { ...STOPPED, queueStopMinutes: stop },
    findStopped);
  await submit(MEMBERS[0], stoppedId, false);

  const you = MEMBERS.find((m) => m.role === 'you');
  const failed = failedSlot(slot);
  const failedStop = stoppedMinutes(failed.start);
  const failedId = await createSchedule(admin, failed.start, failed.end, FAILED, findFailed);
  await submit(MEMBERS[0], failedId, false);
  await submit(you, failedId, true);
  // 排队之后经管理端下架、改停止排队时间、重新上架，让这条排队记录跨过停止排队时间
  await call('PUT', `/admin/schedules/${failedId}/status?status=1`, admin);
  await call('PUT', `/admin/schedules/${failedId}`, admin,
    scheduleBody(failed.start, failed.end, { ...FAILED, queueStopMinutes: failedStop }));
  await call('PUT', `/admin/schedules/${failedId}/status?status=2`, admin);

  // 管理端只能排未开始的课：先按明天 18:00 新建并上架，再直接改库把时间挪到今天已开课，仅作验收数据
  const finishedId = await createSchedule(admin, tomorrowAt(18), tomorrowAt(19), FINISHED, findFinished);
  const done = finishedSlot();
  sql(`UPDATE t_schedule SET schedule_date='${format(done.start).slice(0, 10)}', start_time='${format(done.start)}',
    end_time='${format(done.end)}' WHERE id=${finishedId};`);

  console.log(`排课 ${mainId}：${format(tomorrowAt(15))}，最大 ${MAIN.maxPersons} 人，`
    + `排队上限 ${MAIN.queueLimit}，开课前 ${MAIN.queueStopMinutes} 分钟停止排队。`);
  console.log(`停止排队排课 ${stoppedId}：${format(slot.start)}，最大 ${STOPPED.maxPersons} 人，`
    + `开课前 ${stop} 分钟停止排队（已过）。`);
  console.log(`排队失败排课 ${failedId}：${format(failed.start)}，最大 ${FAILED.maxPersons} 人，A 已预约，`
    + `验收体验排队后停止排队时间改为开课前 ${failedStop} 分钟（已过）。`);
  console.log(`已结束排课 ${finishedId}：${format(done.start)} 开课（已开始），最大 ${FINISHED.maxPersons} 人。`);
  status();
}

function printSchedule(label, scheduleId) {
  const [[booked, max, limit, stop, start]] = sql(`SELECT booked_persons, max_persons, queue_limit,
    queue_stop_minutes, start_time FROM t_schedule WHERE id=${scheduleId};`);
  console.log(`${label} ${scheduleId}  ${start}  已预约 ${booked}/${max}  排队上限 ${limit}  开课前 ${stop} 分钟停止排队`);
  const text = { 1: '已预约', 2: '已取消', 3: '已签到', 4: '排队中', 5: '排队失败' };
  const rows = sql(`SELECT m.name, m.phone, r.status, c.remaining_times FROM t_reservation r
    JOIN t_member m ON m.id=r.member_id JOIN t_member_card c ON c.id=r.member_card_id
    WHERE r.schedule_id=${scheduleId} ORDER BY r.status, r.reservation_time, r.id;`);
  let position = 0;
  for (const [name, phone, code, times] of rows) {
    const tag = code === '4' ? `排队第 ${++position} 位` : text[code];
    console.log(`  ${name} ${phone}  ${tag}  卡剩余 ${times} 次`);
  }
  if (rows.some((r) => r[2] === '4')) console.log(`  排队 ${position}/${limit}`);
}

function status() {
  const mainId = findMain();
  if (!mainId) return console.log('没有验收排课，先执行 setup。');
  printSchedule('主排课', mainId);
  const stoppedId = findStopped();
  if (stoppedId) printSchedule('停止排队排课', stoppedId);
  const failedId = findFailed();
  if (failedId) printSchedule('排队失败排课', failedId);
  const finishedId = findFinished();
  if (finishedId) printSchedule('已结束排课', finishedId);
}

async function join(phone) {
  const member = memberOf(phone);
  try {
    await submit(member, findMain(), true);
    console.log(`${member.name} 排队成功。`);
  } catch (e) {
    console.log(`${member.name} 排队失败：${e.message}`);
  }
  status();
}

async function cancel(phone) {
  const member = memberOf(phone);
  const scheduleId = findMain();
  const rows = sql(`SELECT id FROM t_reservation WHERE schedule_id=${scheduleId} AND member_id=${member.id}
    AND status IN (1,4) LIMIT 1;`);
  if (!rows.length) throw new Error(`${member.name} 在这节课上没有可取消的预约或排队`);
  await call('PUT', `/api/reservations/${rows[0][0]}/cancel`, await memberToken(phone));
  console.log(`${member.name} 已取消。`);
  status();
}

const [command, arg] = process.argv.slice(2);
const actions = { setup, status, cleanup, join: () => join(arg), cancel: () => cancel(arg) };
if (!actions[command]) {
  console.log('用法：node .agents/skills/verify-yoga/scripts/queue.mjs <setup|status|join <手机号>|cancel <手机号>|cleanup>');
  process.exit(2);
}
try {
  await actions[command]();
} catch (e) {
  console.error(e.message);
  process.exit(1);
}
