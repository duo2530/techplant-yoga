// k6 容量压测脚本：读接口与写接口按 MIX 权重混合，恒定到达率 RATE 持续 DURATION。
//   MIX 形如 read=80,reserve=10,cancel=5,queue=3,checkin=2；只压读接口时为 read=100。
//   READ_MIX 是读请求在各读接口之间的权重。
// token 与可消耗池布局来自 export_tokens.py --set bulk 生成的 TOKENS_FILE，登录不在本脚本内发生。
// 可消耗池在 bulk_seed.py 每次运行时重建，可签到的预约在重建后约 4 小时内有效。
// 第 i 次迭代按 (i * STRIDE) mod W 落到权重表的一格，同一操作的第 k 次落到可消耗池的第 k 份数据，
// 各 VU 之间不会重复消耗同一条预约或同一组会员与排课。
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL;
const RATE = Number(__ENV.RATE || 50);
const DURATION = __ENV.DURATION || '1m';
const OFFSET = Number(__ENV.OFFSET || 0);
const MIX = parseWeights(__ENV.MIX || 'read=80,reserve=10,cancel=5,queue=3,checkin=2');
const READ_MIX = parseWeights(__ENV.READ_MIX || 'schedules=20,schedule_detail=15,today_bookable=10,hot=10,'
  + 'stores=5,coaches_featured=5,coach_detail=5,coach_schedules=10,my_reservations=12,my_cards=8');

const data = new SharedArray('bulk', () => [JSON.parse(open(__ENV.TOKENS_FILE))]);
const LAYOUT = data[0].layout;
const POOL = data[0].pool;
const HISTORY = data[0].history;

const WRITE_OPS = ['reserve', 'cancel', 'queue', 'checkin'];
const READ_OPS = Object.keys(READ_MIX.weights);
const ENDPOINTS = READ_OPS.concat(WRITE_OPS);

const httpErrors = new Rate('http_errors');
const bizErrors = new Rate('biz_errors');
const bizOk = new Counter('biz_ok');
const conflictOther = new Counter('conflict_other');
// 预约、排队、取消与签到会返回的 409 文案，未列出的计入 conflict_other 并打印
const CONFLICT_KEYS = {
  '剩余名额不足，不能预约': 'seats_full',
  '剩余名额不足，预约失败': 'seats_full_on_update',
  '您已预约该课程': 'already_reserved',
  '您已在排队中': 'already_queued',
  '会员卡剩余次数不足': 'times_insufficient',
  '请勿重复提交，请稍后重试': 'duplicate_submit',
  '课程还有空位，请直接预约': 'queue_has_seats',
  '排队人数已满': 'queue_full',
  '已超过停止排队时间，不能排队': 'queue_stopped',
  '已签到的预约不能取消': 'cancel_checked_in',
  '已超过取消截止时间，不能取消预约': 'cancel_deadline',
  '已取消的预约不能签到': 'checkin_cancelled',
  '未到签到时间': 'checkin_too_early',
  '课程已结束，不能签到': 'checkin_ended',
  '未预约成功，不能签到': 'checkin_not_reserved',
  '只能预约今天起 14 天内的课程': 'out_of_window',
  '排课已开始或已结束，不能预约': 'schedule_started',
  '会员卡有效期不覆盖本次课程，不能预约': 'card_expired_for_course',
  '预约状态已变更，请刷新后重试': 'status_changed',
};
const conflicts = {};
Object.values(CONFLICT_KEYS).forEach((k) => { conflicts[k] = new Counter(`conflict_${k}`); });

function parseWeights(text) {
  const weights = {};
  text.split(',').map((x) => x.trim()).filter((x) => x).forEach((x) => {
    const [k, v] = x.split('=');
    if (Number(v) > 0) {
      weights[k.trim()] = Number(v);
    }
  });
  const table = [];
  Object.keys(weights).forEach((k) => { for (let j = 0; j < weights[k]; j++) table.push(k); });
  if (!table.length) {
    throw new Error(`权重为空: ${text}`);
  }
  let stride = Math.max(1, Math.floor(table.length * 0.618));
  while (gcd(stride, table.length) !== 1) stride++;
  // 每格之前同名操作的个数，用于把迭代序号换算成该操作自己的序号
  const rank = [];
  const seen = {};
  table.forEach((k) => { rank.push(seen[k] || 0); seen[k] = (seen[k] || 0) + 1; });
  return { weights, table, stride, rank, total: table.length };
}

function gcd(a, b) {
  return b ? gcd(b, a % b) : a;
}

/** 第 i 次迭代对应的操作及该操作的序号 */
function pick(mix, i) {
  const slot = (i * mix.stride) % mix.total;
  const op = mix.table[slot];
  return { op, k: Math.floor(i / mix.total) * mix.weights[op] + mix.rank[slot] };
}

function thresholdsFor() {
  const t = {};
  ENDPOINTS.forEach((ep) => {
    t[`http_req_duration{ep:${ep}}`] = [];
    t[`http_reqs{ep:${ep}}`] = [];
    t[`http_errors{ep:${ep}}`] = [];
    t[`biz_errors{ep:${ep}}`] = [];
  });
  return t;
}

export const options = {
  scenarios: {
    capacity: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: Math.max(20, RATE * 2), maxVUs: Math.min(3000, Math.max(100, RATE * 5)),
    },
  },
  thresholds: thresholdsFor(),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function hash(n) {
  let x = (n + 0x9e3779b9) | 0;
  x = Math.imul(x ^ (x >>> 16), 0x85ebca6b);
  x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35);
  return (x ^ (x >>> 16)) >>> 0;
}

