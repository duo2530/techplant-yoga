package com.techplant.yoga.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.techplant.yoga.member.domain.MemberDO;

/** 会员 Mapper（账号与会员共用 {@code t_member}）。 */
public interface MemberMapper extends BaseMapper<MemberDO> {
}
