package com.deepthinking.service.impl;

import com.deepthinking.common.utils.StringUtil;
import com.deepthinking.mysql.entity.StockTechMinute;
import com.deepthinking.strategy.*;
import com.deepthinking.strategy.signal.DivergenceSignal;
import com.deepthinking.strategy.signal.VolumeAndPriceSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.common.constant.Constants.ZONE_ID;
import static com.deepthinking.strategy.OverNightStrategy.calcVolumeAndPrice;
import static com.deepthinking.strategy.OverNightStrategy.judgeDivergence;
import static com.deepthinking.strategy.StrategyUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTechMinuteCalculator {


    /**
     * ================= 实时计算分时指标 ====================
     * 日线共振：确定股票能不能做
     * 分时指标：确定什么时候买
     * 双重共振：胜率可达 70%~85%（超短线 1-3 天）
     * 所有指标周期统一，无滞后、无冲突
     */
    public static StockTechMinute calcMinuteIndicator(List<StockTechMinute> list) {
        int size = list.size();
        StockTechMinute tech = list.getLast();
        // 至少需要10分钟数据（适配分时MA10/BOLL10）
        if (list.size() < 15) {
            log.warn("分时数据不足不计算，必须满足15条");
            return tech;
        }

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(tech.getStockCode() + "_Minute").build();
        for (StockTechMinute t : list) {
            Instant tr = t.getTradeDate().atTime(t.getTradeTime()).atZone(ZoneId.of(ZONE_ID)).toInstant();
            series.addBar(new BaseBar(Duration.ofMinutes(1), tr.minusSeconds(60), tr,
                    DecimalNum.valueOf(t.getOpen()),
                    DecimalNum.valueOf(t.getHigh()),
                    DecimalNum.valueOf(t.getLow()),
                    DecimalNum.valueOf(t.getClose()),
                    DecimalNum.valueOf(t.getVolume()),
                    DecimalNum.valueOf(t.getAmount()),
                    1));
        }

        BigDecimal currClose = tech.getClose();
        int lastIndex = series.getEndIndex();
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        double ma5 = new SMAIndicator(volumeIndicator, 5).getValue(lastIndex).doubleValue();
        tech.setVolumeRatio(BigDecimal.valueOf(tech.getVolume() / ma5));

        // 1. EMA（指数移动平均） 短线参数：3 5 10   确定当前波段的多空基调     -- 隔夜条件：价格站上 EMA5/EMA10 → 隔夜安全；跌破 EMA10 → 不隔夜。
        DtEMAIndicator ema3 = new DtEMAIndicator(closePriceIndicator, 3);
        DtEMAIndicator ema5 = new DtEMAIndicator(closePriceIndicator, 5);
        DtEMAIndicator ema10 = new DtEMAIndicator(closePriceIndicator, 10);
        Num ema5Num = ema5.getValue(lastIndex);
        Num ema10Num = ema10.getValue(lastIndex);
        tech.setEma3(ema3.getValue(lastIndex).bigDecimalValue());
        tech.setEma5(ema5Num.bigDecimalValue());
        tech.setEma10(ema10Num.bigDecimalValue());

        DtBIASIndicator biasInd = new DtBIASIndicator(series, 5);
        Num bias = biasInd.getValue(lastIndex);
        tech.setBias(bias.bigDecimalValue());

        // 2. MACD（平滑异同移动平均指数）（趋势+动能） 短线参数(fast=5, slow=13, signal=2)   零轴确定长短周期动量方向    -- 隔夜条件：MACD红柱、DIF > DEA。
        DtMACDIndicator macdInd = new DtMACDIndicator(closePriceIndicator, 5, 13, 2);    // 柱状图 (Histogram) = MACD线 - 信号线
        DtMACDIndicator.CrossStatus macdStatus = macdInd.getCrossStatus();
        tech.setMacdDif(macdInd.getDIF().bigDecimalValue());
        tech.setMacdDea(macdInd.getDEA().bigDecimalValue());
        tech.setMacdBar(macdInd.getHistogram().bigDecimalValue());
        tech.setMacdStatus(macdStatus);

        // 3. BOLL（布林带）短线参数：10 2  衡量价格相对于波动的边界位置   -- 隔夜条件：价格在中轨之上，可持仓过夜，若跌破中轨则需离场
        DtBOLLIndicator bollInd = new DtBOLLIndicator(series, 10, 2);
        tech.setBollMid(bollInd.getMid().bigDecimalValue());
        tech.setBollUpper(bollInd.getUpper().bigDecimalValue());
        tech.setBollLower(bollInd.getLower().bigDecimalValue());
        tech.setBollMouthStatus(bollInd.getMouthStatus());
        tech.setBollMidTrend(bollInd.getMidTrend());

        // 4. RSI（相对强弱指标） 超短线最灵：6    衡量市场强弱与超买超卖
        DtRSIIndicator rsiInd = new DtRSIIndicator(series, 6);
        tech.setRsi6(rsiInd.getValue(lastIndex).bigDecimalValue());

        // 5. KDJ（随机指标）短线参数：5 2 2   对短线拐点极其灵敏    -- 隔夜条件：J 在 50~80 之间最稳；J>90 不隔夜。   默认算法可能与通达信/同花顺略有差异（平滑方式）
        DtKDJIndicator kdjInd = new DtKDJIndicator(series, 5, 2, 2);
        Num k = kdjInd.getK();
        Num d = kdjInd.getD();
        Num j = kdjInd.getJ();
        tech.setKdjK(k.bigDecimalValue());
        tech.setKdjD(d.bigDecimalValue());
        tech.setKdjJ(j.bigDecimalValue());
        tech.setKdjStatus(kdjInd.getCrossStatus());


        // 6. WR（威廉指标）极短线参数：6   用于1分钟或5分钟线，适合捕捉极速脉冲行情，预判趋势衰减      -- 隔夜条件：WR < 20 超买 → 不隔夜; WR > 80 超卖 → 可低吸隔夜; WR从超卖区回升时配合OBV放量可加仓。
        DtWRIndicator wrInd = new DtWRIndicator(series, 6);
        Num wr = wrInd.getValue(lastIndex);
        tech.setWr6(wr.bigDecimalValue());

        // 7. VMACD（成交量MACD）  短线参数：5,13,1   量平滑异同平均，量化资金动能    -- 隔夜条件：VMACD 红柱 → 量价配合
        DtVMACDIndicator vmacdInd = new DtVMACDIndicator(volumeIndicator, 5, 13, 2);
        DtVMACDIndicator.CrossStatus vmacdStatus = vmacdInd.getCrossStatus();
        tech.setVmacdDif(vmacdInd.getDIF().bigDecimalValue());
        tech.setVmacdDea(vmacdInd.getDEA().bigDecimalValue());
        tech.setVmacdBar(vmacdInd.getHistogram().bigDecimalValue());
        tech.setVmacdStatus(vmacdStatus);

        // 8. OBV_MA 能量潮均线确认资金流入流出     -- 隔夜条件：OBV > OBV_MA5
        DtOBVMAIndicator obvmaInd = new DtOBVMAIndicator(series, 5);
        Num obvNum = obvmaInd.getObv();
        Num obvMa5Num = obvmaInd.getObvMa();
        tech.setObv(obvNum.longValue());
        tech.setObvMa5(obvMa5Num.longValue());
        tech.setObvStatus(obvmaInd.getCrossStatus());

        Num highest = new HighestValueIndicator(new HighPriceIndicator(series), size / 2).getValue(lastIndex);
        Num lowest = new LowestValueIndicator(new LowPriceIndicator(series), size / 2).getValue(lastIndex);

        // ======================== 指标背离（Divergence）  分时线指标共 6分 ========================
        DivergenceSignal dvg = judgeDivergence(currClose, highest, lowest, macdInd, vmacdInd, kdjInd, rsiInd, obvmaInd, wrInd, null, null);
        tech.setDivergenceType(dvg.getDivergenceType());
        tech.setDivergenceStrength(dvg.getDivergenceStrength());
        tech.setDivergenceResult(dvg.getDivergenceResult());
        log.info("-----计算背离：{} {}_{}_{}", series.getName(), dvg.getDivergenceType(), dvg.getDivergenceStrength(), dvg.getDivergenceResult());

        // ======================== 量价关系 ========================
        VolumeAndPriceSignal volumeAndPriceSignal = calcVolumeAndPrice(series, highest, lowest, ema5Num, ema10Num, bias, tech.getVolumeRatio(), obvmaInd, bollInd, dvg);
        tech.setSignalType(volumeAndPriceSignal.getSignalType());
        tech.setSignalLevel(volumeAndPriceSignal.getSignalLevel());
        tech.setSignalResult(volumeAndPriceSignal.getSignalResult());






        // -------------- 分时多因子共振信号:EMA、MACD、RSI、KDJ、WR、BOLL、VMACD、OBVMA及量价关系（买入和评分）----------------------
        short buyScore = 0;
        short sellScore = 0;
        List<String> buyReasons = new ArrayList<>();
        List<String> sellReasons = new ArrayList<>();
        // 一. 趋势类指标 多头基调(EMA + MACD) + 波动爆发 (BOLL)：40分
        // 1. EMA 多头排列  10分
        if (currClose.compareTo(tech.getEma5()) > 0 && ema5Num.isGreaterThan(ema10Num)) {
            buyScore += 10;
            buyReasons.add("EMA多头排列,短期强势(价格>EMA5>EMA10)");
        } else if (ema5Num.isGreaterThan(ema10Num) && ema5.getValue(lastIndex - 1).isLessThanOrEqual(ema10.getValue(lastIndex - 1))) {      // 金叉
            buyScore += 5;
            buyReasons.add("EMA金叉");   // 金叉: 当前 MA5 > MA10; 前一刻 MA5 <= MA10
//            tech.setEmaGolden(GOLDEN_CROSS);
        }
        // 2. MACD 零轴上金叉 15分
        if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {   // 金叉且红柱放大
            buyScore += 15;
            buyReasons.add("MACD零轴上金叉且红柱放大(动能强)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("MACD零轴上金叉");
        }
        // 3. BOLL 突破下轨支撑 15分
        if (bollInd.isBreakoutDown()) {
            buyScore += 10;
            buyReasons.add("价格突破BOLL下轨(买入信号)");
        }
        // BOll "开口上涨"：中轨向上倾斜 + 布林带开口 15分
        if (bollInd.isBullishBreakout()) {
            buyScore += 15;
            buyReasons.add("BOLL开口且中轨向上倾斜(买入信号)");
        }

        // 二 动能类指标 灵敏择时 (KDJ + RSI + WR)：40分
        // 4. RSI 在50~70之间最强  10分
        if (rsiInd.isOversold()) {     // 向上反转信号
            buyScore += 10;
            buyReasons.add("RSI超卖(<30)");
        }
        // 5. KDJ 对短线拐点极其灵敏  10分
        if (tech.getKdjStatus() == DtKDJIndicator.CrossStatus.GOLDEN_CROSS) {   // 金叉
            if (k.isLessThanOrEqual(NUM_20) && d.isLessThanOrEqual(NUM_20)) {
                buyScore += 10;
                buyReasons.add("(KDJ低位金叉，强烈买入信号(K≤20)");       // 低位金叉（K<20）：代表价格超跌后的动能反转，此时买入信号最为准确。
            } else {
                buyScore += 5;
                buyReasons.add("(KDJ金叉");
            }
        }
        //  KDJ超卖区（机会显现）  10分
        if (j.isLessThanOrEqual(NUM_10)) {                            // 精准买卖点（J值比K/D更准）
            buyScore += 10;
            buyReasons.add("(KDJ严重超卖，买入信号(J≤10)");
        } else if (j.isLessThanOrEqual(NUM_20)) {
            buyScore += 5;
            buyReasons.add("(KDJ超卖，买入信号(J≤20)");
        }
        // 6. WR ≥ 80 超卖区回升并突破-50, 辅助确认超卖（避免RSI假信号）  10分
        if (wrInd.isOversold()) {
            buyScore += 10;
            buyReasons.add("WR超卖区，买入机会(≥80)");    // 等待信号确认,股价重新站上分时均价线时，才是安全的低吸时点。
        }


        // 三 量价类指标 量能确认 (VMACD + OBVMA)：25分
        // 7. VMACD 量能验证真伪关键   -- 隔夜条件：VMACD 红柱 → 量价配合  15分
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {   // 金叉且红柱放大
            buyScore += 15;
            buyReasons.add("VMACD零轴上金叉且红柱放大(放量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("VMACD零轴上金叉(放量)");
        }
        // 8. OBVMA 能量潮均线 -- 隔夜条件：OBV > OBV_MA5  10分
        if (tech.getObvStatus() == DtOBVMAIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("OBV金叉 资金流入(买入信号)");
        }
        tech.setBuyScore(buyScore);
        tech.setBuyReason(StringUtil.joinWithIndex(COMMA, buyReasons));

        // -------------- 分时多因子共振信号:EMA、MACD、RSI、KDJ、WR、BOLL、VMACD、OBVMA及量价关系（卖出和评分）----------------------
        // 一 趋势类指标 多头基调(EMA + MACD) + 波动爆发 (BOLL)：40分
        // 1. EMA空头排列  10分
        if (currClose.compareTo(tech.getEma5()) < 0 && ema5Num.isLessThan(ema10Num)) {
            sellScore += 10;
            sellReasons.add("EMA空头排列,短期弱势(价格<EMA5<EMA10)");
        } else if (ema5Num.isLessThan(ema10Num) && ema5.getValue(lastIndex - 1).isGreaterThan(ema10.getValue(lastIndex - 1))) {      // 死叉
            sellScore += 5;
            sellReasons.add("EMA死叉");
//            tech.setEmaGolden(DEATH_CROSS);
        }
        // 2. MACD 零轴下死叉 15分
        if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {          // 死叉且绿柱放大
            sellScore += 15;
            sellReasons.add("MACD零轴下死叉且绿柱放大(动能弱)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("MACD零轴下死叉");
        }
        // 3. BOLL 突破上轨压力  15分  -- 短线止盈离场点
        if (bollInd.isBreakoutUp()) {
            sellScore += 10;
            sellReasons.add("价格突破BOLL上轨(卖出信号)");
        }
        //  BOLL "收口盘整"：布林带收口 + 中轨走平或下倾  15分
        if (bollInd.isConsolidation()) {           // 扩大超过5%才视为有效，避免微小平移干扰。
            // 小于其移动平均 → 开口收窄。价格位于中轨下方，或中轨向下倾斜
            sellScore += 15;
            sellReasons.add("BOLL开口收窄且中轨走平或下倾(卖出信号)");
        }

        // 二 动能类指标  灵敏择时 (KDJ + RSI + WR)：40分
        // 4. RSI -- 隔夜条件：RSI6 在 50~70 之间最强；>80 不隔夜。  10分
        if (rsiInd.isOverbought()) {        // 向下反转信号
            sellScore += 10;
            sellReasons.add("RSI超买(>70)");
        }
        // 5. KDJ高位死叉  10分
        if (tech.getKdjStatus() == DtKDJIndicator.CrossStatus.DEATH_CROSS) {                            // 死叉
            if (k.isGreaterThanOrEqual(NUM_80) && d.isGreaterThanOrEqual(NUM_80)) {
                sellScore += 10;
                sellReasons.add("(KDJ高位死叉，强烈卖出信号(K≥80)");
            } else {
                sellScore += 5;
                sellReasons.add("(KDJ死叉");
            }
        }
        //   KDJ超买区（风险积聚） 10分
        if (j.isGreaterThanOrEqual(NUM_90)) {
            sellScore += 10;
            sellReasons.add("(KDJ严重超买，卖出信号(J≥90)");
        } else if (j.isGreaterThanOrEqual(NUM_80)) {
            sellScore += 5;
            sellReasons.add("(KDJ超买，卖出信号(J≥80)");
        }
        // 6. WR ≤ 20 超买区时，系统发出首个减仓信号。  10分
        if (wrInd.isOverbought()) {
            sellScore += 10;
            sellReasons.add("WR超买区，卖出信号(≤20)");
        }

        // 三 量价类指标 量能确认 (VMACD + OBVMA)：25分
        // 7. VMACD（成交量MACD）   15分
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {
            sellScore += 15;
            sellReasons.add("VMACD零轴下死叉且绿柱放大(缩量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("VMACD零轴下死叉(缩量)");
        }
        // 8. OBVMA 能量潮均线    10分
        if (tech.getObvStatus() == DtOBVMAIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("OBV死叉 资金流出(卖出信号)");
        }
        tech.setSellScore(sellScore);
        tech.setSellReason(StringUtil.joinWithIndex(COMMA, sellReasons));

        return tech;
    }


}