function uuid() {
  const hex = '0123456789abcdef';
  let s = '';
  for (let j = 0; j < 32; j++) s += hex[Math.floor(Math.random() * 16)];
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-4${s.slice(13, 16)}-a${s.slice(17, 20)}-${s.slice(20)}`;
}

function dateText(offsetDays) {
  // 后端按东八区计算“今天”，这里不依赖压测机时区
  const d = new Date(Date.now() + 8 * 3600 * 1000 + offsetDays * 86400 * 1000);
  return d.toISOString().slice(0, 10);
}

function poolMember(b, u) {
  return u * LAYOUT.pool_stores + b;
}

function auth(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

function readRequest(ep, n) {
  const h = hash(n);
  const store = LAYOUT.store_base + (h % LAYOUT.stores);
  const coach = LAYOUT.coach_base + (h % LAYOUT.coaches);
  const user = HISTORY[h % HISTORY.length];
  switch (ep) {
    case 'schedules':
      return ['GET', `/api/schedules?storeId=${store}&courseType=1&date=${dateText(h % 14)}&pageNum=1&pageSize=10`];
    case 'schedule_detail':
      return ['GET', `/api/schedules/${LAYOUT.schedule_base + (h % LAYOUT.schedules)}`];
    case 'today_bookable':
      return ['GET', `/api/schedules/today-bookable?storeId=${store}`];
    case 'hot':
      return ['GET', `/api/schedules/hot?storeId=${store}`];
    case 'stores':
      return ['GET', `/api/stores?pageNum=${1 + (h % 3)}&pageSize=20`];
    case 'coaches_featured':
      return ['GET', '/api/coaches/featured'];
    case 'coach_detail':
      return ['GET', `/api/coaches/${coach}`];
    case 'coach_schedules':
      return ['GET', `/api/coaches/${coach}/schedules?storeId=${LAYOUT.store_base + Math.floor((h % LAYOUT.coaches)
        / LAYOUT.coaches_per_store)}&courseType=1&pageNum=1&pageSize=10`];
    case 'my_reservations':
      return ['GET', `/api/reservations?pageNum=1&pageSize=10${h % 3 === 0 ? '&status=3' : ''}`, user[1]];
    case 'my_cards':
      return ['GET', '/api/member-cards?pageNum=1&pageSize=10', user[1]];
    default:
      throw new Error(`未知读接口 ${ep}`);
  }
}

/** 写操作的第 k 次落到可消耗池的哪条数据，映射与 bulk_seed.seed_pool 一致 */
function writeRequest(op, k) {
  const S = LAYOUT.pool_stores;
  const Mb = LAYOUT.pool_members_per_store;
  const b = k % S;
  const rest = Math.floor(k / S);
  if (op === 'reserve') {
    const u = rest % Mb;
    const r = Math.floor(rest / Mb) % LAYOUT.reserve_schedules;
    const m = poolMember(b, u);
    const body = `{"requestId":"${uuid()}","scheduleId":${LAYOUT.pool_schedule_base + b * 10000 + r},`
      + `"memberCardId":${LAYOUT.pool_card_base + m},"persons":1}`;
    return ['POST', '/api/reservations', POOL[m], body];
  }
  if (op === 'queue') {
    const Q = LAYOUT.queue_schedules;
    const r = rest % Q;
    const u = ((r % Mb) + 1 + (Math.floor(rest / Q) % LAYOUT.queue_limit)) % Mb;
    const m = poolMember(b, u);
    const body = `{"requestId":"${uuid()}","scheduleId":${LAYOUT.pool_schedule_base + 1000000 + b * 10000 + r},`
      + `"memberCardId":${LAYOUT.pool_card_base + m},"persons":1}`;
    return ['POST', '/api/reservations/queue', POOL[m], body];
  }
  const count = op === 'cancel' ? LAYOUT.cancel : LAYOUT.checkin;
  const q = k % count;
  const m = poolMember(q % S, Math.floor(q / S) % Mb);
  const rid = LAYOUT.pool_reservation_base + (op === 'cancel' ? 0 : 100000000) + q;
  return ['PUT', `/api/reservations/${rid}/${op === 'cancel' ? 'cancel' : 'check-in'}`, POOL[m], null];
}

export default function () {
  const i = exec.scenario.iterationInTest + OFFSET;
  let { op, k } = pick(MIX, i);
  let req;
  if (op === 'read') {
    op = pick(READ_MIX, k).op;
    req = readRequest(op, k);
  } else {
    req = writeRequest(op, k);
  }
  const [method, path, token, body] = req;
  const params = { headers: token ? auth(token) : {}, timeout: '10s', tags: { ep: op, name: op } };
  const res = http.request(method, `${BASE_URL}${path}`, body, params);

  let code = null;
  let msg = '';
  try {
    const parsed = res.json();
    code = parsed.code;
    msg = parsed.msg;
  } catch (e) {
    code = null;
  }
  const failed = res.status === 0 || res.status >= 500 || code === 500;
  httpErrors.add(failed, { ep: op });
  if (failed) {
    return;
  }
  bizErrors.add(code !== 200, { ep: op });
  if (code === 200) {
    bizOk.add(1, { ep: op });
  } else if (code === 409 && CONFLICT_KEYS[msg]) {
    conflicts[CONFLICT_KEYS[msg]].add(1, { ep: op });
  } else {
    conflictOther.add(1, { ep: op });
    console.warn(`非成功响应: ep=${op} status=${res.status} code=${code} msg=${msg}`);
  }
}
