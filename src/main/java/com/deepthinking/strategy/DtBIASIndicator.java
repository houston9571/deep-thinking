package com.deepthinking.strategy;

import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.NUM_0;
import static com.deepthinking.strategy.StrategyUtils.NUM_100;

/**
 * 【东财官方公式版】BIAS 计算器
 * *
 * 核心算法：严格遵循东财公式 BIAS = (当日收盘价 - N日移动平均价) / N日移动平均价 * 100
 * *
 * 正乖离：价格在均线上方，代表超买，有回落压力。
 * 负乖离：价格在均线下方，代表超卖，有反弹需求。
 * *
 * 为了与东财完全对齐，N日移动平均价必须与东财使用的方法完全一致。
 * 此处采用最通用的标准移动平均 (SMA)。
 */
public class DtBIASIndicator extends CachedIndicator<Num> {

    @Getter
    private Num bias = NUM_0;

    public DtBIASIndicator(BarSeries series, int n) {
        super(series);
        int endIndx = series.getEndIndex();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator smaIndicator = new SMAIndicator(closePrice, n);    // 使用标准SMA
        Num currentClose = closePrice.getValue(endIndx);                // 获取当日收盘价
        Num nDayAverage = smaIndicator.getValue(endIndx);               // 获取 N 日移动平均价
        // 如果移动平均价为0，则BIAS无意义，返回0或null
        if (!nDayAverage.isZero()) {        // 按照东财公式计算 BIAS
            // BIAS = (当日收盘价 - N日移动平均价) / N日移动平均价 * 100
            Num numerator = currentClose.minus(nDayAverage);
            bias = numerator.dividedBy(nDayAverage).multipliedBy(NUM_100);
        }
    }

    @Override
    protected Num calculate(int index) {
        return bias;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}