"""接口文档检查器测试：以接口设计模板为正例，在模板上做单点改动作为反例。

  python -X utf8 -m unittest discover -s tests（在 tools/api-doc-lint 下执行）
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import api_doc_lint

TEMPLATE = Path(__file__).resolve().parents[3] / "docs" / "模板" / "接口设计模板.md"


def rules(text):
    return {i.rule for i in api_doc_lint.lint_text(text)}


class TemplateTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = TEMPLATE.read_text(encoding="utf-8-sig")

    def mutate(self, old, new, count=1):
        self.assertIn(old, self.text)
        return self.text.replace(old, new, count)

    def test_template_passes(self):
        self.assertEqual(api_doc_lint.lint_text(self.text), [])

    def test_missing_front_matter(self):
        self.assertIn("front-matter", rules(self.text.split("---", 2)[2]))

    def test_title_mismatch(self):
        self.assertIn("outline", rules(self.mutate("\n# 商店 API\n", "\n# 别的标题\n")))

    def test_missing_base_urls(self):
        self.assertIn("intro", rules(self.mutate("Base URLs:", "")))

    def test_missing_authentication(self):
        self.assertIn("outline", rules(self.mutate("# Authentication", "# 认证")))

    def test_models_must_be_last(self):
        self.assertIn("outline", rules(self.text + "\n# 附录\n"))

    def test_missing_op_anchor(self):
        self.assertIn("endpoint-anchor", rules(self.mutate('<a id="opIdgetPetById"></a>', "")))

    def test_duplicate_op_anchor(self):
        self.assertIn("endpoint-anchor", rules(self.mutate("opIdupdatePet", "opIdgetPetById")))

    def test_route_method_mismatch(self):
        self.assertIn("endpoint-route", rules(self.mutate("\nPUT /pets/{id}", "\nPOST /pets/{id}")))

    def test_lowercase_method_heading(self):
        self.assertIn("endpoint-heading", rules(self.mutate("## GET 获取宠物", "## get 获取宠物")))

    def test_wrong_request_header(self):
        self.assertIn("table-header", rules(self.mutate("|名称|位置|类型|必选|说明|", "|名称|位置|类型|必填|说明|")))

    def test_bad_location(self):
        self.assertIn("param-location", rules(self.mutate("|id|path|string| 是 |", "|id|url|string| 是 |")))

    def test_bad_required(self):
        self.assertIn("param-required", rules(self.mutate("|id|path|string| 是 |", "|id|path|string| yes |")))

    def test_undeclared_path_param(self):
        text = self.mutate("GET /pets/{id}", "GET /pets/{id}/{extra}")
        self.assertIn("param-path", rules(text))

    def test_invalid_response_json(self):
        self.assertIn("response-example", rules(self.mutate('"id": 100\n}', '"id": 100,\n}')))

    def test_missing_response_example(self):
        self.assertIn("response-example", rules(self.mutate("> 返回示例\n\n> 200 Response", "")))

    def test_missing_result_section(self):
        self.assertIn("endpoint-section", rules(self.mutate("### 返回结果", "### 结果", 1)))

    def test_unknown_model_reference(self):
        self.assertIn("result-model", rules(self.mutate("[Pet](#schemapet)|\n\n<a", "[Nope](#schemanope)|\n\n<a")))

    def test_bad_status_meaning(self):
        self.assertIn("result-meaning", rules(self.mutate("[OK](https://tools.ietf.org/html/rfc7231#section-6.3.1)", "OK")))

    def test_inline_requires_structure(self):
        text = self.mutate("### 返回数据结构\n\n状态码 **200**", "")
        self.assertIn("endpoint-section", rules(text))

    def test_bad_field_required(self):
        self.assertIn("field-required", rules(self.mutate("|id|integer(int64)|false|none|", "|id|integer(int64)|no|none|")))

    def test_broken_model_anchor(self):
        self.assertIn("model-anchor", rules(self.mutate('<a id="schema_Pet"></a>', "")))

    def test_column_count(self):
        self.assertIn("table-columns", rules(self.mutate("|name|string|false|none||类别名称。|", "|name|string|false|none|类别名称。|")))


if __name__ == "__main__":
    unittest.main()
