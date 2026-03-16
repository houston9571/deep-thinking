package com.deepthinking.strategy;


import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.NUM_0;

/**
 * 自定义 CYC 指标（股价平均成本线） - 带缓存优化
 * 计算逻辑: CYC(N) = Σ(收盘价 × 成交量) / Σ(成交量) (最近N日)
 * 多头持仓条件：CYC5 > CYC13 > CYC34（成本多头排列）
 * 价格在 CYC13 之上：收盘价高于短期成本线，表明短期持仓者盈利，支撑较强
 */
public class DtCYCIndicator extends CachedIndicator<Num> {

    @Getter
    private Num cyc = NaN.NaN;

    public DtCYCIndicator(BarSeries series, int period) {
        super(series);
        int index = series.getEndIndex();
        // 典型价格 (H+L+C)/3
        TypicalPriceIndicator typicalPriceIndicator = new TypicalPriceIndicator(series);
        // 成交量
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);

        Num sumAmount = NUM_0; // 成交金额累加值
        Num sumVolume = NUM_0; // 成交量累加值

        for (int i = index - period + 1; i <= index; i++) {
            Num tp = typicalPriceIndicator.getValue(i);      // 典型价格
            Num vol = volumeIndicator.getValue(i);           // 成交量
            sumAmount = sumAmount.plus(tp.multipliedBy(vol));
            sumVolume = sumVolume.plus(vol);
        }

        // 避免除零错误
        if (!sumVolume.isZero()) {
            cyc = sumAmount.dividedBy(sumVolume);
        }
    }

    @Override
    protected Num calculate(int index) {
        return cyc;
    }


    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}