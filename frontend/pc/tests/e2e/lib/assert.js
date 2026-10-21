/** 断言：失败抛 AssertionError，由执行器记录断言信息与失败产物。 */

class AssertionError extends Error
{
  constructor(message)
  {
    super(message)
    this.name = 'AssertionError'
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

  contains(text, keyword, message)
  {
    if (!String(text).includes(keyword))
    {
      throw new AssertionError(`${message}（期望包含「${keyword}」，实际「${String(text).slice(0, 120)}」）`)
    }
  }
}

module.exports = { assert, AssertionError }
