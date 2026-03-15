package com.deepthinking.mysql.entity;

import com.alibaba.fastjson2.annotation.JSONField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepthinking.strategy.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.deepthinking.common.constant.Constants.ID;

/**
 * 股票日线技术指标表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("stock_tech_daily")
public class StockTechDaily extends BaseEntity {


    @TableId(value = ID, type = IdType.AUTO)
    private Integer id;

    private String stockCode;
    private String stockName;

    @JSONField(format = "yyyy-MM-dd")
    private LocalDate tradeDate;

    private StrategyUtils.SignalType signalType;
    private StrategyUtils.SignalLevel signalLevel;
    private String signalResult;

    // 超短线均线： 5/10/20
    private BigDecimal ema5;
    private BigDecimal ema10;
    private BigDecimal ema20;
    private BigDecimal bias;       // 乖离率

    // MACD(5,13,2)
    private BigDecimal macdDif;
    private BigDecimal macdDea;
    private BigDecimal macdBar;
    private DtMACDIndicator.CrossStatus macdStatus;

    // 超短线RSI(6)
    private BigDecimal rsi6;

    // KDJ(5,2,2)
    private BigDecimal kdjK;
    private BigDecimal kdjD;
    private BigDecimal kdjJ;
    private DtKDJIndicator.CrossStatus kdjStatus;

    // BOLL(10) 布林带状态：1=收口,2=开口,3=正常
    private BigDecimal bollMid;
    private BigDecimal bollUpper;
    private BigDecimal bollLower;
    private DtBOLLIndicator.MouthStatus bollMouthStatus;
    private DtBOLLIndicator.MidTrend bollMidTrend;

    // WR(6)
    private BigDecimal wr6;

    // VMACD(5,13,1)
    private BigDecimal vmacdDif;
    private BigDecimal vmacdDea;
    private BigDecimal vmacdBar;
    private DtVMACDIndicator.CrossStatus vmacdStatus;

    // OBV + OBV_MA5
    private Long obv;
    private Long obvMa5;
    private DtOBVMAIndicator.CrossStatus obvStatus;

    // ATR(6)
    private BigDecimal atr;
    private BigDecimal mtr;

    // ADX(8)
    private BigDecimal adx;

    // CCI(8)
    private BigDecimal cci;

    // CYC(5,14,34)
    private BigDecimal cyc5;
    private BigDecimal cyc13;
    private BigDecimal cyc34;

    // MFI(8)
    private BigDecimal mfi;

    private BigDecimal avgCost;             // 筹码平均成本（成交量加权）(30日)
    private BigDecimal costConcentration;   // 筹码集中度（%，值越小越集中）

    private StrategyUtils.DivergenceType divergenceType;
    private Short divergenceStrength;
    private String divergenceResult;

    private Double buyScore;
    private String buyReason;
    private Double sellScore;
    private String sellReason;



}
