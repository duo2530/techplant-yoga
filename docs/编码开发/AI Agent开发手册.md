# AI Agent 开发手册
## 1、版本记录

| 版本 | 日期 | 修改内容 |
|---|---|---|
| v0.1 | 2026-09-29 | 初始编写 |

## 2、手册

- 使用 lombokproject 的 @Data，避免繁琐的 getter/setter
- 使用 lombokproject 的 @RequiredArgsConstructor + final，完成 Bean 的注入
- 使用 mapstruct 解决 Bean 的转换问题
- 通过[项目基础设置.md]了解当前技术栈，选用合适的版本
