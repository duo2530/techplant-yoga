---
name: verify-reservation-concurrency
description: 用 TLA+ 模型和 TLC 穷举检查预约、排队、取消、补位、人数不足自动取消、停卡与排课状态修改在并发下的行锁与事务协议，确认不超卖、不重复有效预约、返次恰好一次。修改预约服务、排课或会员卡的加锁、事务隔离级别、条件更新，或排课状态流转之后使用，也用于“检查预约并发”“跑一下 TLA+ 模型”。
---

# verify-reservation-concurrency

本 skill 的正文维护在 [.agents/skills/verify-reservation-concurrency/SKILL.md](../../../.agents/skills/verify-reservation-concurrency/SKILL.md)，这里只给 Claude Code 提供入口。先完整读那份文件，再按它执行，不要在这里另写步骤。
