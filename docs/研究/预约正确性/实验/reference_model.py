"""Executable specification for the A01 cancellation policy; no database access."""

from collections import deque
from copy import deepcopy
from dataclasses import asdict, dataclass, field
from pathlib import Path
import hashlib
import json
import platform
import random
import sys


@dataclass
class State:
    capacity: list = field(default_factory=lambda: [2, 2])
    opened: list = field(default_factory=lambda: [True, True])
    booked: list = field(default_factory=lambda: [0, 0])
    initial: list = field(default_factory=lambda: [2, 2])
    balance: list = field(default_factory=lambda: [2, 2])
    reservations: dict = field(default_factory=dict)
    ledger: list = field(default_factory=list)


class InjectedFailure(Exception):
    pass


def checkpoint(stage, failure):
    if stage == failure:
        raise InjectedFailure(stage)


def append_ledger(state, key, kind, card, delta):
    if any(x[0] == key and x[1] == kind for x in state.ledger):
        raise AssertionError("duplicate-ledger")
    before = state.balance[card]
    state.balance[card] += delta
    state.ledger.append([key, kind, card, delta, before, state.balance[card]])


def cancel_one(state, key, reason, mutation=None, failure=None):
    r = state.reservations[key]
    if r[4] == "CANCELLED" and mutation != "double-refund":
        return "ok"
    if r[4] == "CHECKED_IN":
        return "reject"
    r[4], r[5] = "CANCELLED", reason
    checkpoint("reservation", failure)
    if mutation != "keep-seat" and mutation != "double-refund":
        state.booked[r[1]] -= r[3]
    checkpoint("capacity", failure)
    if mutation == "double-refund":
        before = state.balance[r[2]]
        state.balance[r[2]] += r[3]
        state.ledger.append([key, "R", r[2], r[3], before, state.balance[r[2]]])
    else:
        append_ledger(state, key, "R", r[2], r[3])
    checkpoint("ledger", failure)
    return "ok"


def transition(original, action, failure=None, mutation=None):
    state = deepcopy(original)
    kind, *args = action
    try:
        if kind == "create":
            key, member, schedule, card, persons = args
            payload = [member, schedule, card, persons]
            if key in state.reservations:
                result = "ok" if state.reservations[key][:4] == payload else "conflict"
                return state, result
            if not (0 <= member < 2 and card == member and persons > 0):
                return state, "reject"
            active = any(r[0] == member and r[1] == schedule and r[4] != "CANCELLED"
                         for r in state.reservations.values())
            if (not state.opened[schedule] or active
                    or state.booked[schedule] + persons > state.capacity[schedule]
                    or state.balance[card] < persons):
                return state, "reject"
            state.reservations[key] = payload + ["RESERVED", None]
            checkpoint("reservation", failure)
            state.booked[schedule] += persons
            checkpoint("capacity", failure)
            append_ledger(state, key, "D", card, -persons)
            checkpoint("ledger", failure)
            return state, "ok"
        if kind == "cancel":
            key = args[0]
            if key not in state.reservations:
                return state, "reject"
            r = state.reservations[key]
            if r[4] != "CANCELLED" and not state.opened[r[1]]:
                return state, "reject"
            return state, cancel_one(state, key, "MEMBER", mutation, failure)
        if kind == "close":
            schedule = args[0]
            rows = sorted(k for k, r in state.reservations.items() if r[1] == schedule)
            if any(state.reservations[k][4] == "CHECKED_IN" for k in rows):
                return state, "reject"
            state.opened[schedule] = False
            checkpoint("close", failure)
            for key in rows:
                if state.reservations[key][4] == "RESERVED":
                    cancel_one(state, key, "SCHEDULE", mutation, failure)
            return state, "ok"
        raise ValueError(kind)
    except InjectedFailure:
        return deepcopy(original), "aborted"


def violations(state):
    errors = []
    for schedule, capacity in enumerate(state.capacity):
        occupied = sum(r[3] for r in state.reservations.values()
                       if r[1] == schedule and r[4] != "CANCELLED")
        if not 0 <= state.booked[schedule] <= capacity or occupied > capacity:
            errors.append("I1-capacity")
        if occupied != state.booked[schedule]:
            errors.append("I2-occupancy")
        if not state.opened[schedule] and occupied:
            errors.append("A01-close-complete")
    for card, balance in enumerate(state.balance):
        if balance < 0:
            errors.append("I3-nonnegative")
        delta = sum(x[3] for x in state.ledger if x[2] == card)
        if balance != state.initial[card] + delta:
            errors.append("I6-conservation")
    active = set()
    for key, r in state.reservations.items():
        d = [x for x in state.ledger if x[0] == key and x[1] == "D"]
        refund = [x for x in state.ledger if x[0] == key and x[1] == "R"]
        if len(d) != 1 or d[0][3] != -r[3] or d[0][2] != r[2]:
            errors.append("I7-deduction")
        expected = r[3] if r[4] == "CANCELLED" else 0
        if len(refund) > 1 or sum(x[3] for x in refund) != expected:
            errors.append("I4-refund")
        if any(x[2] != r[2] for x in refund):
            errors.append("I4-original-card")
        if r[4] != "CANCELLED":
            pair = (r[0], r[1])
            if pair in active:
                errors.append("I8-active-unique")
            active.add(pair)
    for key, kind, card, delta, before, after in state.ledger:
        if key not in state.reservations or after != before + delta:
            errors.append("I6-ledger")
    return sorted(set(errors))


