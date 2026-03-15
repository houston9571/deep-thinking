package com.deepthinking.strategy;

import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockTechDaily;

import java.math.BigDecimal;

import static com.deepthinking.common.constant.Constants.YI;

public class SelectionStrategy {


    /**
     * 核心结论：隔夜持仓需精选基本面稳健、现金流充沛、行业抗风险的股票。
     * *
     * 资金流向与股价背离：
     * 强信号：股价小涨，但主力净流入巨大（主力在悄悄吸筹）。
     * 陷阱：股价涨停，但主力净流入却在减少（主力在利用涨停板出货，即“板上抛售”）。
     * 尾盘抢筹：收盘前最后 15 分钟有明显的放量拉升，通常预示着次日高开概率大。
     */
    public static boolean passBasicSelection(StockInfo stockInfo, StockKlineDaily daily) {
        return daily.getMainNetRatio().doubleValue() > 0                        // 主力净流入占比10% 以上，说明主力在锁筹，隔夜安全性高。
                && isBetween(daily.getChangePercent(), 2, 9)         // 涨跌幅 5%-9%，且收在最高点附近。
                && isBetween(daily.getTurnover(), 3, 15)             // 换手率 黄金区间 3%-7%（活跃）, 7%-15%（热门）
                && isBetween(daily.getVolumeRatio(), 1, 5)           // 量比   1.5-2.5温和放量, 2.5-5：显著放量
                && isBetween(daily.getFreeMarketCap(), 30 * YI, 1000 * YI);     // 流通市值
    }


    /**
     * 核心结论：
     * ADX > 25：确认趋势已经启动，而不是在震荡。
     * BIAS(6) 不宜过高：如果 BIAS6 已经超过 10%，说明短线严重超买，次日极易冲高回落，不建议隔夜。
     * ATR 异常放大：当日 MTR > 2 * ATR 且价格收阳，代表动能爆发。
     * 尾盘抢筹：收盘前最后 15 分钟有明显的放量拉升，通常预示着次日高开概率大。
     * 空间 Price > MA(20) 趋势向上力度
     * MTR > 1.5*ATR 动能爆发
     * 乖离 BIAS(6) < 7 防止追高在极值点
     * 资金 OBV  创新高资金真实流入形态20日内首次涨停/突破筹码干净
     */
    public static boolean passIndicatorSelection(StockInfo stockInfo, StockKlineDaily daily) {
        return true;
    }

    /**
     * 次日处理逻辑：
     * <p>
     * 高开 > 3%：观察前 15 分钟，不破均线持有，跌破均线止盈。
     * <p>
     * 低开且无反抽：如果 9:45 前不能翻红，按 ATR 止损位 坚决离场。
     */
    public static boolean passIndicatorSelection(StockTechDaily techDaily) {
//        if (ObjectUtil.isEmpty(techDaily)) {
//            return false;
//        }
        return true;
    }

    public static boolean isBetween(long val, long small, long big) {
        return small <= val && val <= big;
    }

    public static boolean isBetween(BigDecimal val, double small, double big) {
        return small <= val.doubleValue() && val.doubleValue() <= big;
    }
}
