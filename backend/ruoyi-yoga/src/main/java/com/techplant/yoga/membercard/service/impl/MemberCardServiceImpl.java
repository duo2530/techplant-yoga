package com.techplant.yoga.membercard.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.exception.ServiceException;
import com.techplant.yoga.common.response.PageResult;
import com.techplant.yoga.common.util.CurrentUserUtils;
import com.techplant.yoga.member.service.MemberService;
import com.techplant.yoga.member.vo.MemberSummaryVO;
import com.techplant.yoga.membercard.convert.MemberCardConverter;
import com.techplant.yoga.membercard.dao.MemberCardDao;
import com.techplant.yoga.membercard.domain.MemberCardDO;
import com.techplant.yoga.membercard.dto.MemberCardCreateDTO;
import com.techplant.yoga.membercard.dto.MemberCardStopDTO;
import com.techplant.yoga.membercard.enums.CourseScopeEnum;
import com.techplant.yoga.membercard.enums.MemberCardTypeEnum;
import com.techplant.yoga.membercard.query.MemberCardQuery;
import com.techplant.yoga.membercard.service.CardNoGenerator;
import com.techplant.yoga.membercard.service.MemberCardService;
import com.techplant.yoga.membercard.vo.MemberCardDetailVO;
import com.techplant.yoga.membercard.vo.MemberCardListItemVO;
import com.techplant.yoga.store.service.StoreService;
import com.techplant.yoga.store.vo.StoreSummaryVO;

/** 会员卡管理业务实现。 */
@Service
public class MemberCardServiceImpl implements MemberCardService
{
    /** 资源不存在：会员卡、会员或门店已被删除 */
    private static final int NOT_FOUND = 404;

    /** 状态冲突：卡号冲突、开卡与停卡的状态流转非法 */
    private static final int CONFLICT = 409;

    /** 参数校验失败／系统异常 */
    private static final int PARAM_INVALID = 500;

    /** 卡号唯一索引名（唯一键冲突归类用） */
    private static final String UK_CARD_NO = "uk_card_no";

    private final MemberCardDao memberCardDao;

    private final MemberService memberService;

    private final StoreService storeService;

    private final CardNoGenerator cardNoGenerator;

    public MemberCardServiceImpl(MemberCardDao memberCardDao, MemberService memberService, StoreService storeService,
            CardNoGenerator cardNoGenerator)
    {
        this.memberCardDao = memberCardDao;
        this.memberService = memberService;
        this.storeService = storeService;
        this.cardNoGenerator = cardNoGenerator;
    }

