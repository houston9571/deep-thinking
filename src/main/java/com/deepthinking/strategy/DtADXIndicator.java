package com.deepthinking.strategy;

import lombok.Getter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.*;

/**
 * ADX (平均趋向指标) 衡量趋势的强度。ADX值越高，表示趋势越强；ADX值越低，表示市场越震荡。
 * +DI（Positive Directional Indicator）：上升方向指标
 * -DI（Negative Directional Indicator）：下降方向指标
 * ADX（Average Directional Index）：平均方向性指数
 * ADX的计算步骤：
 * 1. 计算真实波幅（TR）
 * 2. 计算+DM和-DM
 * 3. 计算+DI和-DI
 * 4. 计算ADX
 * 计算逻辑：ADX = EMA( |DI+ - DI-| / (DI+ + DI-) , period )
 * *
 * ADX < 20	    趋势弱，震荡市
 * ADX 20-25	趋势开始形成
 * ADX > 25	    趋势强（典型强趋势）
 * ADX > 40	    极强趋势（可能见顶）
 */
public class DtADXIndicator extends CachedIndicator<Num> {

    private final DtEMAIndicator adxInd;
    private final PlusDIIndicator plusDI;
    private final MinusDIIndicator minusDI;
    private final int endIndex;

    public DtADXIndicator(BarSeries series, int period) {
        super(series);
        endIndex = series.getEndIndex();
        DXIndicator dxIndicator = new DXIndicator(series, period);
        adxInd = new DtEMAIndicator(dxIndicator, period);
        plusDI = dxIndicator.getPlusDI();
        minusDI = dxIndicator.getMinusDI();
    }

    @Override
    protected Num calculate(int index) {
        return adxInd.getValue(index);
    }

    /**
     * ADX > 25	    趋势强（典型强趋势）
     */
    public boolean isStrong() {
        return adxInd.getValue(endIndex).isGreaterThanOrEqual(NUM_25);
    }

    /**
     * 高位预警 ADX > 60 进入过热后的衰竭期 分批减仓
     */
    public boolean isTooHot(){
        return adxInd.getValue(endIndex).isGreaterThanOrEqual(NUM_60);
    }

    public Num getADX() {
        return adxInd.getValue(endIndex);
    }

    public Num getPlusDI() {
        return plusDI.getValue(endIndex);
    }

