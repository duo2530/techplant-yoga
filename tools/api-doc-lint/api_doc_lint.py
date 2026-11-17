"""接口文档格式检查：校验 Markdown 是否符合 docs/模板/接口设计模板.md（Apifox 导出格式）。

  python -X utf8 tools/api-doc-lint/api_doc_lint.py <文件或目录> [...]

有问题时逐条输出 `文件:行号: [规则] 说明`，退出码为 1。
"""
import argparse
import json
import re
import sys
from dataclasses import dataclass
from pathlib import Path

METHODS = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS"
ENDPOINT_HEADING = re.compile(rf"^## ({METHODS}) (\S.*)$")
METHOD_LINE = re.compile(rf"^({METHODS}) (/\S*)$")
HEADING = re.compile(r"^(#{1,4}) (.*)$")
MODEL_MARKER = re.compile(r'^<h2 id="tocS_(\w+)">(\w+)</h2>$')
OP_ANCHOR = re.compile(r'^<a id="opId(\w+)"></a>$')
RESPONSE_MARKER = re.compile(r"^> (\d{3}) Response$")
STATUS_MEANING = re.compile(r"^\[[^\]]+\]\(https://tools\.ietf\.org/html/rfc\d+#section-[\d.]+\)$")
MODEL_REF = re.compile(r"^\[(\w+)\]\(#schema(\w+)\)$")
SEPARATOR_CELL = re.compile(r"^:?-+:?$")
PATH_PARAM = re.compile(r"\{(\w+)\}")

REQUEST_HEADER = ["名称", "位置", "类型", "必选", "说明"]
RESULT_HEADER = ["状态码", "状态码含义", "说明", "数据模型"]
FIELD_HEADER = ["名称", "类型", "必选", "约束", "中文名", "说明"]
ENUM_HEADER = ["属性", "值"]
TABLE_HEADERS = {
    "请求参数": REQUEST_HEADER,
    "返回结果": RESULT_HEADER,
    "返回数据结构": FIELD_HEADER,
    "属性": FIELD_HEADER,
    "枚举值": ENUM_HEADER,
}
PARAM_LOCATIONS = {"path", "query", "header", "cookie", "body"}
ENDPOINT_ORDER = ["body", "请求参数", "示例", "返回结果", "返回数据结构"]
MODELS_TITLE = "数据模型"
AUTH_TITLE = "Authentication"


@dataclass(frozen=True)
class Issue:
    line: int
    rule: str
    message: str


class Doc:
    def __init__(self, text):
        self.lines = text.splitlines()
        self.fenced = self._mask_fences()
        self.issues = []

    def _mask_fences(self):
        mask, inside = [], False
        for line in self.lines:
            is_marker = line.lstrip().startswith("```")
            mask.append(inside or is_marker)
            if is_marker:
                inside = not inside
        return mask

    def report(self, index, rule, message):
        self.issues.append(Issue(index + 1, rule, message))

    def headings(self, start=0, end=None):
        end = len(self.lines) if end is None else end
        found = []
        for i in range(start, end):
            if self.fenced[i]:
                continue
            m = HEADING.match(self.lines[i])
            if m:
                found.append((i, len(m.group(1)), m.group(2).strip()))
        return found

    def next_nonblank(self, index, end=None):
        end = len(self.lines) if end is None else end
        for i in range(index, end):
            if self.lines[i].strip():
                return i
        return None

    def prev_nonblank(self, index):
        for i in range(index, -1, -1):
            if self.lines[i].strip():
                return i
        return None

    def fence_after(self, index, end):
        """返回 (语言, 内容, 起始行)；index 之后第一个非空行不是围栏则返回 None。"""
        i = self.next_nonblank(index, end)
        if i is None or not self.lines[i].lstrip().startswith("```"):
            return None
        lang = self.lines[i].lstrip()[3:].strip()
        body = []
        for j in range(i + 1, end):
            if self.lines[j].lstrip().startswith("```"):
                return lang, "\n".join(body), i
            body.append(self.lines[j])
        return lang, "\n".join(body), i

    def tables(self, start, end):
        """返回 [(表头所在行, [行文本...])]，只收围栏外连续以 | 开头的行。"""
        found, i = [], start
        while i < end:
            if not self.fenced[i] and self.lines[i].startswith("|"):
                j = i
                while j < end and not self.fenced[j] and self.lines[j].startswith("|"):
                    j += 1
                found.append((i, self.lines[i:j]))
                i = j
            else:
                i += 1
        return found


