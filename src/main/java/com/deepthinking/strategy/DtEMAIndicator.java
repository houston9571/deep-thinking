package com.deepthinking.strategy;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

import static com.deepthinking.strategy.StrategyUtils.*;

/**
 * 【东财专用版】EMA 计算器
 * <p>
 * 核心逻辑对齐东方财富 APP：
 * 1. 初始值：EMA[0] = Close[0] (直接使用第一根 K 线的收盘价)
 * 2. 递推：EMA[i] = Close[i] * (2/(N+1)) + EMA[i-1] * (1 - 2/(N+1))
 * <p>
 * ⚠️ 注意：此算法在前 N 天内与学术定义 (SMA 种子) 不同，但与国内所有行情软件一致。
 */
public class DtEMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private List<Num> emaValues;
    private final int endIndex;

    public DtEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        endIndex = indicator.getBarSeries().getEndIndex();
        this.indicator = indicator;
        preCalculate(barCount);
    }

    private void preCalculate(int barCount) {
        int seriesLength = getBarSeries().getBarCount();
        emaValues = new ArrayList<>(seriesLength);

        if (seriesLength == 0 || seriesLength < barCount) return;

        // 1. 计算平滑系数 alpha = 2 / (N + 1)
        Num alpha = NUM_2.dividedBy(numOf(barCount + 1));
        Num oneMinusAlpha = NUM_1.minus(alpha);

        // 2. 初始化：EMA[0] = Close[0] (东财核心逻辑)
        Num currentEma = indicator.getValue(0);
        emaValues.add(currentEma);

        // 3. 递推计算
        for (int i = 1; i < seriesLength; i++) {
            Num close = indicator.getValue(i);
            // EMA = Close * alpha + PrevEMA * (1 - alpha)
            currentEma = close.multipliedBy(alpha).plus(currentEma.multipliedBy(oneMinusAlpha));
            emaValues.add(currentEma);
        }
    }

    public Num getEMA(){
        return emaValues.getLast();
    }

    @Override
    protected Num calculate(int index) {
        return emaValues.get(index);
    }

    // 价格向上走
    public boolean goUp() {
        return getEMA().isGreaterThan(emaValues.get(endIndex-1));
    }

    // 价格向下走
    public boolean goDown() {
        return getEMA().isLessThan(emaValues.get(endIndex-1));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}