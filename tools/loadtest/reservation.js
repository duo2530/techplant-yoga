// k6 预约压测脚本。场景由环境变量 SCENARIO 选择：
//   A / C / WARMUP：恒定到达率 RATE（次/秒）持续 DURATION，请求 k 由会员 k%M 预约排课 (k + floor(k/M)) % S，
//                   M+1 与 S 互素时前 M*S 个请求的会员与排课组合互不重复，相邻请求落在不同会员和不同排课上。
//   B：前 B_MEMBERS 个会员各预约一次同一节排课 B_SCHEDULE_ID，B_SECONDS 秒内匀速到达；B_SECONDS=0 时同时发出。
// 业务结果按响应体 code 区分：200 成功，409 按 msg 归类计数，其余计为非预期；错误口径见 README。
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL;
const SCENARIO = __ENV.SCENARIO;
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '10m';
const SCHEDULE_BASE = Number(__ENV.SCHEDULE_BASE);
const SCHEDULE_COUNT = Number(__ENV.SCHEDULE_COUNT);
const B_SCHEDULE_ID = __ENV.B_SCHEDULE_ID;
const B_MEMBERS = Number(__ENV.B_MEMBERS || 500);
const B_SECONDS = Number(__ENV.B_SECONDS || 10);
// 场景 C 每一级是一次独立的 k6 运行，OFFSET 让各级接着上一级的请求序号，避免重复的会员与排课组合
const OFFSET = Number(__ENV.OFFSET || 0);

const tokens = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS_FILE)));

const errors = new Rate('errors');
const reserved = new Counter('reserved');
const reservedDuration = new Trend('reserved_duration', true);
const unexpected = new Counter('unexpected');
const CONFLICTS = {
  '剩余名额不足，不能预约': new Counter('conflict_seats_full'),
  '剩余名额不足，预约失败': new Counter('conflict_seats_full_on_update'),
  '您已预约该课程': new Counter('conflict_already_reserved'),
  '会员卡剩余次数不足': new Counter('conflict_times_insufficient'),
  '请勿重复提交，请稍后重试': new Counter('conflict_duplicate_submit'),
};
const conflictOther = new Counter('conflict_other');
const conflictTotal = new Counter('conflict_total');

function scenarios() {
  if (SCENARIO === 'B' && B_SECONDS === 0) {
    return { B: { executor: 'per-vu-iterations', vus: B_MEMBERS, iterations: 1, maxDuration: '60s' } };
  }
  if (SCENARIO === 'B') {
    return {
      B: {
        executor: 'constant-arrival-rate', rate: B_MEMBERS / B_SECONDS, timeUnit: '1s', duration: `${B_SECONDS}s`,
        preAllocatedVUs: B_MEMBERS, maxVUs: B_MEMBERS,
      },
    };
  }
  return {
    [SCENARIO]: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      // VU 用尽时 k6 记为 dropped_iterations，场景 C 据此判定到达率未达标
      preAllocatedVUs: Math.max(50, RATE * 2), maxVUs: Math.min(3000, Math.max(200, RATE * 5)),
    },
  };
}

export const options = {
  scenarios: scenarios(),
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  discardResponseBodies: false,
  noConnectionReuse: false,
};

function target(k) {
  if (SCENARIO === 'B') {
    return { member: k, schedule: B_SCHEDULE_ID };
  }
  const m = tokens.length;
  return { member: k % m, schedule: String(SCHEDULE_BASE + ((k + Math.floor(k / m)) % SCHEDULE_COUNT)) };
}

function uuid() {
  const hex = '0123456789abcdef';
  let s = '';
  for (let i = 0; i < 32; i++) {
    s += hex[Math.floor(Math.random() * 16)];
  }
  return `${s.slice(0, 8)}-${s.slice(8, 12)}-4${s.slice(13, 16)}-a${s.slice(17, 20)}-${s.slice(20)}`;
}

export default function () {
  const i = exec.scenario.iterationInTest;
  if (SCENARIO === 'B' && i >= B_MEMBERS) {
    return;
  }
  const t = target(SCENARIO === 'B' ? i : i + OFFSET);
  const [memberId, cardId, token] = tokens[t.member];
  // 排课与会员卡 ID 超过 JS 安全整数范围时会丢精度，所以请求体按字符串拼接数字
  const body = `{"requestId":"${uuid()}","scheduleId":${t.schedule},"memberCardId":${cardId},"persons":1}`;
  const res = http.post(`${BASE_URL}/api/reservations`, body, {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    timeout: '10s',
    tags: { name: 'reserve' },
  });

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
  errors.add(failed);
  if (failed) {
    return;
  }
  if (code === 200) {
    reserved.add(1);
    reservedDuration.add(res.timings.duration);
  } else if (code === 409) {
    conflictTotal.add(1);
    (CONFLICTS[msg] || conflictOther).add(1);
    if (!CONFLICTS[msg]) {
      console.warn(`未归类的 409: member=${memberId} msg=${msg}`);
    }
  } else {
    unexpected.add(1);
    console.warn(`非预期响应: member=${memberId} status=${res.status} code=${code} msg=${msg}`);
  }
}