def canonical(state):
    return json.dumps(asdict(state), sort_keys=True, separators=(",", ":"))


ACTIONS = [
    ("create", "a", 0, 0, 0, 1),
    ("create", "a", 0, 1, 0, 1),
    ("create", "b", 0, 1, 0, 2),
    ("create", "c", 1, 0, 1, 1),
    ("create", "d", 0, 0, 0, 1),
] + [("cancel", key) for key in "abcd"] + [("close", s) for s in range(2)]


def explore(depth=6):
    initial = State()
    queue = deque([(initial, [])])
    seen = {canonical(initial)}
    edges = 0
    while queue:
        state, history = queue.popleft()
        assert not violations(state), (history, violations(state))
        if len(history) == depth:
            continue
        for action in ACTIONS:
            nxt, result = transition(state, action)
            edges += 1
            assert not violations(nxt), (history + [action], violations(nxt))
            identity = canonical(nxt)
            if identity not in seen:
                seen.add(identity)
                queue.append((nxt, history + [action]))
    return {"depth": depth, "states": len(seen), "edges": edges,
            "action_alphabet": len(ACTIONS), "violations": 0}


def randomized():
    count = 0
    for seed in range(100):
        rng = random.Random(seed)
        state = State()
        for _ in range(100):
            state, result = transition(state, rng.choice(ACTIONS))
            assert not violations(state), (seed, count, violations(state))
            count += 1
    return {"seeds": [0, 99], "operations": count, "violations": 0}


def mutation_experiments():
    state, _ = transition(State(), ACTIONS[0])
    cases = []
    bad, _ = transition(state, ("close", 0), mutation="keep-seat")
    assert "I2-occupancy" in violations(bad)
    cases.append({"mutation": "system-cancel-keeps-seat", "trace": [ACTIONS[0], ["close", 0]],
                  "detected": violations(bad), "state": asdict(bad)})
    cancelled, _ = transition(state, ("cancel", "a"))
    bad, _ = transition(cancelled, ("cancel", "a"), mutation="double-refund")
    assert "I4-refund" in violations(bad)
    cases.append({"mutation": "bypass-cancel-state-and-ledger-uniqueness",
                  "trace": [ACTIONS[0], ["cancel", "a"], ["cancel", "a"]],
                  "detected": violations(bad), "state": asdict(bad)})
    return cases


def rollback_experiments():
    booked, _ = transition(State(), ACTIONS[0])
    cases = [(State(), ACTIONS[0], x) for x in ("reservation", "capacity", "ledger")]
    cases += [(booked, ("cancel", "a"), x) for x in ("reservation", "capacity", "ledger")]
    cases += [(booked, ("close", 0), x) for x in ("close", "reservation", "capacity", "ledger")]
    for state, action, failure in cases:
        nxt, result = transition(state, action, failure=failure)
        assert result == "aborted" and nxt == state and not violations(nxt)
    return {"injected_failures": len(cases), "rolled_back": len(cases)}


def accepts_history(initial, entries, final):
    """Bounded backtracking checker; unknown calls may have committed or aborted."""
    def visit(state, pending):
        if not pending:
            return state == final
        for entry in pending:
            if any(other["end"] < entry["start"] for other in pending if other is not entry):
                continue
            rest = [other for other in pending if other is not entry]
            nxt, result = transition(state, entry["action"])
            if entry["result"] is None:
                if visit(state, rest) or visit(nxt, rest):
                    return True
            elif entry["result"] == result and visit(nxt, rest):
                return True
        return False
    return visit(initial, entries)


def history_experiments():
    initial = State(capacity=[1, 2])
    winner, _ = transition(initial, ACTIONS[0])
    a = {"start": 0, "end": 3, "action": ACTIONS[0], "result": "ok"}
    c = {"start": 1, "end": 4, "action": ACTIONS[3], "result": "reject"}
    assert accepts_history(initial, [a, c], winner)
    bad = dict(c, result="ok")
    assert not accepts_history(initial, [a, bad], winner)
    unknown = dict(a, result=None)
    retry = dict(a, start=4, end=5)
    assert accepts_history(initial, [unknown, retry], winner)
    real_time_bad = [dict(c, start=0, end=1), dict(a, start=2, end=3)]
    assert not accepts_history(initial, real_time_bad, winner)
    return {"valid_race_accepted": True, "two_successes_rejected": True,
            "unknown_then_retry_accepted": True, "real_time_violation_rejected": True,
            "source": "synthetic histories, not observations of the Java service"}


def main():
    report = {"python": sys.version, "platform": platform.platform(),
              "policy": "A01: close admissions, cancel, release occupancy, refund original deduction",
              "limits": "2 schedules, 2 members/cards, 4 request keys, times cards only; atomic actions",
              "model_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              "finite_exploration": explore(), "random_sequences": randomized(),
              "rollback_model": rollback_experiments(), "mutations": mutation_experiments(),
              "history_checker": history_experiments()}
    target = Path(__file__).with_name("results.json")
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k not in ("mutations", "python", "platform")},
                     ensure_ascii=False, indent=2))
    print("Mutations detected:", [case["mutation"] for case in report["mutations"]])


if __name__ == "__main__":
    main()
