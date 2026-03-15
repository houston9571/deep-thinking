package com.deepthinking.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static com.deepthinking.strategy.StrategyUtils.NUM_100;

/**
 * 跳空幅度指标（百分比）
 * 正值表示高开，负值表示低开
 * *
 * **** 为什么跳空过大的股票不宜隔夜持仓？ ****
 * 统计规律：方正证券等研究发现，开盘跳空幅度过大的股票（无论高开或低开），在后一月具有负向收益，即“开盘跳一跳，股价向下掉”。
 * 情绪透支：大幅高开往往意味着短期利好过度反应，次日买盘不足，容易回落；大幅低开则可能隐含未消化的利空，恐慌情绪延续。
 * 缺口回补压力：技术分析认为，跳空缺口有“逢缺必补”的倾向，尤其是普通缺口，短期内回补概率较高。
 * 隔夜策略本质：隔夜持仓博取的是次日惯性冲高，若开盘已大幅透支涨幅，则次日溢价空间有限，风险收益比不佳。
 */
public class DtGapPercentIndicator extends CachedIndicator<Num> {

    Num gap;

    public DtGapPercentIndicator(BarSeries series) {
        super(series);
        int endIndex = series.getEndIndex();
        Num prevClose = getBarSeries().getBar(endIndex - 1).getClosePrice();
        Num open = getBarSeries().getBar(endIndex).getOpenPrice();
        // (open - prevClose) / prevClose * 100
        gap = open.minus(prevClose).dividedBy(prevClose).multipliedBy(NUM_100);
    }

    @Override
    protected Num calculate(int index) {
        return gap;
    }

    /**
     * 跳空幅度 = (当日开盘价 - 前一日收盘价) / 前一日收盘价 × 100%
     * < 2%	    正常波动	可接受
     * 2% - 4%	温和跳空	结合其他指标（如量能、板块热度）判断
     * > 4%	    过度跳空	优先剔除，不建议隔夜持仓
     */
    public Num getGap() {
        return gap.abs();
    }

    public boolean isHighGap() {
        return gap.isPositive();
    }

    public boolean isLowGap() {
        return gap.isNegative();
    }


    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}