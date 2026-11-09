// 人数不足自动取消验收数据：node .agents/skills/verify-yoga/scripts/auto-cancel.mjs <setup|status|run|cleanup>
// 只操作本地开发库；复用 queue.mjs 的验收会员（ID 1999000000000000001～003、005），两个脚本的 setup 会互相清掉对方的数据。
// 两节团课都在约 125 分钟后开课：不足排课最低 3 人只约了 2 人，对照排课最低 2 人约满 2 人。
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const API = process.env.UAT_API_URL || 'http://localhost:8080';
const MYSQL_CONTAINER = process.env.UAT_MYSQL_CONTAINER || 'yoga-mysql';
const PASSWORD = 'yoga1234';
const PASSWORD_HASH = '$2a$10$3STsWqV/E3dCLbLtS0qy3OznVw1eBCbP.94Sb.Ny5kAothF1QwMdy';
const STORE_ID = '1856739201475235901';
const COURSE_ID = '1856739201475235801';
const JOB = { jobId: 100, jobGroup: 'DEFAULT' };
const LEAD_MINUTES = 125;
const WINDOW_MINUTES = 120;
const UNDERSTAFFED = { label: '不足排课', coachId: '1856739201475237001', classroomId: '1856739201475236001',
  durationMinutes: 60, maxPersons: 5, minPersons: 3 };
const CONTROL = { label: '对照排课', coachId: '1856739201475237002', classroomId: '1856739201475236002',
  durationMinutes: 55, maxPersons: 5, minPersons: 2 };

