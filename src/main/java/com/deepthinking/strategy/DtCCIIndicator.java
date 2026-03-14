package com.deepthinking.strategy;

import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.*;

/**
 *  CCI 商品通道指数, 衡量价格偏离其统计平均值的指标，常用于判断超买超卖。
 * 基于典型价格（TP = (High + Low + Close)/3）与平均值的偏离程度。
 * CCI 上穿 +100	强势突破	+1.5
 * CCI 下穿 -100	弱势突破	-1.5
 */
public class DtCCIIndicator extends CachedIndicator<Num> {
    private final TypicalPriceIndicator tp;     // 典型价格 (H+L+C)/3
    private final SMAIndicator smaTP;           // 典型价格的SMA
    @Getter
    private final Num cci;
    private Num max;
    private Num min;

    public DtCCIIndicator(BarSeries series, int barCount) {
        super(series);
        int endIndex = series.getEndIndex();
        tp = new TypicalPriceIndicator(series);
        smaTP = new SMAIndicator(tp, barCount);
        cci = preCalculate(endIndex, barCount);
        max = cci;
        min = cci;
        for (int i = endIndex - barCount + 1; i < endIndex; i++) {
            max = max.max(preCalculate(i, barCount));
            min = min.min(preCalculate(i, barCount));
        }
    }

    private Num preCalculate(int index, int barCount) {
        Num tpValue = tp.getValue(index);
        Num smaValue = smaTP.getValue(index);
        // 计算平均绝对方差 MD = SUM(|TP - SMA|) / period
        Num sum = NUM_0;
        for (int i = index - barCount + 1; i <= index; i++) {
            sum = sum.plus(tp.getValue(i).minus(smaValue).abs());
        }
        Num md = sum.dividedBy(numOf(barCount));
        // CCI = (TP - SMA) / (0.015 * MD)
        return tpValue.minus(smaValue).dividedBy(md.multipliedBy(numOf(0.015)));

    }

    @Override
    protected Num calculate(int index) {
        return cci;
    }

    /**
     * CCI > 100	超买（可能回调）       ✅ 短线卖出信号
     */
    public boolean isOverbought() {
        return cci.isGreaterThan(NUM_100);
    }

    /**
     * CCI > 200    极端超买（强烈回调）    ✅ 严格卖出
     */
    public boolean isSeriousOverbought(){
        return cci.isGreaterThan(NUM_200);
    }

    /**
     * CCI < -100	超卖（可能反弹）      ✅ 短线买入信号
     */
    public boolean isOversold() {
        return cci.isLessThan(NUM_N100);
    }

    /**
     * CCI < -200	极端超卖（强烈反弹）	✅ 严格买入
     */
    public boolean isSeriousOversold(){
        return cci.isLessThan(NUM_N200);

    }

    /**
     * 核心逻辑	价格速度/斜率的衰竭, ±100 之外的背离才有意义
     * CCI 顶背离	价格新高，CCI 未新高	-2.0
     */
    public boolean isHighest() {
        return cci.isGreaterThanOrEqual(max);
    }

    /**
     * 核心逻辑	价格速度/斜率的衰竭, ±100 之外的背离才有意义
     * CCI 底背离	价格新低，CCI 未新低	+2.0
     */
    public boolean isLowest() {
        return cci.isLessThanOrEqual(min);
    }


    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}