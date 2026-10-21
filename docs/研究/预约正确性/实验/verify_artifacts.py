"""Check research artifact encoding, local links, syntax, and captured hashes."""

from pathlib import Path
from urllib.parse import unquote
import ast
import hashlib
import json
import re


def main():
    directory = Path(__file__).resolve().parent.parent
    root = directory.parents[2]
    checked = 0
    local_links = 0
    for path in sorted(directory.rglob("*")):
        if not path.is_file() or path.suffix not in (".md", ".py", ".json", ".sql"):
            continue
        content = path.read_text(encoding="utf-8")
        assert "\ufffd" not in content, str(path)
        if path.suffix == ".py":
            ast.parse(content, filename=str(path))
        elif path.suffix == ".json":
            json.loads(content)
        elif path.suffix == ".md":
            for target in re.findall(r"\]\(([^)]+)\)", content):
                if "://" in target or target.startswith("#"):
                    continue
                relative = unquote(target.split("#", 1)[0])
                assert (path.parent / relative).exists(), (str(path), target)
                local_links += 1
        checked += 1
    manifest = json.loads((directory / "实验/source-manifest.json").read_text(encoding="utf-8"))
    for source in manifest["sources"]:
        digest = hashlib.sha256((root / source["path"]).read_bytes()).hexdigest()
        assert digest == source["sha256"], source["path"]
    results = json.loads((directory / "实验/results.json").read_text(encoding="utf-8"))
    digest = hashlib.sha256((directory / "实验/reference_model.py").read_bytes()).hexdigest()
    assert digest == results["model_sha256"], "model changed after execution"
    mysql_path = directory / "实验/mysql-results.json"
    if mysql_path.exists():
        mysql = json.loads(mysql_path.read_text(encoding="utf-8"))
        digest = hashlib.sha256((directory / "实验/run_mysql_sessions.py").read_bytes()).hexdigest()
        assert digest == mysql["runner_sha256"], "MySQL runner changed after execution"
        assert mysql["status"] == "PASS" and mysql["container_removed"]
    print("UTF-8 artifacts:", checked, "local links:", local_links,
          "source hashes:", len(manifest["sources"]), "model result hash: matched")


if __name__ == "__main__":
    main()
