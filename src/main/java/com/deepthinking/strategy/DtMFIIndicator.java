package com.deepthinking.strategy;

import com.google.common.collect.Lists;
import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import java.util.List;

import static com.deepthinking.strategy.StrategyUtils.*;
import static net.sf.jsqlparser.parser.feature.Feature.values;

/**
 * MFI 资金流量指数（Money Flow Index），是一种类似于RSI的指标，但使用成交量和价格来测量买入和卖出压力。MFI的计算公式如下：
 * 计算逻辑: MFI = 100 - (100 / (1 + (正资金流量和 / 负资金流量和)))
 * *   正资金流量 = 典型价格 × 成交量 (当典型价格 > 前一日典型价格)
 * *   负资金流量 = 典型价格 × 成交量 (当典型价格 < 前一日典型价格)
 */
public class DtMFIIndicator extends CachedIndicator<Num> {

    TypicalPriceIndicator tpIndicator;
    VolumeIndicator volumeIndicator;
    @Getter
    private final Num mfi;
    private Num max;
    private Num min;

    public DtMFIIndicator(BarSeries series, int barCount) {
        super(series);
        int endIndex = series.getEndIndex();
        tpIndicator = new TypicalPriceIndicator(series);
        volumeIndicator = new VolumeIndicator(series);
        mfi = preCalculate(endIndex, barCount);
        max = mfi;
        min = mfi;
        for (int i = endIndex - barCount + 1; i < endIndex; i++) {
            max = max.max(preCalculate(i, barCount));
            min = min.min(preCalculate(i, barCount));
        }
    }

    private Num preCalculate(int index, int barCount) {
        if (index < barCount) {
            return NUM_NAN;
        }
        // 计算周期内正负资金流之和
        Num positiveFlow = NUM_0;
        Num negativeFlow = NUM_0;

        for (int i = index - barCount + 1; i <= index; i++) {
            if (i < 1) continue;                                // 第一根K线无法比较 TP
            Num tpToday = tpIndicator.getValue(i);
            Num tpYesterday = tpIndicator.getValue(i - 1);
            Num moneyFlow = tpToday.multipliedBy(volumeIndicator.getValue(i));

            if (tpToday.isGreaterThan(tpYesterday)) {
                positiveFlow = positiveFlow.plus(moneyFlow);
            } else if (tpToday.isLessThan(tpYesterday)) {
                negativeFlow = negativeFlow.plus(moneyFlow);
            }
            // 相等时两边都不加
        }

        // 处理负资金流为零的情况（极端买入压力）
        Num result;
        if (negativeFlow.isZero()) {
            result = NUM_100;
        } else {
            Num moneyRatio = positiveFlow.dividedBy(negativeFlow);
            // MFI = 100 - 100 / (1 + MR)
            result = NUM_100.minus(NUM_100.dividedBy(NUM_1.plus(moneyRatio)));
        }
        return result;
    }

    @Override
    protected Num calculate(int index) {
        return mfi;
    }

    /**
     * MFI > 80 超买（可能回调，考虑卖出），做空信号权重 -1
     */
    public boolean isOverbought() {
        return mfi.isGreaterThanOrEqual(NUM_80);
    }

    /**
     * MFI < 20 超卖（可能反弹，考虑买入），做多信号权重 +1
     */
    public boolean isOversold() {
        return mfi.isLessThanOrEqual(NUM_20);
    }

    /**
     * MFI 未新高   MFI 与价格背离（需额外实现）→ 更强信号 ±2
     */
    public boolean isHighest() {
        return mfi.isGreaterThanOrEqual(max);
    }

    public boolean isLowest() {
        return mfi.isLessThanOrEqual(min);
    }


    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}