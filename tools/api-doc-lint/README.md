# 接口文档格式检查

检查 Markdown 接口文档是否符合 [接口设计模板](../../docs/模板/接口设计模板.md)（Apifox 导出格式）。只依赖 Python 标准库。

```powershell
python -X utf8 tools/api-doc-lint/api_doc_lint.py <文件或目录> [...]
```

目录会递归检查其中的 `*.md`。每个问题输出为 `文件:行号: [规则] 说明`，有问题时退出码为 1。

## 检查内容

| 范围 | 规则 |
|---|---|
| 文件结构 | front matter 含 `title`；一级标题依次为：文档标题（与 `title` 一致，后接简介和 `Base URLs:`）、`Authentication`、至少一个接口分组、`数据模型`（最后） |
| 接口 | 标题前有 `<a id="opId…"></a>` 且不重复；标题为 `## 方法 名称`（方法大写）；紧跟同方法的请求行 `METHOD /path`，路由不重复；随后有接口说明 |
| 小节顺序 | `> Body 请求参数` → `### 请求参数` → `> 返回示例` → `### 返回结果` → `### 返回数据结构` |
| 请求参数表 | 表头固定；位置为 path/query/header/cookie/body；必选为 是/否；路径占位符须以 path 声明；Body 示例与 body 行成对出现 |
| 返回 | 状态码含义为 RFC 链接；数据模型为 `[名称](#schema名称)`、`Inline` 或 `None`，引用的模型须已定义；Inline 须有 `### 返回数据结构`；示例状态码须在返回结果表中，JSON 须合法 |
| 数据模型 | `<h2 id="tocS_名称">` 后依次 4 个锚点、`json` 示例、说明、`### 属性` 表；必选为 true/false，约束不为空 |
| 所有表格 | 表头、分隔行、列数与对应小节一致 |

## 测试

在 `tools/api-doc-lint` 下执行：

```powershell
python -X utf8 -m unittest discover -s tests
```

模板自身必须零问题，其余用例在模板上做单点改动，验证对应规则会报错。