def split_cells(row):
    body = row.strip()
    body = body[1:] if body.startswith("|") else body
    body = body[:-1] if body.endswith("|") and not body.endswith("\\|") else body
    return [c.strip() for c in re.split(r"(?<!\\)\|", body)]


def check_table(doc, header_line, rows, expected, name):
    """校验表头、分隔行和列数，返回数据行 [(行号, cells)]。"""
    header = split_cells(rows[0])
    if header != expected:
        doc.report(header_line, "table-header", f"「{name}」表头应为 |{'|'.join(expected)}|，实际为 |{'|'.join(header)}|")
        return []
    if len(rows) < 2 or not all(SEPARATOR_CELL.match(c) for c in split_cells(rows[1])) or len(split_cells(rows[1])) != len(expected):
        doc.report(header_line + 1, "table-separator", f"「{name}」表头后应紧跟 {len(expected)} 列分隔行（|---|）")
        return []
    data = []
    for offset, row in enumerate(rows[2:], start=2):
        cells = split_cells(row)
        if len(cells) != len(expected):
            doc.report(header_line + offset, "table-columns", f"「{name}」应有 {len(expected)} 列，实际 {len(cells)} 列")
        else:
            data.append((header_line + offset, cells))
    return data


def parse_front_matter(doc):
    """返回 (front matter 字段, 正文起始行)。"""
    if not doc.lines or doc.lines[0].strip() != "---":
        doc.report(0, "front-matter", "文件须以 YAML front matter（---）开头")
        return {}, 0
    for i in range(1, len(doc.lines)):
        if doc.lines[i].strip() == "---":
            fields = {}
            for line in doc.lines[1:i]:
                m = re.match(r"^(\w+):\s*(.*)$", line)
                if m:
                    fields[m.group(1)] = m.group(2).strip().strip("\"'")
            return fields, i + 1
    doc.report(0, "front-matter", "front matter 缺少结束的 ---")
    return {}, 0


def check_json_block(doc, fence, rule, what):
    if fence is None:
        return
    lang, content, line = fence
    if lang == "json":
        try:
            json.loads(content)
        except ValueError as e:
            doc.report(line, rule, f"{what} 不是合法 JSON：{e}")


def check_outline(doc, body_start):
    """校验一级标题顺序，返回 (标题, 数据模型段起止, 分组段列表)。"""
    h1 = [(i, t) for i, level, t in doc.headings(body_start) if level == 1]
    if not h1:
        doc.report(body_start, "outline", "缺少一级标题（# 文档标题）")
        return None
    if len(h1) < 3 or h1[1][1] != AUTH_TITLE:
        doc.report(h1[0][0], "outline", f"一级标题应依次为：文档标题、{AUTH_TITLE}、至少一个接口分组、{MODELS_TITLE}")
        return None
    if h1[-1][1] != MODELS_TITLE:
        doc.report(h1[-1][0], "outline", f"最后一个一级标题应为「{MODELS_TITLE}」")
        return None
    bounds = [i for i, _ in h1] + [len(doc.lines)]
    sections = [(h1[k][0], bounds[k + 1], h1[k][1]) for k in range(len(h1))]
    return sections


def check_intro(doc, title, section):
    start, end, _ = section
    intro = [doc.lines[i].strip() for i in range(start + 1, end) if doc.lines[i].strip()]
    if not intro or intro[0].startswith("Base URLs"):
        doc.report(start, "intro", "文档标题后须有一段文档简介")
    if "Base URLs:" not in intro:
        doc.report(start, "intro", "缺少 `Base URLs:` 行")


