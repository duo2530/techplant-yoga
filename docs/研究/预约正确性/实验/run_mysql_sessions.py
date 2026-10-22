"""Run isolated MySQL mechanism experiments through persistent CLI sessions."""

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path
from queue import Queue, Empty
from threading import Thread
import json
import hashlib
import subprocess
import time
import uuid


IMAGE = "mysql@sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b"


def docker(*args, timeout=30):
    return subprocess.check_output(["docker", *args], stderr=subprocess.STDOUT,
                                   timeout=timeout).decode("utf-8", errors="strict").strip()


class Session:
    def __init__(self, container, name, transcript):
        self.name, self.transcript = name, transcript
        self.process = subprocess.Popen(
            ["docker", "exec", "-i", "-e", "MYSQL_PWD=research-only", container,
             "mysql", "--batch", "--raw", "--skip-column-names", "--unbuffered", "--force",
             "-uroot", "booking_research"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
        self.queue = Queue()
        self.reader = Thread(target=self.read, daemon=True)
        self.reader.start()

    def read(self):
        for line in self.process.stdout:
            self.queue.put(line.rstrip("\r\n"))
        self.queue.put(None)

    def sql(self, sql, timeout=20):
        marker = "END_" + uuid.uuid4().hex
        started = time.monotonic()
        self.process.stdin.write(sql.rstrip().rstrip(";") + ";\nSELECT '" + marker + "';\n")
        self.process.stdin.flush()
        lines = []
        while True:
            remaining = timeout - (time.monotonic() - started)
            if remaining <= 0:
                raise TimeoutError((self.name, sql, lines))
            try:
                line = self.queue.get(timeout=remaining)
            except Empty as exc:
                raise TimeoutError((self.name, sql, lines)) from exc
            if line is None:
                raise RuntimeError((self.name, sql, lines, "client closed"))
            if line == marker:
                break
            lines.append(line)
        self.transcript.append({"session": self.name, "sql": sql, "output": lines,
                                "elapsed_ms": round((time.monotonic() - started) * 1000, 2)})
        return lines

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.terminate()
                self.process.wait(timeout=5)


def expect(actual, expected):
    if actual != expected:
        raise AssertionError({"expected": expected, "actual": actual})


def wait_for_lock(observer, future):
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        count = observer.sql("SELECT COUNT(*) FROM performance_schema.data_lock_waits")
        if int(count[-1]) > 0:
            observer.sql("SELECT OBJECT_NAME,INDEX_NAME,LOCK_MODE,LOCK_STATUS,LOCK_DATA "
                         "FROM performance_schema.data_locks WHERE OBJECT_SCHEMA='booking_research'")
            return
        if future.done():
            raise AssertionError(("operation finished without observed lock wait", future.result()))
        time.sleep(0.05)
    raise TimeoutError("lock wait not observed")


def run():
    name = "yoga-research-" + uuid.uuid4().hex[:10]
    transcript, checks, sessions = [], {}, []
    report = {"started_at": datetime.now().astimezone().isoformat(),
              "image": IMAGE, "container": name, "transcript": transcript, "checks": checks,
              "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest()}
    created = False
    try:
        report["docker_version"] = docker("info", "--format", "{{.ServerVersion}}")
        docker("run", "--name", name, "--rm", "-d", "-e", "MYSQL_ROOT_PASSWORD=research-only",
               "-e", "MYSQL_DATABASE=booking_research", IMAGE, timeout=60)
        created = True
        deadline = time.monotonic() + 50
        while True:
            try:
                docker("exec", "-e", "MYSQL_PWD=research-only", name,
                       "mysql", "-uroot", "booking_research", "-Nse", "SELECT 1", timeout=3)
                break
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
                if time.monotonic() >= deadline:
                    raise TimeoutError("MySQL readiness")
                time.sleep(0.3)
        a, b, c = [Session(name, identity, transcript) for identity in "ABC"]
        sessions = [a, b, c]
        report["connections"] = {session.name: session.sql("SELECT CONNECTION_ID()")[0]
                                 for session in sessions}
        report["mysql_environment"] = c.sql(
            "SELECT VERSION(),@@transaction_isolation,@@autocommit,@@sql_mode,"
            "@@innodb_rollback_on_timeout,@@session.time_zone")
        for session in sessions:
            session.sql("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            session.sql("SET SESSION innodb_lock_wait_timeout=15")
        a.sql("CREATE TABLE resource(id INT PRIMARY KEY,capacity INT NOT NULL,used INT NOT NULL,"
              "CHECK(capacity>0 AND used>=0 AND used<=capacity)) ENGINE=InnoDB")
        a.sql("CREATE TABLE commitment(id INT PRIMARY KEY,resource_id INT NOT NULL,persons INT NOT NULL) ENGINE=InnoDB")
        a.sql("CREATE TABLE intent(member_id INT NOT NULL,request_id VARCHAR(36) NOT NULL,"
              "payload VARCHAR(100) NOT NULL,PRIMARY KEY(member_id,request_id)) ENGINE=InnoDB")
        a.sql("INSERT INTO resource VALUES(1,1,0),(2,1,0)")
        snapshot = ("SELECT used,capacity,(SELECT COALESCE(SUM(persons),0) FROM commitment "
                    "WHERE resource_id=1) FROM resource WHERE id=1")
        with ThreadPoolExecutor(max_workers=2) as pool:
            expect(a.sql("START TRANSACTION; SELECT used FROM resource WHERE id=1"), ["0"])
            expect(b.sql("START TRANSACTION; SELECT used FROM resource WHERE id=1"), ["0"])
            a.sql("INSERT INTO commitment VALUES(11,1,1); UPDATE resource SET used=1 WHERE id=1 AND capacity>=1; COMMIT")
            b.sql("INSERT INTO commitment VALUES(12,1,1); UPDATE resource SET used=1 WHERE id=1 AND capacity>=1; COMMIT")
            expect(c.sql(snapshot), ["1\t1\t2"])
            checks["E-SQL-1"] = "PASS: detected used=1 but promised=2 despite CHECK"

            for rollback in (False, True):
                a.sql("DELETE FROM commitment; UPDATE resource SET used=0 WHERE id=1")
                expect(a.sql("START TRANSACTION; UPDATE resource SET used=used+1 "
                             "WHERE id=1 AND used+1<=capacity; SELECT ROW_COUNT()"), ["1"])
                pending = pool.submit(b.sql, "START TRANSACTION; UPDATE resource SET used=used+1 "
                                      "WHERE id=1 AND used+1<=capacity; SELECT ROW_COUNT()")
                wait_for_lock(c, pending)
                if rollback:
                    a.sql("ROLLBACK")
                    expect(pending.result(timeout=10), ["1"])
                    b.sql("INSERT INTO commitment VALUES(22,1,1); COMMIT")
                else:
                    a.sql("INSERT INTO commitment VALUES(21,1,1); COMMIT")
                    expect(pending.result(timeout=10), ["0"])
                    b.sql("ROLLBACK")
                expect(c.sql(snapshot), ["1\t1\t1"])
            checks["E-SQL-2"] = "PASS: observed waits; loser affects 0 after commit, 1 after rollback"

            a.sql("DELETE FROM intent; UPDATE resource SET used=0 WHERE id=1")
            a.sql("START TRANSACTION; SELECT * FROM resource WHERE id=1 FOR UPDATE; "
                  "INSERT INTO intent VALUES(1,'k','p=1'); UPDATE resource SET used=1 WHERE id=1")
            expect(b.sql("START TRANSACTION; SELECT * FROM intent WHERE member_id=1 AND request_id='k'"), [])
            pending = pool.submit(b.sql, "SELECT used FROM resource WHERE id=1 FOR UPDATE")
            wait_for_lock(c, pending)
            a.sql("COMMIT")
            expect(pending.result(timeout=10), ["1"])
            expect(b.sql("SELECT payload FROM intent WHERE member_id=1 AND request_id='k'"), [])
            expect(b.sql("SELECT payload FROM intent WHERE member_id=1 AND request_id='k' FOR UPDATE"), ["p=1"])
            b.sql("ROLLBACK")
            checks["E-SQL-3"] = "PASS: old snapshot empty, current read sees committed intent"

            a.sql("DELETE FROM commitment; UPDATE resource SET used=0")
            a.sql("START TRANSACTION; INSERT INTO commitment VALUES(31,1,1); "
                  "SELECT id FROM resource WHERE id=1 FOR UPDATE")
            b.sql("START TRANSACTION; INSERT INTO commitment VALUES(32,2,1); "
                  "SELECT id FROM resource WHERE id=2 FOR UPDATE")
            first = pool.submit(a.sql, "SELECT id FROM resource WHERE id=2 FOR UPDATE")
            wait_for_lock(c, first)
            second = pool.submit(b.sql, "SELECT id FROM resource WHERE id=1 FOR UPDATE")
            results = {"A": first.result(timeout=10), "B": second.result(timeout=10)}
            victims = [s for s, lines in results.items() if any("ERROR 1213" in line for line in lines)]
            expect(len(victims), 1)
            victim = victims[0]
            expect(a.sql("SELECT COUNT(*) FROM commitment WHERE id=31"), ["0" if victim == "A" else "1"])
            expect(b.sql("SELECT COUNT(*) FROM commitment WHERE id=32"), ["0" if victim == "B" else "1"])
            c.sql("SHOW ENGINE INNODB STATUS")
            a.sql("COMMIT"); b.sql("COMMIT")
            expect(c.sql("SELECT COUNT(*) FROM commitment"), ["1"])
            checks["E-SQL-4-deadlock"] = {"result": "PASS", "victim": victim, "outputs": results,
                                          "assertion": "victim transaction insert rolled back; survivor committed"}

            a.sql("DELETE FROM commitment; START TRANSACTION; SELECT id FROM resource WHERE id=1 FOR UPDATE")
            b.sql("SET SESSION innodb_lock_wait_timeout=2; START TRANSACTION; INSERT INTO commitment VALUES(41,2,1)")
            pending = pool.submit(b.sql, "UPDATE resource SET used=used+1 WHERE id=1 AND used+1<=capacity")
            wait_for_lock(c, pending)
            timeout_lines = pending.result(timeout=10)
            assert any("ERROR 1205" in line for line in timeout_lines), timeout_lines
            expect(b.sql("SELECT COUNT(*) FROM commitment WHERE id=41"), ["1"])
            b.sql("ROLLBACK"); a.sql("ROLLBACK")
            expect(c.sql("SELECT COUNT(*) FROM commitment WHERE id=41"), ["0"])
            checks["E-SQL-4-timeout"] = "PASS: 1205 retained prior insert until explicit ROLLBACK"
        report["status"] = "PASS"
    except Exception as exc:
        report["status"] = "FAIL"
        report["error"] = repr(exc)
        raise
    finally:
        for session in sessions:
            session.close()
        if created:
            docker("stop", "--time", "5", name)
            report["container_removed"] = True
        report["finished_at"] = datetime.now().astimezone().isoformat()
        Path(__file__).with_name("mysql-results.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "transcript"}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    run()