    public Num getMinusDI() {
        return minusDI.getValue(endIndex);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/**
 * DX (方向指标)
 * DX = |+DI - -DI| / (+DI + -DI) * 100
 * 需要 +DI 和 -DI 具有相同的周期
 */
@Getter
class DXIndicator extends CachedIndicator<Num> {

    private final PlusDIIndicator plusDI;
    private final MinusDIIndicator minusDI;

    public DXIndicator(BarSeries series, int period) {
        super(series);
        this.plusDI = new PlusDIIndicator(series, period);
        this.minusDI = new MinusDIIndicator(series, period);
    }

    @Override
    protected Num calculate(int index) {
        Num pdi = plusDI.getValue(index);
        Num mdi = minusDI.getValue(index);
        Num sum = pdi.plus(mdi);

        // 避免除零
        if (sum.isZero()) {
            return NUM_0;
        }
        Num diff = pdi.minus(mdi).abs();
        // DX = (diff / sum) * 100
        return diff.dividedBy(sum).multipliedBy(NUM_100);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}


/**
 * Wilder 平滑辅助类    国际用法，对齐Wilder标准，ta4j使用 MMAIndicator
 * 公式为 Smooth_t = (Smooth_{t-1} * (period-1) + value_t) / period。用于平滑 +DM、-DM 和 TR
 */
//class WilderSmoothingIndicator extends CachedIndicator<Num> {
//    private final Indicator<Num> indicator;
//    private final int period;
//
//    public WilderSmoothingIndicator(Indicator<Num> indicator, int period) {
//        super(indicator.getBarSeries());
//        this.indicator = indicator;
//        this.period = period;
//    }
//
//    @Override
//    protected Num calculate(int index) {
//        if (index == period) {
//            // 第一个平滑值：前 period 个值的算术平均
//            Num sum = NUM_0;
//            for (int i = 1; i <= period; i++) {         // 注意：DM/TR 从索引1开始有效
//                sum = sum.plus(indicator.getValue(i));
//            }
//            return sum.dividedBy(numOf(period));
//        } else {
//            Num prevSmooth = getValue(index - 1);
//            Num currentValue = indicator.getValue(index);
//            // prevSmooth * (period-1) + currentValue) / period
//            return prevSmooth.multipliedBy(numOf(period - 1)).plus(currentValue).dividedBy(numOf(period));
//        }
//    }
//
//    @Override
//    public int getCountOfUnstableBars() {
//        return 0;
//    }
//}

/**
 * +DM (正向方向变动) = 当日最高价 - 前日最高价（若该差值为正且大于当日最低价与前日最低价的差值），否则为 0。
 */
class PlusDMIndicator extends CachedIndicator<Num> {
    private final HighPriceIndicator high;
    private final LowPriceIndicator low;

    public PlusDMIndicator(BarSeries series) {
        super(series);
        this.high = new HighPriceIndicator(series);
        this.low = new LowPriceIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        Num highToday = high.getValue(index);
        Num highPrev = high.getValue(index - 1);
        Num lowToday = low.getValue(index);
        Num lowPrev = low.getValue(index - 1);

        Num upMove = highToday.minus(highPrev);
        Num downMove = lowPrev.minus(lowToday);

        if (upMove.isGreaterThan(downMove) && upMove.isGreaterThan(NUM_0)) {
            return upMove;
        }
        return NUM_0;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/**
 * -DM (负向方向变动) = 前日最低价 - 当日最低价（若该差值为正且大于当日最高价与前日最高价的差值），否则为 0。
 */
class MinusDMIndicator extends CachedIndicator<Num> {
    private final HighPriceIndicator high;
    private final LowPriceIndicator low;

    public MinusDMIndicator(BarSeries series) {
        super(series);
        this.high = new HighPriceIndicator(series);
        this.low = new LowPriceIndicator(series);
    }

    @Override
    protected Num calculate(int index) {
        Num highToday = high.getValue(index);
        Num highPrev = high.getValue(index - 1);
        Num lowToday = low.getValue(index);
        Num lowPrev = low.getValue(index - 1);

        Num upMove = highToday.minus(highPrev);
        Num downMove = lowPrev.minus(lowToday);

        if (downMove.isGreaterThan(upMove) && downMove.isGreaterThan(NUM_0)) {
            return downMove;
        }
        return NUM_0;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/**
 * +DI (正向趋向指标) = (平滑后的 +DM) / (平滑后的 TR) * 100
 */
class PlusDIIndicator extends CachedIndicator<Num> {
    private final DtEMAIndicator smoothedPlusDM;
    private final DtEMAIndicator smoothedTR;

    public PlusDIIndicator(BarSeries series, int period) {
        super(series);
        this.smoothedPlusDM = new DtEMAIndicator(new PlusDMIndicator(series), period);
        this.smoothedTR = new DtEMAIndicator(new DtTRIndicator(series), period);
    }

    @Override
    protected Num calculate(int index) {
        Num plusDM = smoothedPlusDM.getValue(index);
        Num tr = smoothedTR.getValue(index);
        if (tr.isZero()) {
            return NUM_0;
        }
        return plusDM.dividedBy(tr).multipliedBy(NUM_100);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}

/**
 * -DI (负向趋向指标)
 */
class MinusDIIndicator extends CachedIndicator<Num> {
    private final DtEMAIndicator smoothedMinusDM;
    private final DtEMAIndicator smoothedTR;

    public MinusDIIndicator(BarSeries series, int period) {
        super(series);
        this.smoothedMinusDM = new DtEMAIndicator(new MinusDMIndicator(series), period);
        this.smoothedTR = new DtEMAIndicator(new DtTRIndicator(series), period);
    }

    @Override
    protected Num calculate(int index) {
        Num minusDM = smoothedMinusDM.getValue(index);
        Num tr = smoothedTR.getValue(index);
        if (tr.isZero()) {
            return NUM_0;
        }
        return minusDM.dividedBy(tr).multipliedBy(NUM_100);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
