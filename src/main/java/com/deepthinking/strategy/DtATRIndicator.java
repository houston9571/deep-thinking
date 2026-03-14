package com.deepthinking.strategy;

import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.numOf;


/**
 * 波动率: 计算超短线止损
 * MTR 真实波幅（点）  ATR 平均真实波幅（线）
 * 当 MTR > 1.5*ATR 或 2*ATR时，往往代表出现了极端波动（Fat Tail Event）
 *
 * 在东财的默认公式中，ATR 是这样定义的：
 * TR := MAX(MAX(HIGH-LOW,ABS(HIGH-REF(CLOSE,1))),ABS(LOW-REF(CLOSE,1)));
 * ATR : MA(TR, N);  国内习惯（东财/通达信）：多用 SMA（简单平均）
 */
public class DtATRIndicator extends CachedIndicator<Num> {
    @Getter
    private final Num mtr;
    @Getter
    private final Num atr;


    public DtATRIndicator(BarSeries series, int barCount) {
        super(series);
        int endIndex = series.getEndIndex();
        DtTRIndicator trInd = new DtTRIndicator(series);
        DtEMAIndicator atrInd = new DtEMAIndicator(trInd, barCount);    // 国内软件默认用SMA
        mtr = trInd.getValue(endIndex);
        atr = atrInd.getValue(endIndex);
    }

    @Override
    protected Num calculate(int index) {
      return atr;
    }

    /**
     * 动力确认，价格处于上升趋势，且MTR>1.5*ATR（波动扩张）时入场，做多信号
     * MTR > 1.5*ATR 趋势加速/异动突破  确认方向后加仓
     */
    public boolean volumeConfirm(){
        return mtr.isGreaterThan(atr.multipliedBy(numOf(1.5)));      // 1.5 过滤掉低波动期的假信号。
    }

    /**
     * MTR > 3 * ATR 情绪极值/变盘预警, 风险极高, 减仓、停止开仓、收紧止损
     */
    public boolean volumeWarn(){
        return mtr.isGreaterThanOrEqual(atr.multipliedBy(numOf(3)));      // 1.5 过滤掉低波动期的假信号。
    }





    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}