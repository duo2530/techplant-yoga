package com.techplant.yoga.member.service.impl;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.member.convert.MemberConverter;
import com.techplant.yoga.member.dao.MemberDao;
import com.techplant.yoga.member.dao.MemberLevelDictDao;
import com.techplant.yoga.member.domain.MemberDO;
import com.techplant.yoga.member.dto.MemberCreateDTO;
import com.techplant.yoga.member.dto.MemberUpdateDTO;
import com.techplant.yoga.member.enums.MemberGenderEnum;
import com.techplant.yoga.member.query.MemberQuery;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.vo.MemberDetailVO;
import com.techplant.yoga.member.vo.MemberListItemVO;
import com.techplant.yoga.member.vo.MemberSummaryVO;

/** 会员基础能力实现。 */
@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    /** 资源不存在：会员已被删除 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：手机号已建档／已注册／并发冲突 */
    private static final int CONFLICT = 409;

    /** 参数校验失败 */
    private static final int PARAM_INVALID = 400;

    private static final int INTERNAL_ERROR = 500;

    /** 显示名前缀：忻会员 + 手机号后 4 位 */
    private static final String NAME_PREFIX = "忻会员";

    /** 新增会员未提交等级时取该等级码 */
    private static final int DEFAULT_LEVEL = 0;

    private final MemberDao memberDao;

    private final MemberLevelDictDao memberLevelDictDao;

    /**
     * 查询会员列表：等级筛选先按字典校验，筛选与分页下推给 DAO，只读不加事务
     */
    @Override
    public PageResult<MemberListItemVO> page(MemberQuery query) {
        Map<Integer, String> levelLabels = memberLevelDictDao.selectLevelLabelMap();
        validateLevel(query.getLevel(), levelLabels);

        IPage<MemberDO> page = memberDao.selectPage(query);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                MemberConverter.toListItemVOList(page.getRecords(), levelLabels));
    }

    /**
     * 查询会员详情：不存在（或已被物理删除）→ 404
     */
    @Override
    public MemberDetailVO getById(Long memberId) {
        return MemberConverter.toDetailVO(requireMember(memberId), memberLevelDictDao.selectLevelLabelMap());
    }

    /**
     * 新增会员档案：手机号唯一，等级按字典校验且缺省取 0，不写登录凭证；
     * 显示名与手机号后四位由本方法生成
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(MemberCreateDTO dto) {
        String phone = normalizePhone(dto.getPhone());
        MemberGenderEnum.validateIfPresent(dto.getGender());

        Map<Integer, String> levelLabels = memberLevelDictDao.selectLevelLabelMap();
        Integer level = dto.getLevel() == null ? Integer.valueOf(DEFAULT_LEVEL) : dto.getLevel();
        validateLevel(level, levelLabels);

        if (memberDao.selectByPhone(phone) != null) {
            throw new ServiceException("该手机号已建立会员档案", CONFLICT);
        }

        MemberDO member = MemberConverter.toDO(dto, level);
        member.setPhone(phone);
        member.setPhoneLast4(last4(phone));
        member.setName(displayName(phone));
        try {
            int rows = memberDao.insert(member);
            if (rows != 1) {
                throw new ServiceException("新增会员失败", INTERNAL_ERROR);
            }
        } catch (DuplicateKeyException e) {
            // uk_member_phone 并发兜底：唯一键冲突同样 409，不覆盖并发建档结果
            ServiceException exception = new ServiceException("该手机号已建立会员档案", CONFLICT);
            exception.initCause(e);
            throw exception;
        }
    }

    /**
     * 修改会员档案：只更新请求中出现的字段（性别／等级），等级按字典校验；
     * 手机号、显示名与登录字段不参与
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberDetailVO update(Long memberId, MemberUpdateDTO dto) {
        MemberDO member = requireMember(memberId);

        Integer gender = submittedValue(dto.genderPresent(), dto.getGender(), "性别");
        Integer level = submittedValue(dto.levelPresent(), dto.getLevel(), "会员等级");
        MemberGenderEnum.validateIfPresent(gender);
        Map<Integer, String> levelLabels = memberLevelDictDao.selectLevelLabelMap();
        validateLevel(level, levelLabels);

        if (gender != null || level != null) {
            memberDao.updateProfile(memberId, gender, level, CurrentUserUtils.getUserIdOrNull());
            // 并发删除可能提交在本事务快照之后：快照读仍会取到旧行，存在性以当前读为准
            member = requireMemberForUpdate(memberId);
        }
        return MemberConverter.toDetailVO(member, levelLabels);
    }

    /**
     * 删除会员：物理删除会员行，登录凭证随行删除
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long memberId) {
        requireMember(memberId);
        if (memberDao.deleteById(memberId) != 1) {
            // 并发下会员已被删除：删除影响行数为 0
            throw notFound();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void registerByPhone(String phone, String passwordHash) {
        String normalizedPhone = normalizePhone(phone);
        if (passwordHash == null || passwordHash.trim().isEmpty()) {
            throw new ServiceException("密码不能为空", PARAM_INVALID);
        }

        MemberDO existing = memberDao.selectByPhone(normalizedPhone);
        if (existing != null) {
            // 已注册：拒绝重复注册
            if (existing.isRegistered()) {
                throw new ServiceException("该手机号已注册", CONFLICT);
            }
            // 未注册档案：只补密码，保留原会员 ID、手机号、显示名、性别、等级与业务关联
            int rows = memberDao.updatePasswordIfUnregistered(existing.getId(), passwordHash);
            if (rows != 1) {
                // 影响行数为 0：并发下已被注册（或档案刚被删除），不覆盖别人的结果
                throw new ServiceException("该手机号已注册", CONFLICT);
            }
            return;
        }

        MemberDO member = new MemberDO();
        member.setPhone(normalizedPhone);
        member.setPassword(passwordHash);
        member.setPhoneLast4(last4(normalizedPhone));
        member.setName(displayName(normalizedPhone));
        member.setGender(MemberGenderEnum.UNKNOWN.getCode());
        member.setLevel(DEFAULT_LEVEL);
        try {
            int rows = memberDao.insert(member);
            if (rows != 1) {
                throw new ServiceException("注册失败，请稍后重试", INTERNAL_ERROR);
            }
        } catch (DuplicateKeyException e) {
            // uk_member_phone 并发兜底：唯一键冲突同样 409，不覆盖并发建档结果
            ServiceException exception = new ServiceException("该手机号已注册", CONFLICT);
            exception.initCause(e);
            throw exception;
        }
    }

    @Override
    public boolean existsById(Long memberId) {
        return memberDao.selectById(memberId) != null;
    }

    @Override
    public boolean isRegistered(Long memberId) {
        return memberDao.existsRegisteredById(memberId);
    }

    @Override
    public boolean lockForBooking(Long memberId) {
        return memberDao.selectByIdForUpdate(memberId) != null;
    }

    @Override
    public List<MemberSummaryVO> summaries(Collection<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return new ArrayList<MemberSummaryVO>();
        }
        Set<Long> distinctIds = new LinkedHashSet<Long>();
        for (Long memberId : memberIds) {
            if (memberId != null) {
                distinctIds.add(memberId);
            }
        }
        if (distinctIds.isEmpty()) {
            return new ArrayList<MemberSummaryVO>();
        }
        return MemberConverter.toSummaryVOList(memberDao.selectByIds(distinctIds));
    }

    /**
     * 按 ID 取会员，不存在直接 404
     */
    private MemberDO requireMember(Long memberId) {
        MemberDO member = memberDao.selectById(memberId);
        if (member == null) {
            throw notFound();
        }
        return member;
    }

    /**
     * 按 ID 当前读取会员，行已被并发删除时同样按 404 处理
     */
    private MemberDO requireMemberForUpdate(Long memberId) {
        MemberDO member = memberDao.selectByIdForUpdate(memberId);
        if (member == null) {
            throw notFound();
        }
        return member;
    }

    /**
     * 等级取值校验：以字典 {@code member_level} 为准，字典中不存在的等级码（含已停用等级）一律拒绝
     */
    private void validateLevel(Integer level, Map<Integer, String> levelLabels) {
        if (level == null) {
            return;
        }
        if (levelLabels.isEmpty()) {
            throw new ServiceException("会员等级字典未配置，无法保存会员等级", INTERNAL_ERROR);
        }
        if (!levelLabels.containsKey(level)) {
            throw new ServiceException("会员等级取值非法", PARAM_INVALID);
        }
    }

    /**
     * 取请求体显式提交的字段值：未提交返回 {@code null}（省略保留原值），提交 {@code null} 报参数错误
     */
    private Integer submittedValue(boolean present, Integer value, String fieldName) {
        if (!present) {
            return null;
        }
        if (value == null) {
            throw new ServiceException(fieldName + "不能为 null，若要保留原值请省略该字段", PARAM_INVALID);
        }
        return value;
    }

    /** 手机号规整与兜底校验：接口层已校验格式，这里防住 service 直调 */
    private String normalizePhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new ServiceException("手机号不能为空", PARAM_INVALID);
        }
        String normalized = phone.trim();
        if (normalized.length() < 4) {
            throw new ServiceException("手机号格式不正确", PARAM_INVALID);
        }
        return normalized;
    }

    /** 手机号后 4 位（phone_last4 与显示名共用） */
    private String last4(String phone) {
        return phone.substring(phone.length() - 4);
    }

    /** 会员显示名：忻会员 + 手机号后 4 位 */
    private String displayName(String phone) {
        return NAME_PREFIX + last4(phone);
    }

    private ServiceException notFound() {
        return new ServiceException("会员不存在或已被删除", NOT_FOUND);
    }
}