def check_models(doc, start, end):
    models = {}
    markers = [i for i in range(start, end) if not doc.fenced[i] and MODEL_MARKER.match(doc.lines[i])]
    for k, i in enumerate(markers):
        name, shown = MODEL_MARKER.match(doc.lines[i]).groups()
        stop = markers[k + 1] if k + 1 < len(markers) else end
        if name != shown:
            doc.report(i, "model-anchor", f"<h2> 的 id（tocS_{name}）与标题文字（{shown}）须一致")
        if name in models:
            doc.report(i, "model-duplicate", f"数据模型 {name} 重复定义")
        models[name] = i
        expected = [f'<a id="schema{name.lower()}"></a>', f'<a id="schema_{name}"></a>',
                    f'<a id="tocS{name.lower()}"></a>', f'<a id="tocs{name.lower()}"></a>']
        cursor = i + 1
        for anchor in expected:
            j = doc.next_nonblank(cursor, stop)
            if j is None or doc.lines[j].strip() != anchor:
                doc.report(i, "model-anchor", f"数据模型 {name} 标题后应依次出现锚点 {anchor}")
                break
            cursor = j + 1
        fence = doc.fence_after(cursor, stop)
        if fence is None or fence[0] != "json":
            doc.report(i, "model-example", f"数据模型 {name} 须在锚点后给出 ```json 示例")
            continue
        check_json_block(doc, fence, "model-example", f"数据模型 {name} 的示例")
        after = fence[2] + fence[1].count("\n") + 2
        desc = doc.next_nonblank(after + 1, stop)
        if desc is None or doc.lines[desc].startswith(("#", "|", ">", "```")):
            doc.report(i, "model-description", f"数据模型 {name} 须在示例后给出一句说明")
        heads = {t: n for n, _, t in doc.headings(i, stop)}
        if "属性" not in heads:
            doc.report(i, "model-fields", f"数据模型 {name} 缺少 ### 属性")
    return models


def check_tables_in(doc, start, end):
    """按最近的 ###/#### 标题确定每张表的类型并校验，返回 {标题: 数据行}。"""
    heads = doc.headings(start, end)
    result = {}
    for line, rows in doc.tables(start, end):
        owner = next((t for i, level, t in reversed(heads) if i < line and level >= 3), None)
        expected = TABLE_HEADERS.get(owner)
        if expected is None:
            continue
        data = check_table(doc, line, rows, expected, owner)
        result.setdefault(owner, []).extend(data)
        if owner in ("属性", "返回数据结构"):
            for row_line, cells in data:
                if cells[2] not in ("true", "false"):
                    doc.report(row_line, "field-required", f"必选应为 true 或 false，实际为「{cells[2]}」")
                if not cells[3]:
                    doc.report(row_line, "field-constraint", "约束不能为空（无约束写 none）")
    return result


def check_model_fields(doc, models, end):
    ordered = sorted(models.values())
    for k, i in enumerate(ordered):
        stop = ordered[k + 1] if k + 1 < len(ordered) else end
        check_tables_in(doc, i, stop)