    @Override
    public PageResult<MemberCardListItemVO> page(MemberCardQuery query)
    {
        IPage<MemberCardDO> page = memberCardDao.selectPage(query);
        List<MemberCardDO> cards = page.getRecords();
        // IPage 不出 service 层
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), toListItemVOList(cards));
    }

    @Override
    public MemberCardDetailVO getById(Long cardId)
    {
        return detailOf(requireCard(cardId));
    }

    /**
     * 发卡：先校验卡类型字段组合，再校验会员与门店存在，最后生成卡号入库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(MemberCardCreateDTO dto)
    {
        MemberCardTypeEnum cardType = MemberCardTypeEnum.validate(dto.getCardType());
        CourseScopeEnum.validate(dto.getCourseScope());
        validateCombination(dto, cardType);

        if (!memberService.existsById(dto.getMemberId()))
        {
            throw new ServiceException("会员不存在或已被删除", NOT_FOUND);
        }

        // 开卡门店与适用门店一次查回：前者决定卡号里的门店编号，后者只校验存在
        Map<Long, StoreSummaryVO> stores = indexStores(
                storeService.summaries(Arrays.asList(dto.getOpenStoreId(), dto.getStoreId())));
        StoreSummaryVO openStore = stores.get(dto.getOpenStoreId());
        if (openStore == null)
        {
            throw new ServiceException("开卡门店不存在或已被删除", NOT_FOUND);
        }
        if (cardType.isTimesCard() && stores.get(dto.getStoreId()) == null)
        {
            throw new ServiceException("适用门店不存在或已被删除", NOT_FOUND);
        }

        // 年卡的有效期数量由服务端固定为 1 年，有效期单位与起止时间留到正式开卡时写入
        Integer validPeriodCount = cardType.isTimesCard() ? dto.getValidPeriodCount()
                : Integer.valueOf(MemberCardTypeEnum.ANNUAL_PERIOD_COUNT);
        MemberCardDO card = MemberCardConverter.toDO(dto, cardType, cardNoGenerator.generate(openStore.getStoreNo()),
                validPeriodCount);
        try
        {
            int rows = memberCardDao.insert(card);
            if (rows != 1 || card.getId() == null)
            {
                throw new ServiceException("发卡失败", PARAM_INVALID);
            }
        }
        catch (DuplicateKeyException e)
        {
            throw cardNoConflict(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberCardDetailVO activate(Long cardId)
    {
        // 当前读加行锁：并发开卡只有一次能通过状态检查
        MemberCardDO card = requireCardForUpdate(cardId);
        if (card.getStopTime() != null)
        {
            throw new ServiceException("该会员卡已停卡，不能开卡", CONFLICT);
        }
        if (card.getActivateTime() != null)
        {
            throw new ServiceException("该会员卡已开卡，不能重复开卡", CONFLICT);
        }

        MemberCardTypeEnum cardType = MemberCardTypeEnum.of(card.getCardType());
        if (cardType == null)
        {
            throw new ServiceException("卡类型取值非法，无法开卡", PARAM_INVALID);
        }
        int validPeriodCount = cardType.isTimesCard() ? requireValidPeriodCount(card)
                : MemberCardTypeEnum.ANNUAL_PERIOD_COUNT;

        // 有效期起为开卡日 00:00:00，止为「起始日 + 有效天数 - 1 天」的 23:59:59，含首尾日
        LocalDateTime activateTime = LocalDateTime.now(CardNoGenerator.CARD_ZONE);
        LocalDateTime validStartTime = activateTime.toLocalDate().atStartOfDay();
        LocalDateTime validEndTime = validStartTime.toLocalDate()
                .plusDays(cardType.validDays(validPeriodCount) - 1L).atTime(23, 59, 59);

        if (memberCardDao.activate(cardId, cardType.getValidPeriodUnit().getCode(), validStartTime, validEndTime,
                activateTime, CurrentUserUtils.getUserIdOrNull()) != 1)
        {
            throw new ServiceException("会员卡状态已变更，请刷新后重试", CONFLICT);
        }
        return detailOf(requireCard(cardId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberCardDetailVO stop(Long cardId, MemberCardStopDTO dto)
    {
        MemberCardDO card = requireCardForUpdate(cardId);
        if (card.getStopTime() != null)
        {
            throw new ServiceException("该会员卡已停卡，不能重复停卡", CONFLICT);
        }
        String stopReason = dto == null ? null : trimToNull(dto.getStopReason());
        if (stopReason == null)
        {
            throw new ServiceException("停卡原因不能为空", PARAM_INVALID);
        }

        // 停卡只写停卡时间与原因，剩余次数与有效期原样保留
        if (memberCardDao.stop(cardId, LocalDateTime.now(CardNoGenerator.CARD_ZONE), stopReason,
                CurrentUserUtils.getUserIdOrNull()) != 1)
        {
            throw new ServiceException("会员卡状态已变更，请刷新后重试", CONFLICT);
        }
        return detailOf(requireCard(cardId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long cardId)
    {
        requireCard(cardId);
        if (memberCardDao.deleteById(cardId) != 1)
        {
            // 并发下已被删除：影响行数为 0
            throw notFound();
        }
    }

    /** 批量转列表项：会员名与门店名按 ID 批量补齐，关联对象已删除时由转换器兜底为空字符串 */
    private List<MemberCardListItemVO> toListItemVOList(List<MemberCardDO> cards)
    {
        List<MemberCardListItemVO> rows = new ArrayList<MemberCardListItemVO>();
        if (cards == null || cards.isEmpty())
        {
            return rows;
        }
        Map<Long, String> memberNames = loadMemberNames(cards);
        Map<Long, StoreSummaryVO> stores = loadStoreSummaries(cards);
        LocalDate today = LocalDate.now(CardNoGenerator.CARD_ZONE);
        for (MemberCardDO card : cards)
        {
            rows.add(MemberCardConverter.toListItemVO(card, memberNames.get(card.getMemberId()),
                    storeNoOf(stores, card.getOpenStoreId()), storeNameOf(stores, card.getOpenStoreId()),
                    storeNameOf(stores, card.getStoreId()), remainingDays(card, today)));
        }
        return rows;
    }

    /** 单卡转详情：字段与列表项一致 */
    private MemberCardDetailVO detailOf(MemberCardDO card)
    {
        List<MemberCardDO> cards = Collections.singletonList(card);
        Map<Long, String> memberNames = loadMemberNames(cards);
        Map<Long, StoreSummaryVO> stores = loadStoreSummaries(cards);
        return MemberCardConverter.toDetailVO(card, memberNames.get(card.getMemberId()),
                storeNoOf(stores, card.getOpenStoreId()), storeNameOf(stores, card.getOpenStoreId()),
                storeNameOf(stores, card.getStoreId()),
                remainingDays(card, LocalDate.now(CardNoGenerator.CARD_ZONE)));
    }

    /** 会员卡上的次数类字段组合校验，规则随卡类型分流 */
    private void validateCombination(MemberCardCreateDTO dto, MemberCardTypeEnum cardType)
    {
        if (cardType.isTimesCard())
        {
            if (dto.getStoreId() == null)
            {
                throw new ServiceException("次数卡必须选择适用门店", PARAM_INVALID);
            }
            if (dto.getPurchaseTimes() == null || dto.getGiftTimes() == null)
            {
                throw new ServiceException("次数卡必须填写购买次数与赠送次数", PARAM_INVALID);
            }
            if (dto.getPurchaseTimes() < 0 || dto.getGiftTimes() < 0)
            {
                throw new ServiceException("购买次数与赠送次数不能小于 0", PARAM_INVALID);
            }
            if (dto.getPurchaseTimes() + dto.getGiftTimes() <= 0)
            {
                throw new ServiceException("购买次数与赠送次数合计必须大于 0", PARAM_INVALID);
            }
            if (!MemberCardTypeEnum.isValidTimesPeriodCount(dto.getValidPeriodCount()))
            {
                throw new ServiceException("次数卡有效期数量只能是 3、6 或 12 个月", PARAM_INVALID);
            }
            return;
        }
        if (dto.getStoreId() != null)
        {
            throw new ServiceException("年卡不能选择适用门店", PARAM_INVALID);
        }
        if (dto.getPurchaseTimes() != null || dto.getGiftTimes() != null)
        {
            throw new ServiceException("年卡不能填写购买次数与赠送次数", PARAM_INVALID);
        }
        if (dto.getValidPeriodCount() != null)
        {
            throw new ServiceException("年卡不能填写有效期数量，有效期固定 1 年", PARAM_INVALID);
        }
    }

    /** 次数卡开卡时读取发卡保存的有效期数量，缺失按数据异常处理 */
    private int requireValidPeriodCount(MemberCardDO card)
    {
        if (card.getValidPeriodCount() == null)
        {
            throw new ServiceException("会员卡有效期数量缺失，无法开卡", PARAM_INVALID);
        }
        return card.getValidPeriodCount();
    }

    /**
     * 剩余天数（含今天）：只有年卡未过期时才给值，其余情况返回 {@code null}
     */
    private Integer remainingDays(MemberCardDO card, LocalDate today)
    {
        if (!MemberCardTypeEnum.ANNUAL.getCode().equals(card.getCardType()) || card.getValidEndTime() == null)
        {
            return null;
        }
        LocalDate endDate = card.getValidEndTime().toLocalDate();
        if (today.isAfter(endDate))
        {
            return null;
        }
        return (int) (ChronoUnit.DAYS.between(today, endDate) + 1);
    }

    /** 按 ID 取会员卡，不存在直接 404 */
    private MemberCardDO requireCard(Long cardId)
    {
        MemberCardDO card = memberCardDao.selectById(cardId);
        if (card == null)
        {
            throw notFound();
        }
        return card;
    }

    /** 按 ID 当前读取并锁定会员卡，行已被并发删除时同样按 404 处理 */
    private MemberCardDO requireCardForUpdate(Long cardId)
    {
        MemberCardDO card = memberCardDao.selectByIdForUpdate(cardId);
        if (card == null)
        {
            throw notFound();
        }
        return card;
    }

    /** 批量取会员显示名（id → 显示名）；会员已删除时不在结果中 */
    private Map<Long, String> loadMemberNames(List<MemberCardDO> cards)
    {
        List<Long> memberIds = new ArrayList<Long>();
        for (MemberCardDO card : cards)
        {
            addIfPresent(memberIds, card.getMemberId());
        }
        Map<Long, String> names = new HashMap<Long, String>();
        for (MemberSummaryVO member : memberService.summaries(memberIds))
        {
            names.put(member.getId(), member.getName());
        }
        return names;
    }

    /** 取卡片涉及的门店（开卡门店与适用门店）；门店已删除时不在结果中 */
    private Map<Long, StoreSummaryVO> loadStoreSummaries(List<MemberCardDO> cards)
    {
        List<Long> storeIds = new ArrayList<Long>();
        for (MemberCardDO card : cards)
        {
            addIfPresent(storeIds, card.getOpenStoreId());
            addIfPresent(storeIds, card.getStoreId());
        }
        return indexStores(storeService.summaries(storeIds));
    }

    private Map<Long, StoreSummaryVO> indexStores(List<StoreSummaryVO> summaries)
    {
        Map<Long, StoreSummaryVO> stores = new HashMap<Long, StoreSummaryVO>();
        for (StoreSummaryVO store : summaries)
        {
            stores.put(store.getId(), store);
        }
        return stores;
    }

    private String storeNoOf(Map<Long, StoreSummaryVO> stores, Long storeId)
    {
        StoreSummaryVO store = storeId == null ? null : stores.get(storeId);
        return store == null ? null : store.getStoreNo();
    }

    private String storeNameOf(Map<Long, StoreSummaryVO> stores, Long storeId)
    {
        StoreSummaryVO store = storeId == null ? null : stores.get(storeId);
        return store == null ? null : store.getName();
    }

    private void addIfPresent(Collection<Long> target, Long value)
    {
        if (value != null && !target.contains(value))
        {
            target.add(value);
        }
    }

    /** 卡号唯一键冲突：只有 uk_card_no 才是「同门店同秒已有发卡」，其余数据库异常原样抛出 */
    private RuntimeException cardNoConflict(DuplicateKeyException cause)
    {
        String message = cause.getMessage() == null ? "" : cause.getMessage();
        if (!message.contains(UK_CARD_NO))
        {
            return cause;
        }
        ServiceException exception = new ServiceException("该门店当前时间已有发卡记录，请稍后重试", CONFLICT);
        exception.initCause(cause);
        return exception;
    }

    private String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ServiceException notFound()
    {
        return new ServiceException("会员卡不存在或已被删除", NOT_FOUND);
    }
}