const MEMBERS = [
  { id: '1999000000000000005', phone: '19900000005', name: '验收体验', schedule: UNDERSTAFFED },
  { id: '1999000000000000003', phone: '19900000003', name: '验收会员C', schedule: UNDERSTAFFED },
  { id: '1999000000000000001', phone: '19900000001', name: '验收会员A', schedule: CONTROL },
  { id: '1999000000000000002', phone: '19900000002', name: '验收会员B', schedule: CONTROL },
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
const addMinutes = (d, minutes) => new Date(d.getTime() + minutes * 60000);

/** 现在起 125 分钟后向上取整到分钟；排课须在开课当天内下课 */
function slotStart() {
  const start = addMinutes(new Date(), LEAD_MINUTES);
  if (start.getSeconds() || start.getMilliseconds()) start.setMinutes(start.getMinutes() + 1, 0, 0);
  const latestEnd = addMinutes(start, Math.max(UNDERSTAFFED.durationMinutes, CONTROL.durationMinutes));
  if (latestEnd.getDate() !== start.getDate()) {
    throw new Error(`开课时间 ${format(start)} 起的课会跨过午夜，请在 21:00 之前或 22:00 之后执行 setup`);
  }
  return start;
}

function findSchedule(config) {
  const rows = sql(`SELECT id FROM t_schedule WHERE coach_id=${config.coachId} AND classroom_id=${config.classroomId}
    AND course_id=${COURSE_ID} AND max_persons=${config.maxPersons} AND min_persons=${config.minPersons}
    AND schedule_date BETWEEN CURDATE() AND CURDATE() + INTERVAL 1 DAY AND TIMESTAMPDIFF(MINUTE, start_time, end_time)=${config.durationMinutes}
    ORDER BY id DESC LIMIT 1;`);
  return rows.length ? rows[0][0] : null;
}

function cardOf(memberId) {
  return sql(`SELECT id FROM t_member_card WHERE member_id=${memberId} LIMIT 1;`)[0][0];
}

function cleanup() {
  const scheduleIds = sql(`SELECT DISTINCT schedule_id FROM t_reservation WHERE member_id IN (${MEMBER_IDS});`)
    .map((r) => r[0]);
  for (const own of [findSchedule(UNDERSTAFFED), findSchedule(CONTROL)]) {
    if (own && !scheduleIds.includes(own)) scheduleIds.push(own);
  }
  sql(`DELETE FROM t_member_card_transaction WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_reservation WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member_card WHERE member_id IN (${MEMBER_IDS});
    DELETE FROM t_member WHERE id IN (${MEMBER_IDS});`);
  let deleted = 0;
  for (const id of scheduleIds) {
    if (sql(`SELECT COUNT(*) FROM t_reservation WHERE schedule_id=${id};`)[0][0] === '0') {
      sql(`DELETE FROM t_schedule WHERE id=${id};`);
      deleted += 1;
    }
  }
  console.log(`已清理验收会员、会员卡、预约与流水；删除排课 ${deleted} 节。`);
}

async function createSchedule(admin, config, start) {
  await call('POST', '/admin/schedules', admin, { storeId: STORE_ID, courseId: COURSE_ID, coachId: config.coachId,
    classroomId: config.classroomId, startTime: format(start), endTime: format(addMinutes(start, config.durationMinutes)),
    maxPersons: config.maxPersons, minPersons: config.minPersons, queueLimit: 3, queueStopMinutes: 110 });
  const scheduleId = findSchedule(config);
  await call('PUT', `/admin/schedules/${scheduleId}/status?status=2`, admin);
  return scheduleId;
}

async function setup() {
  const start = slotStart();
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

  const ids = new Map();
  for (const config of [UNDERSTAFFED, CONTROL]) ids.set(config, await createSchedule(admin, config, start));
  for (const m of MEMBERS) {
    await call('POST', '/api/reservations', await memberToken(m.phone),
      { requestId: randomUUID(), scheduleId: ids.get(m.schedule), memberCardId: cardOf(m.id), persons: 1 });
  }

  const windowStart = addMinutes(start, -WINDOW_MINUTES);
  console.log(`不足排课 ${ids.get(UNDERSTAFFED)}：${format(start)} 开课，最低 ${UNDERSTAFFED.minPersons} 人，已约 2 人。`);
  console.log(`对照排课 ${ids.get(CONTROL)}：${format(start)} 开课，最低 ${CONTROL.minPersons} 人，已约 2 人。`);
  console.log(`${format(windowStart)} 进入开课前 ${WINDOW_MINUTES} 分钟；此后第一次整分钟执行（或 run）取消不足排课。`
    + `在此之前 run 不应取消任何排课。`);
  status();
}

function printSchedule(config) {
  const id = findSchedule(config);
  if (!id) return console.log(`${config.label} 不存在，先执行 setup。`);
  const [[start, booked, min, code]] = sql(`SELECT start_time, booked_persons, min_persons, status
    FROM t_schedule WHERE id=${id};`);
  const statusText = { 1: '待上架', 2: '已上架', 3: '已取消' };
  console.log(`${config.label} ${id}  ${start}  ${statusText[code]}  已预约 ${booked}  最低 ${min}`);
  const text = { 1: '已预约', 2: '已取消', 3: '已签到', 4: '排队中', 5: '排队失败' };
  const reason = { 1: '会员主动取消', 2: '排课取消' };
  const rows = sql(`SELECT m.name, m.phone, r.status, IFNULL(r.cancel_reason, ''), c.remaining_times,
    (SELECT COUNT(*) FROM t_member_card_transaction t WHERE t.reservation_id=r.id AND t.change_type=2)
    FROM t_reservation r JOIN t_member m ON m.id=r.member_id JOIN t_member_card c ON c.id=r.member_card_id
    WHERE r.schedule_id=${id} ORDER BY r.reservation_time, r.id;`);
  for (const [name, phone, code2, cancelReason, times, refunds] of rows) {
    const why = cancelReason ? `（${reason[cancelReason]}）` : '';
    console.log(`  ${name} ${phone}  ${text[code2]}${why}  卡剩余 ${times} 次  返次流水 ${refunds} 条`);
  }
}

function status() {
  const job = sql(`SELECT invoke_target, cron_expression, status FROM sys_job WHERE job_id=${JOB.jobId};`);
  console.log(job.length
    ? `定时任务 ${JOB.jobId}：${job[0][0]}  ${job[0][1]}  ${job[0][2] === '0' ? '正常' : '暂停'}`
    : `定时任务 ${JOB.jobId} 不存在：先执行 backend/sql/migrations/20261109-schedule-auto-cancel-job.sql 并重启后端。`);
  console.log(`现在 ${format(new Date())}`);
  printSchedule(UNDERSTAFFED);
  printSchedule(CONTROL);
}

async function run() {
  await call('PUT', '/monitor/job/run', await adminToken(), JOB);
  console.log(`已触发定时任务 ${JOB.jobId} 执行一次。`);
  await new Promise((r) => setTimeout(r, 2000));
  status();
}

const [command] = process.argv.slice(2);
const actions = { setup, status, run, cleanup };
if (!actions[command]) {
  console.log('用法：node .agents/skills/verify-yoga/scripts/auto-cancel.mjs <setup|status|run|cleanup>');
  process.exit(2);
}
try {
  await actions[command]();
} catch (e) {
  console.error(e.message);
  process.exit(1);
}