def check_endpoint(doc, start, end, models, seen_ops, seen_routes):
    heading = ENDPOINT_HEADING.match(doc.lines[start])
    method = heading.group(1)

    anchor = doc.prev_nonblank(start - 1)
    op = OP_ANCHOR.match(doc.lines[anchor].strip()) if anchor is not None else None
    if not op:
        doc.report(start, "endpoint-anchor", '接口标题前须有 <a id="opId接口标识"></a>')
    elif op.group(1) in seen_ops:
        doc.report(start, "endpoint-anchor", f"opId{op.group(1)} 重复")
    else:
        seen_ops.add(op.group(1))

    route_line = doc.next_nonblank(start + 1, end)
    route = METHOD_LINE.match(doc.lines[route_line]) if route_line is not None else None
    if not route or route.group(1) != method:
        doc.report(start, "endpoint-route", f"标题后应紧跟与标题方法一致的请求行（如 `{method} /path`）")
        return
    path = route.group(2)
    if (method, path) in seen_routes:
        doc.report(route_line, "endpoint-route", f"接口 {method} {path} 重复")
    seen_routes.add((method, path))

    desc = doc.next_nonblank(route_line + 1, end)
    if desc is None or doc.lines[desc].startswith(("#", ">", "|", "```")):
        doc.report(route_line, "endpoint-description", "请求行后须有接口说明")

    markers = []
    for i in range(route_line + 1, end):
        if doc.fenced[i]:
            continue
        line = doc.lines[i]
        if line == "> Body 请求参数":
            markers.append((i, "body"))
        elif line == "> 返回示例":
            markers.append((i, "示例"))
        else:
            m = HEADING.match(line)
            if m and len(m.group(1)) == 3:
                markers.append((i, m.group(2).strip()))
    keys = [k for _, k in markers]
    for _, k in markers:
        if k not in ENDPOINT_ORDER:
            doc.report(markers[keys.index(k)][0], "endpoint-section", f"未知小节「{k}」，应使用：{' / '.join(ENDPOINT_ORDER[1:])}")
    known = [(i, ENDPOINT_ORDER.index(k)) for i, k in markers if k in ENDPOINT_ORDER]
    for (_, a), (i, b) in zip(known, known[1:]):
        if b < a:
            doc.report(i, "endpoint-order", f"小节顺序应为：{' → '.join(ENDPOINT_ORDER)}")
            break

    tables = check_tables_in(doc, route_line + 1, end)
    params = tables.get("请求参数", [])
    for row_line, cells in params:
        if cells[1] not in PARAM_LOCATIONS:
            doc.report(row_line, "param-location", f"位置应为 {'/'.join(sorted(PARAM_LOCATIONS))}，实际为「{cells[1]}」")
        if cells[3] not in ("是", "否"):
            doc.report(row_line, "param-required", f"必选应为「是」或「否」，实际为「{cells[3]}」")
        if not cells[0] or not cells[2]:
            doc.report(row_line, "param-empty", "参数名称和类型不能为空")
    placeholders = PATH_PARAM.findall(path)
    declared = {c[0] for _, c in params if c[1] == "path"}
    for name in placeholders:
        if name not in declared:
            doc.report(route_line, "param-path", f"路径参数 {{{name}}} 未在请求参数表中以 path 位置声明")
    if (placeholders or any(k == "body" for k in keys)) and "请求参数" not in keys:
        doc.report(start, "endpoint-section", "存在路径参数或 Body 时须有 ### 请求参数")

    body_marker = next((i for i, k in markers if k == "body"), None)
    has_body_row = any(c[1] == "body" for _, c in params)
    if body_marker is not None:
        fence = doc.fence_after(body_marker + 1, end)
        if fence is None:
            doc.report(body_marker, "body-example", "`> Body 请求参数` 后须跟代码块")
        check_json_block(doc, fence, "body-example", "Body 请求参数示例")
        if not has_body_row:
            doc.report(body_marker, "param-body", "存在 Body 示例，但请求参数表没有 body 位置的行")
    elif has_body_row:
        doc.report(start, "param-body", "请求参数表有 body 位置的行，但缺少 `> Body 请求参数` 示例")

    check_responses(doc, route_line, end, keys, tables.get("返回结果", []), markers, models)


