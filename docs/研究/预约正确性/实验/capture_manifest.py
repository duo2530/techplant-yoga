"""Capture source hashes and symbol locations without reading runtime secrets."""

from datetime import datetime, timedelta, timezone
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", default=".tmp/reservation-research-maven-retry.log")
    parser.add_argument("--evidence", default="maven-evidence-retry.json")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    sources = ["backend/sql/yoga.sql", "backend/pom.xml", "backend/ruoyi-yoga/pom.xml"]
    java_root = "backend/ruoyi-yoga/src/main/java/com/techplant/yoga/"
    sources += [java_root + item for item in (
        "reservation/service/impl/ReservationServiceImpl.java",
        "reservation/dao/ReservationDaoImpl.java",
        "reservation/controller/ReservationPublicController.java",
        "reservation/controller/ReservationController.java",
        "schedule/service/impl/ScheduleServiceImpl.java",
        "schedule/service/impl/ScheduleBookingServiceImpl.java",
        "schedule/dao/ScheduleDaoImpl.java",
        "membercard/service/impl/MemberCardServiceImpl.java",
        "membercard/service/impl/MemberCardTransactionServiceImpl.java",
        "membercard/dao/MemberCardDaoImpl.java",
        "membercard/dao/MemberCardTransactionDaoImpl.java",
        "member/dao/MemberDaoImpl.java")]
    test_root = "backend/ruoyi-yoga/src/test/java/com/techplant/yoga/"
    sources += [test_root + item for item in (
        "reservation/service/ReservationServiceImplTest.java",
        "common/testcontainers/YogaMyBatisTestConfig.java",
        "common/testcontainers/YogaMySqlContainer.java",
        "common/testcontainers/YogaSchema.java")]
    sources += [
        "backend/ruoyi-framework/src/main/java/com/ruoyi/framework/config/DruidConfig.java",
        "backend/ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/DataSourceAspect.java",
        "docs/系统测试/AI Agent测试手册.md",
        "docs/系统测试/预约并发与事务核对.md",
        "docs/详细设计/预约管理详细设计.md",
        "docs/详细设计/排课管理详细设计.md",
        "docs/需求文档/全局说明.md", "docs/需求文档/状态流转图.md",
        "docs/需求分析/用例/UC-009-维护排课.md"]
    symbol = re.compile(r"(?:public|private|protected)\s+(?:static\s+)?[\w<>?, ]+\s+(\w+)\s*\(|CREATE TABLE|^#{1,4} ")
    items = []
    for relative in sources:
        path = root / relative
        raw = path.read_bytes()
        content = raw.decode("utf-8")
        lines = [{"line": i, "text": line.strip()} for i, line in enumerate(content.splitlines(), 1)
                 if symbol.search(line)]
        items.append({"path": relative, "sha256": hashlib.sha256(raw).hexdigest(), "locations": lines})
    def git(*args):
        return subprocess.check_output(["git", "-c", "core.quotepath=false", *args], cwd=root).decode("utf-8").strip()
    report = {"captured_at": datetime.now(timezone(timedelta(hours=8))).isoformat(),
              "git_head": git("rev-parse", "HEAD"), "working_tree": git("status", "--short"),
              "observation": "working tree, including changes predating this research", "sources": items}
    target = Path(__file__).with_name("source-manifest.json")
    target.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Captured", len(items), "UTF-8 source files")
    xml_path = root / ("backend/ruoyi-yoga/target/surefire-reports/"
                       "TEST-com.techplant.yoga.reservation.service.ReservationServiceImplTest.xml")
    log_path = root / args.log
    if xml_path.exists() and log_path.exists():
        suite = ET.fromstring(xml_path.read_bytes())
        properties = {p.attrib["name"]: p.attrib.get("value")
                      for p in suite.findall("./properties/property")
                      if p.attrib["name"] in ("java.version", "java.home", "os.name", "os.version")}
        log = log_path.read_text(encoding="utf-8")
        summaries = re.findall(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)", log)
        counts = tuple(suite.attrib[key] for key in ("tests", "failures", "errors", "skipped"))
        if not summaries or summaries[-1] != counts:
            raise ValueError("Log summary does not match current Surefire XML; select a matching --log")
        docker_failure = "Could not find a valid Docker environment" in log
        passed = int(suite.attrib.get("tests", "0")) > 0 and all(
            int(suite.attrib.get(key, "0")) == 0 for key in ("errors", "failures", "skipped"))
        evidence = {"captured_at": report["captured_at"], "suite": suite.attrib,
                    "runtime": properties,
                    "status": "PASS" if passed else "FAIL",
                    "root_cause": "Could not find a valid Docker environment" if docker_failure else None,
                    "root_cause_found_in_log": docker_failure,
                    "test_phase": ("Spring context and Testcontainers initialization; no business assertions reached"
                                   if docker_failure else "See per-test Surefire results"),
                    "test_cases": [{"name": case.attrib["name"],
                                    "result": "ERROR" if case.find("error") is not None else
                                    "FAIL" if case.find("failure") is not None else
                                    "SKIPPED" if case.find("skipped") is not None else "PASS"}
                                   for case in suite.findall("testcase")],
                    "log": {"path": str(log_path.relative_to(root)),
                            "sha256": hashlib.sha256(log_path.read_bytes()).hexdigest()},
                    "surefire_xml": {"path": str(xml_path.relative_to(root)),
                                     "sha256": hashlib.sha256(xml_path.read_bytes()).hexdigest()}}
        Path(__file__).with_name(args.evidence).write_text(
            json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("Captured Maven evidence:", suite.attrib.get("tests"), "tests,",
              suite.attrib.get("errors"), "errors")


if __name__ == "__main__":
    main()
