package com.deepthinking.strategy;


import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.*;

/**
 * 【东财官方公式版】WR (威廉指标) 计算器
 * <p>
 * 核心算法：严格遵循东财公式 WR = (N日内最高价 - 当日收盘价) / (N日内最高价 - N日内最低价) * 100
 * 取值范围为 0 到 100。
 * WR 越接近 0，表示越超买；WR 越接近 100，表示越超卖。
 */
public class DtWRIndicator extends CachedIndicator<Num> {

    private final HighPriceIndicator highPrice;
    private final LowPriceIndicator lowPrice;
    private final ClosePriceIndicator closePrice;
    private final int barCount; // WR 周期 N
    private final int endIndex;
    @Getter
    private Num wr;

    public DtWRIndicator(BarSeries series, int n) {
        super(series);
        endIndex = series.getEndIndex();
        this.highPrice = new HighPriceIndicator(series);
        this.lowPrice = new LowPriceIndicator(series);
        this.closePrice = new ClosePriceIndicator(series);
        this.barCount = n;
        preCalculate();
    }

    private void preCalculate() {
        HighestValueIndicator highestHigh = new HighestValueIndicator(highPrice, barCount);
        LowestValueIndicator lowestMin = new LowestValueIndicator(lowPrice, barCount);

        Num highestHighPrice = highestHigh.getValue(endIndex);
        Num lowestLowPrice = lowestMin.getValue(endIndex);

        // 按照东财公式计算 WR
        // WR = (N日内最高价 - 当日收盘价) / (N日内最高价 - N日内最低价) * 100
        wr = ((highestHighPrice.minus(closePrice.getValue(endIndex))).dividedBy(highestHighPrice.minus(lowestLowPrice))).multipliedBy(NUM_100);

    }

    @Override
    protected Num calculate(int index) {
        return wr;
    }


    /**
     * 超买区‌：WR ≤ 20‌（表示价格接近N日内最高点，可能回调）值越低越超买
     */
    public boolean isOverbought() {
        return wr.isLessThanOrEqual(NUM_20);
    }

    /**
     * 超卖区‌：WR ≥ 80‌（表示价格接近N日内最低点，可能反弹）值越高越超卖
     */
    public boolean isOversold() {
        return wr.isGreaterThanOrEqual(NUM_80);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}