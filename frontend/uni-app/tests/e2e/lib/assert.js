/** 断言与跳过标记：失败抛 AssertionError，环境不满足的用例抛 SkipError。 */

class AssertionError extends Error
{
  constructor(message)
  {
    super(message)
    this.name = 'AssertionError'
  }
}

class SkipError extends Error
{
  constructor(message)
  {
    super(message)
    this.name = 'SkipError'
  }
}

const assert = {
  ok(value, message)
  {
    if (!value)
    {
      throw new AssertionError(message)
    }
  },

  equal(actual, expected, message)
  {
    if (actual !== expected)
    {
      throw new AssertionError(`${message}（期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}）`)
    }
  },

  /** 数值近似比较：用于进度条宽度这类按像素取整的比例 */
  close(actual, expected, delta, message)
  {
    if (!(Math.abs(actual - expected) <= delta))
    {
      throw new AssertionError(`${message}（期望 ${expected} ±${delta}，实际 ${actual}）`)
    }
  },

  contains(text, keyword, message)
  {
    if (!String(text).includes(keyword))
    {
      throw new AssertionError(`${message}（期望包含「${keyword}」，实际「${String(text).slice(0, 80)}」）`)
    }
  }
}

module.exports = { assert, AssertionError, SkipError }
