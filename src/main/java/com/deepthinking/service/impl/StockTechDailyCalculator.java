package com.deepthinking.service.impl;

import com.deepthinking.common.utils.StringUtil;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockTechDaily;
import com.deepthinking.strategy.*;
import com.deepthinking.strategy.signal.DivergenceSignal;
import com.deepthinking.strategy.signal.ResonanceSignal;
import com.deepthinking.strategy.signal.VolumeAndPriceSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.common.constant.Constants.*;
import static com.deepthinking.strategy.OverNightStrategy.*;
import static com.deepthinking.strategy.StrategyUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTechDailyCalculator {


    /**
     * ==================全指标短线参数对照表（超短线 1-3 天）====================
     * 核心逻辑：缩短周期 + 提升敏感度
     * 指标	        原默认参数	    超短线参数	    调整目的
     * MACD/VMACD	12/26/9	        5/13/2	        MACD(5,13,2) + 红柱连续放大50% + 量比>2.5 = 短线黄金组。短线交易的"命脉"
     * MA 均线	    5/10/20/60	    3/5/10/20	    聚焦短期均线，捕捉 1-3 天趋势
     * RSI 强弱	    6/14	        3/9	            更快反应短期强弱，提前 1-2 个 K 线出信号
     * KDJ	随机指标 9（周期）	        6（周期）	    反应快，无滞后，信号滞后性从 2 天→1 天
     * BOLL 布林带   20（中轨）	    10      	    窄轨，精准捕捉短期突破 / 回调
     * WR 威廉指数	10	            6	            短期超买 / 超卖，低吸高抛更精准
     * OBV_MA 均线   20	            10	            短期资金趋势，匹配超短线周期
     * ATR 波动率   14	            6	            计算超短线止损
     * ADX 平均趋向指标
     * CCI 顺势指标  14	            8	            短期强势 / 反转
     * CYC 股价平均成本线
     * MFI（资金流）	14	            8	            短期资金进出，过滤无资金的假突破
     * 筹码集中度	    60	            30	            短期主力成本，避免中线筹码钝化
     * 背离回溯周期	10	            5	            只看最近 5 天背离
     * ======================================================================
     * 趋势类：均线系统（如EMA5/EMA13）、MACD、ADX（趋势强度）
     * 动能类：RSI、KDJ（超买超卖状态）
     * 波动率：ATR（用于设置止损，衡量隔夜风险）
     * 量能：OBV、VMACD（确认趋势的成交量支持）
     * 市场情绪：WR（威廉指标）
     * 位置：BOLL（价格在轨道中的位置）
     * 大周期趋势：15分钟或60分钟图的方向（过滤逆势隔夜持仓）
     */
    public static StockTechDaily calcMinuteIndicator(List<StockKlineDaily> list) {
        int size = list.size();
        if (list.size() < 15) {
            log.warn("日线数据不足不计算，必须满足15条");
            return null;
        }
        StockKlineDaily klineDaily = list.getLast();

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(klineDaily.getStockCode() + "_Daily").build();
        for (StockKlineDaily t : list) {
            series.addBar(new BaseBar(Duration.ofDays(1), null, null,
                    DecimalNum.valueOf(t.getOpen()),
                    DecimalNum.valueOf(t.getHigh()),
                    DecimalNum.valueOf(t.getLow()),
                    DecimalNum.valueOf(t.getClose()),
                    DecimalNum.valueOf(t.getVolume()),
                    DecimalNum.valueOf(t.getAmount()),
                    1));
        }
        int lastIndex = series.getEndIndex();
        ClosePriceIndicator closePriceInd = new ClosePriceIndicator(series);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);

        StockTechDaily tech = StockTechDaily.builder().stockCode(klineDaily.getStockCode()).stockName(klineDaily.getStockName()).tradeDate(klineDaily.getTradeDate()).build();

        // 1. EMA（指数移动平均） 短线参数：3 5 10   确定当前波段的多空基调     -- 隔夜条件：价格站上 EMA5/EMA10 → 隔夜安全；跌破 EMA10 → 不隔夜。
        DtEMAIndicator ema5Ind = new DtEMAIndicator(closePriceInd, 5);
        DtEMAIndicator ema10Ind = new DtEMAIndicator(closePriceInd, 10);
        Num ema5 = ema5Ind.getValue(lastIndex);
        Num ema10 = ema10Ind.getValue(lastIndex);
        tech.setEma5(ema5.bigDecimalValue());
        tech.setEma10(ema10.bigDecimalValue());

        DtBIASIndicator biasInd = new DtBIASIndicator(series, 5);
        Num bias = biasInd.getValue(lastIndex);
        tech.setBias(bias.bigDecimalValue());

        // 2. MACD（平滑异同移动平均指数）（趋势+动能） 短线参数(fast=5, slow=13, signal=2)   零轴确定长短周期动量方向    -- 隔夜条件：MACD红柱、DIF > DEA。
        DtMACDIndicator macdInd = new DtMACDIndicator(closePriceInd, 5, 13, 2);    // 柱状图 (Histogram) = MACD线 - 信号线
        tech.setMacdDif(macdInd.getDIF().bigDecimalValue());
        tech.setMacdDea(macdInd.getDEA().bigDecimalValue());
        tech.setMacdBar(macdInd.getHistogram().bigDecimalValue());
        tech.setMacdStatus(macdInd.getCrossStatus());

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
        tech.setKdjK(kdjInd.getK().bigDecimalValue());
        tech.setKdjD(kdjInd.getD().bigDecimalValue());
        tech.setKdjJ(kdjInd.getJ().bigDecimalValue());
        tech.setKdjStatus(kdjInd.getCrossStatus());


        // 6. WR（威廉指标）极短线参数：6   用于1分钟或5分钟线，适合捕捉极速脉冲行情，预判趋势衰减      -- 隔夜条件：WR < 20 超买 → 不隔夜; WR > 80 超卖 → 可低吸隔夜; WR从超卖区回升时配合OBV放量可加仓。
        DtWRIndicator wrInd = new DtWRIndicator(series, 6);
        tech.setWr6(wrInd.getValue(lastIndex).bigDecimalValue());

        // 7. VMACD（成交量MACD）  短线参数：5,13,1   量平滑异同平均，量化资金动能    -- 隔夜条件：VMACD 红柱 → 量价配合
        DtVMACDIndicator vmacdInd = new DtVMACDIndicator(volumeIndicator, 5, 13, 2);
        tech.setVmacdDif(vmacdInd.getDIF().bigDecimalValue());
        tech.setVmacdDea(vmacdInd.getDEA().bigDecimalValue());
        tech.setVmacdBar(vmacdInd.getHistogram().bigDecimalValue());
        tech.setVmacdStatus(vmacdInd.getCrossStatus());

        // 8. OBV_MA 能量潮均线确认资金流入流出     -- 隔夜条件：OBV > OBV_MA5
        DtOBVMAIndicator obvmaInd = new DtOBVMAIndicator(series, 5);
        tech.setObv(obvmaInd.getObv().longValue());
        tech.setObvMa5(obvmaInd.getObvMa().longValue());
        tech.setObvStatus(obvmaInd.getCrossStatus());

        // 9. ATR 波动率 计算超短线止损
        DtATRIndicator atrInd = new DtATRIndicator(series, 7);
        tech.setMtr(atrInd.getMtr().bigDecimalValue());
        tech.setAtr(atrInd.getAtr().bigDecimalValue());

        // 10. ADX（平均趋向指标）—— 判断趋势强度
        DtADXIndicator adxInd = new DtADXIndicator(series, 10);
        tech.setAdx(adxInd.getADX().bigDecimalValue());

        // 11. CCI
        DtCCIIndicator cciInd = new DtCCIIndicator(series, 8);
        tech.setCci(cciInd.getCci().bigDecimalValue());


        // 12. CYC 股价平均成本线 CYC(N) = Σ(收盘价 × 成交量) / Σ(成交量)  反映真实市场成本 → 更准确判断主力成本
        DtCYCIndicator cyc5Ind = new DtCYCIndicator(series, 5);       // CYC5(超短线成本)，反映一周内参与者的平均成本。
        DtCYCIndicator cyc13Ind = new DtCYCIndicator(series, 13);     // CYC13(短线成本)，判断股价是否回调至关键支撑位。
//        DtCYCIndicator cycInd34 = new DtCYCIndicator(series, 34);     // CYC34(中线成本)，确定中期趋势强度。
        tech.setCyc5(cyc5Ind.getCyc().bigDecimalValue());
        tech.setCyc13(cyc13Ind.getCyc().bigDecimalValue());

        // 13. MFI 短期资金进出，过滤无资金的假突破
        DtMFIIndicator mfiInd = new DtMFIIndicator(series, 8);
        tech.setMfi(mfiInd.getMfi().bigDecimalValue());




        // ======================== 策略辅助 ========================

        Num highest = new HighestValueIndicator(new HighPriceIndicator(series), size / 2).getValue(lastIndex);
        Num lowest = new LowestValueIndicator(new LowPriceIndicator(series), size / 2).getValue(lastIndex);
        Num closePrice = closePriceInd.getValue(lastIndex);

        // ======================== 指标背离 8项 ========================
        DivergenceSignal dvg = judgeDivergence(closePrice, highest, lowest, macdInd, vmacdInd, kdjInd, rsiInd, obvmaInd, wrInd, cciInd, mfiInd);
        tech.setDivergenceType(dvg.getDivergenceType());
        tech.setDivergenceStrength(dvg.getDivergenceStrength());
        tech.setDivergenceResult(dvg.getDivergenceResult());
        log.info("-----计算背离：{} {}_{}_{}", series.getName(), dvg.getDivergenceType(), dvg.getDivergenceStrength(), dvg.getDivergenceResult());

        // ======================== 量价关系 ========================
        VolumeAndPriceSignal volumeAndPriceSignal = calcVolumeAndPrice(series, highest, lowest, ema5, ema10, bias, klineDaily.getVolumeRatio(), obvmaInd, bollInd, dvg);
        tech.setSignalType(volumeAndPriceSignal.getSignalType());
        tech.setSignalLevel(volumeAndPriceSignal.getSignalLevel());
        tech.setSignalResult(volumeAndPriceSignal.getSignalResult());

        // ======================== 多因子共振信号 ========================
        ResonanceSignal resonance = judgeResonance(lastIndex, closePrice, ema5Ind, ema10Ind, macdInd, adxInd, cyc5Ind, cyc13Ind, rsiInd,
                kdjInd, wrInd, cciInd, vmacdInd, obvmaInd, mfiInd, atrInd, highest, bollInd);
        tech.setBuyScore(resonance.getBuyScore());
        tech.setBuyReason(resonance.getBuyReason());
        tech.setSellScore(resonance.getSellScore());
        tech.setSellReason(resonance.getSellReason());

        return tech;
    }


}
