package com.deepthinking.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;


/**
 * 真实波幅 (MTR) 指标
 * TR = max(high - low, |high - prevClose|, |low - prevClose|)
 */
public class DtTRIndicator extends CachedIndicator<Num> {

    private final HighPriceIndicator highIndicator;
    private final LowPriceIndicator lowIndicator;
    private final ClosePriceIndicator closeIndicator;

    public DtTRIndicator(BarSeries series) {
        super(series);
        highIndicator = new HighPriceIndicator(series);
        lowIndicator = new LowPriceIndicator(series);
        closeIndicator = new ClosePriceIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {  // 第一根 K 线无法计算 TR（需要前一日收盘价），返回 0 或 NaN
            return  NaN;
        }
        Num high = highIndicator.getValue(index);
        Num low = lowIndicator.getValue(index);
        Num prevClose = closeIndicator.getValue(index - 1);

        Num hl = high.minus(low);
        Num hc = high.minus(prevClose).abs();
        Num lc = low.minus(prevClose).abs();
        // 返回三者最大值
        return hl.max(hc).max(lc);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}