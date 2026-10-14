package com.techplant.yoga.membercard.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/**
 * 卡号生成器：{@code yyyyMMdd + 门店编号(4) + HHmmss}，共 18 位。
 *
 * <p>时间取发卡时刻的会员卡业务时区 {@code Asia/Shanghai}，不接受客户端传入；
 * 组成精度只到秒，同一开卡门店同一秒只能生成一个卡号，唯一性由 {@code uk_card_no} 兜底。</p>
 */
@Component
public class CardNoGenerator
{
    /** 会员卡业务时区：卡号与有效期都按它计算 */
    public static final ZoneId CARD_ZONE = ZoneId.of("Asia/Shanghai");

    /** 卡号中的发卡日期 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 卡号中的发卡时间（24 小时制） */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    /** 按当前时间生成卡号 */
    public String generate(String storeNo)
    {
        return generate(storeNo, LocalDateTime.now(CARD_ZONE));
    }

    /**
     * 按给定时间生成卡号
     *
     * @param storeNo   开卡门店编号（四位数字，取自门店 {@code store_no}）
     * @param issueTime 发卡时间，由调用方在事务内读取
     */
    public String generate(String storeNo, LocalDateTime issueTime)
    {
        if (storeNo == null || storeNo.length() != 4)
        {
            throw new ServiceException("开卡门店编号不是四位数字，无法生成卡号", 500);
        }
        return issueTime.format(DATE_FORMAT) + storeNo + issueTime.format(TIME_FORMAT);
    }
}