def check_responses(doc, route_line, end, keys, results, markers, models):
    if "返回结果" not in keys:
        doc.report(route_line, "endpoint-section", "缺少 ### 返回结果")
        return
    codes, needs_example, needs_structure = set(), False, False
    for row_line, cells in results:
        code, meaning, _, model = cells
        if not re.fullmatch(r"\d{3}", code):
            doc.report(row_line, "result-code", f"状态码应为三位数字，实际为「{code}」")
        codes.add(code)
        if not STATUS_MEANING.match(meaning):
            doc.report(row_line, "result-meaning", "状态码含义应为 [名称](https://tools.ietf.org/html/rfcNNNN#section-x.y.z) 链接")
        ref = MODEL_REF.match(model)
        if ref:
            needs_example = True
            if ref.group(1) not in models:
                doc.report(row_line, "result-model", f"数据模型 {ref.group(1)} 未在「{MODELS_TITLE}」中定义")
            elif ref.group(2) != ref.group(1).lower():
                doc.report(row_line, "result-model", f"链接锚点应为 #schema{ref.group(1).lower()}")
        elif model == "Inline":
            needs_example = needs_structure = True
        elif model != "None":
            doc.report(row_line, "result-model", f"数据模型应为 [名称](#schema名称)、Inline 或 None，实际为「{model}」")
    if needs_structure and "返回数据结构" not in keys:
        doc.report(route_line, "endpoint-section", "存在 Inline 数据模型，须有 ### 返回数据结构")
    example_at = next((i for i, k in markers if k == "示例"), None)
    if example_at is None:
        if needs_example:
            doc.report(route_line, "response-example", "缺少 `> 返回示例`")
        return
    stop = next((i for i, k in markers if i > example_at and k != "示例"), end)
    shown = []
    for i in range(example_at + 1, stop):
        m = RESPONSE_MARKER.match(doc.lines[i]) if not doc.fenced[i] else None
        if not m:
            continue
        shown.append(m.group(1))
        fence = doc.fence_after(i + 1, stop)
        if fence is None:
            doc.report(i, "response-example", f"`> {m.group(1)} Response` 后须跟代码块")
        check_json_block(doc, fence, "response-example", f"{m.group(1)} 返回示例")
        if m.group(1) not in codes:
            doc.report(i, "response-example", f"示例状态码 {m.group(1)} 未出现在返回结果表中")
    if not shown:
        doc.report(example_at, "response-example", "`> 返回示例` 后须有 `> 状态码 Response` 和代码块")


def lint_text(text):
    doc = Doc(text)
    fields, body_start = parse_front_matter(doc)
    title = fields.get("title", "")
    if "front-matter" not in {i.rule for i in doc.issues} and not title:
        doc.report(0, "front-matter", "front matter 缺少 title")

    sections = check_outline(doc, body_start)
    if sections is None:
        return sorted(doc.issues, key=lambda i: i.line)
    head = sections[0]
    if title and head[2] != title:
        doc.report(head[0], "outline", f"一级标题「{head[2]}」应与 front matter 的 title「{title}」一致")
    check_intro(doc, title, head)

    models_start, models_end, _ = sections[-1]
    models = check_models(doc, models_start, models_end)
    check_model_fields(doc, models, models_end)

    seen_ops, seen_routes = set(), set()
    for start, end, name in sections[2:-1]:
        endpoints = [(i, t) for i, level, t in doc.headings(start + 1, end) if level == 2]
        if not endpoints:
            doc.report(start, "group-empty", f"分组「{name}」下没有接口")
        bounds = [i for i, _ in endpoints] + [end]
        for k, (i, _) in enumerate(endpoints):
            if not ENDPOINT_HEADING.match(doc.lines[i]):
                doc.report(i, "endpoint-heading", "接口标题应为 `## 方法 名称`，方法为大写 HTTP 方法")
                continue
            check_endpoint(doc, i, bounds[k + 1], models, seen_ops, seen_routes)
    return sorted(doc.issues, key=lambda i: (i.line, i.rule))


def collect(paths):
    files = []
    for p in map(Path, paths):
        files.extend(sorted(p.rglob("*.md")) if p.is_dir() else [p])
    return files


def main(argv=None):
    parser = argparse.ArgumentParser(description="检查接口文档是否符合 Apifox 导出格式")
    parser.add_argument("paths", nargs="+", help="Markdown 文件或目录（目录递归检查 *.md）")
    args = parser.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    total = 0
    files = collect(args.paths)
    for f in files:
        try:
            text = f.read_text(encoding="utf-8-sig")
        except (OSError, UnicodeDecodeError) as e:
            print(f"{f}:1: [read] 无法按 UTF-8 读取：{e}")
            total += 1
            continue
        for issue in lint_text(text):
            print(f"{f}:{issue.line}: [{issue.rule}] {issue.message}")
            total += 1
    print(f"检查 {len(files)} 个文件，发现 {total} 个问题")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
