package com.deepthinking.strategy;

import com.deepthinking.common.utils.StringUtil;
import com.deepthinking.strategy.signal.DivergenceSignal;
import com.deepthinking.strategy.signal.ResonanceSignal;
import com.deepthinking.strategy.signal.VolumeAndPriceSignal;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.common.constant.Constants.HASH;
import static com.deepthinking.strategy.StrategyUtils.*;

/**
 * 超短线实战铁律
 * 持股 1～3 天，超时必走
 * 止损 3%，不扛单
 * 止盈 5%～8%
 * 只做 放量票 量比>2.5
 * 只在 9:30-10:00 / 14:00-14:30 开仓
 * 共振评分 ≥75 才买入
 */
@Slf4j
public class OverNightStrategy {

    /*
     * ========================== 指标背离（Divergence） ======================
     * 背离（Divergence）是指价格走势与动量指标（如 MACD、RSI、KDJ、OBV）的趋势方向相反，暗示当前动能正在放缓。
     * 在分钟级别（尤其是 1min, 5min）短线交易中，指标背离是识别“虚假拉升”或“衰竭杀跌”的核心工具。
     * 最好的背离是：价格离 EMA 已经很远（乖离率大），同时指标出现背离。
     * ====== “三背离共振”是日线级别最高级别的卖出（或买入）信号: MFI或RSI底背离 + OBV底背离 + VMACD柱线缩短 + CCI
     *
     * 背离不是即时信号：背离出现后，价格可能继续背离多次（尤其在强趋势中），必须等待确认。
     * 顶背离卖出规则: 出现顶背离后，不立即卖出，等待价格跌破某一关键支撑（如前期回调低点、EMA10）或指标死叉确认。若同时伴有K线形态（如长上影、吞没形态），可果断减仓。
     * 底背离买入规则: 出现底背离后，等待价格突破近期下跌趋势线或站上某关键均线（如EMA5），配合成交量放大确认。结合指标（如RSI底背离、KDJ金叉）共振入场。
     *
     * ======================================================================
     */
    public static DivergenceSignal judgeDivergence(Num closePrice, Num highest, Num lowest, DtMACDIndicator macdInd, DtVMACDIndicator vmacdInd,
                                                   DtKDJIndicator kdjInd, DtRSIIndicator rsiInd, DtOBVMAIndicator obvmaInd, DtWRIndicator wrInd, DtCCIIndicator cciInd, DtMFIIndicator mfiInd) {
        DivergenceSignal divergenceSignal = DivergenceSignal.builder().divergenceType(DivergenceType.NONE).build();

        short divergenceStrength = 0;
        List<String> result = Lists.newArrayList();
        // 底背离：价格创出新低（或低点持平），而指标未能同步创出新低，反而出现更高的低点 → 预示下跌动能衰竭，可能见底回升。  -- 买入信号
        if (closePrice.isEqual(lowest)) {
            if (!macdInd.isLowest() || (macdInd.isGreen() && macdInd.isShirk())) {          // MACD底背离或绿柱缩小
                divergenceStrength++;
                result.add("MACD");
            }
            if (vmacdInd.isLowest() || (vmacdInd.isGreen() && vmacdInd.isShirk())) {        // VMACD底背离或绿柱缩小
                divergenceStrength++;
                result.add("VMACD");
            }
            if (!kdjInd.isLowest() || kdjInd.isOversold()) {                                // KDJ底背离或超卖
                divergenceStrength++;
                result.add("KDJ");
            }
            if (!rsiInd.isLowest() || rsiInd.isOversold()) {                                // RSI底背离或超卖
                divergenceStrength++;
                result.add("RSI");
            }
            if (rsiInd.isSeriousOversold()) {                                               // RSI严重超卖
                divergenceStrength++;
                result.add("RSI");
            }
            if (!obvmaInd.isLowest() || obvmaInd.isObvUTurnUp()) {                          // 量价底背离 OBV能量潮掉头向上
                divergenceStrength++;
                result.add("OBV");
            }
            if (wrInd.isOversold()) {                                                       // 超卖区‌：WR ≥ 80‌（表示价格接近N日内最低点，可能反弹）值越高越超卖
                divergenceStrength++;
                result.add("WR");
            }
            if (cciInd != null && !cciInd.isLowest() && cciInd.isOversold()) {              // CCI 顶背离  判定规则：价格新高 + 指标走低 + 指标处于超买区
                divergenceStrength++;
                result.add("CCI");
            }
            if (mfiInd != null && (!mfiInd.isLowest() || mfiInd.isOversold())) {            // MFI底背离或超卖
                divergenceStrength++;
                result.add("MFI");      // MFI 加入了成交量权重，比 RSI 更难被操纵。MFI 的顶背离（捕捉庄家出货）和 CCI 的底背离（捕捉急跌后的超跌反弹）
            }

            // 底背离时成交量放大，则反转概率更高
            if (divergenceStrength > 0) {
                divergenceSignal.setDivergenceStrength(divergenceStrength);
                divergenceSignal.setDivergenceType(DivergenceType.BOTTOM);
                divergenceSignal.setDivergenceResult(StringUtil.joinWithIndex(COMMA, result));
            }
        }
        // 顶背离：价格创出新高（或高点持平），而指标未能同步创出新高，反而出现更低的高点 → 预示上涨动能减弱，可能见顶回落。 -- 卖出信号
        if (closePrice.isEqual(highest)) {
            if (!macdInd.isHighest() || (macdInd.isRed() && macdInd.isShirk())) {           // MACD顶背离或红柱缩小
                divergenceStrength++;
                result.add("MACD");
            }
            if (!vmacdInd.isHighest() || (vmacdInd.isRed() && vmacdInd.isShirk())) {        // VMACD顶背离或红柱缩小
                divergenceStrength++;
                result.add("VMACD");
            }
            if (!kdjInd.isHighest() || kdjInd.isOverbought()) {                             // KDJ顶背离或超买
                divergenceStrength++;
                result.add("KDJ");
            }
            if (!rsiInd.isHighest() || rsiInd.isOverbought()) {                             // RSI顶背离或超买
                divergenceStrength++;
                result.add("RSI");
            }
            if (rsiInd.isSeriousOverbought()) {                                               // RSI严重超买
                divergenceStrength++;
                result.add("RSI");
            }
            if (!obvmaInd.isHighest() || obvmaInd.isObvUTurnDown()) {                       // 量价顶背离 OBV能量潮掉头向下
                divergenceStrength++;
                result.add("OBV");
            }
            if (wrInd.isOverbought()) {                                                     // 超买区‌：WR ≤ 20‌（表示价格接近N日内最高点，可能回调）值越低越超买
                divergenceStrength++;
                result.add("WR");
            }
            if (cciInd != null && !cciInd.isHighest() && cciInd.isOverbought()) {           // CCI 顶背离  判定规则：价格新高 + 指标走低 + 指标处于超买区
                divergenceStrength++;
                result.add("CCI");
            }
            if (mfiInd != null && (!mfiInd.isHighest() || mfiInd.isOverbought())) {         // MFI 未新高或超买
                divergenceStrength++;
                result.add("MFI");      // MFI 加入了成交量权重，比 RSI 更难被操纵。MFI 的顶背离（捕捉庄家出货）和 CCI 的底背离（捕捉急跌后的超跌反弹）
            }
            if (divergenceStrength > 0) {
                divergenceSignal.setDivergenceStrength(divergenceStrength);
                divergenceSignal.setDivergenceType(DivergenceType.TOP);
                divergenceSignal.setDivergenceResult(StringUtil.joinWithIndex(COMMA, result));
            }
        }
        return divergenceSignal;
    }


    /**
     * 计算量价关系
     */
    public static VolumeAndPriceSignal calcVolumeAndPrice(BarSeries series, Num highest, Num lowest, Num ema5N, Num ema10N, Num bias, BigDecimal volRatio,
                                                          DtOBVMAIndicator obvmaInd, DtBOLLIndicator bollInd, DivergenceSignal divergenceSignal) {
        int lastIndex = series.getEndIndex();
        Bar currBar = series.getLastBar();
        Bar prevBar = series.getBar(lastIndex - 1);
        double prevClose = prevBar.getClosePrice().doubleValue();
        double prevHigh = prevBar.getHighPrice().doubleValue();

        double currClose = currBar.getClosePrice().doubleValue();
        double currOpen = currBar.getOpenPrice().doubleValue();
        double currHigh = currBar.getHighPrice().doubleValue();
        double currLow = currBar.getLowPrice().doubleValue();

        double ema10 = ema10N.doubleValue();

        // 计算量比 (当前量 / 5日均量)
        double volumeRatio = volRatio.doubleValue();
        boolean isVolUp = volumeRatio > 1.2;        // 量比>1.2视为放量
        boolean isVolDown = volumeRatio < 0.8;      // 量比<0.8视为缩量
        boolean isPriceUp = currClose > prevClose;  // 价格涨
        double body = currClose - currOpen;
        double priceChangePercent = body / currOpen * 100;  // 计算涨跌幅 (实体)
        String result = "", tag = String.format("(涨幅%.2f%% 量比%.2f),", priceChangePercent, volumeRatio);

        SignalType signalTpye = SignalType.WATCH;
        SignalLevel signalLevel = SignalLevel.NONE;
        String signalResult = "量能持平";
        int score = 1;
        List<String> reasons = Lists.newArrayList();
        if (isVolUp) {
            boolean isPositiveCandle = currClose > currOpen;    // 收盘价 > 开盘价（阳线）
            boolean isVolumeSurge = volumeRatio > 1.5;          // 放量
            // 计算上影线比例 (判断是否滞涨)
            double upperShadow = currHigh - currClose;
            double shadowRatio = (body == 0) ? 100 : (upperShadow / body);
            boolean hasLongUpperShadow = shadowRatio > 0.5; // 上影线超过实体一半
            if (isPriceUp) {      //  ✅量增价升 看多/持股
                if (!isPositiveCandle) {            // 基础条件：必须是阳线且放量
                    result = "非阳线，不满足量增价升";
                } else if (!isVolumeSurge) {        // 成交量未显著放大
                    signalLevel = SignalLevel.WEAK;
                    result = "阳线但成交量未显著放大";
                } else {                             // 阳线且放量
                    // 进阶过滤 1: 排除“巨量滞涨” (最危险的陷阱)
                    if (volumeRatio >= 3.0 && priceChangePercent < 0.5) {
                        signalLevel = SignalLevel.LOW;
                        reasons.add("❌巨量滞涨：量比>3但涨幅小(主力可能在对倒出货！)");
                    } else if (volumeRatio >= 3.0 && hasLongUpperShadow) {
                        signalLevel = SignalLevel.LOW;
                        reasons.add("❌巨量滞涨：量比>3但上影线长(主力可能在对倒出货！)");
                    } else {
                        // --- 逻辑判断 ---
                        // 进阶过滤 2: 实体力度
                        if (priceChangePercent > 0.5) { // 1分钟线阈值，5分钟线可设为1.0
                            score += 2;
                            reasons.add("K线实体饱满(买盘强劲)");
                        } else {
                            reasons.add("⚠️K线实体较小(动能一般)");
                        }
                        // 进阶过滤 3: 位置判断 (突破 vs 高位)
                        if (currClose > bollInd.getUpper().doubleValue() || currClose > prevHigh) {
                            score += 2;
                            reasons.add("关键位置突破(突破BOLL上轨或前高)");
                        }
                        if (bias.doubleValue() > 3) {      // 乖离率>3%
                            score -= 1;
                            reasons.add("⚠️乖离率过大3%，谨防冲高回落");
                        }
                        if (obvmaInd.isHighest()) {
                            score += 1;
                            reasons.add("OBV同步创新高");
                        }

                        // 最终决策
                        if (score >= 4) {
                            signalLevel = SignalLevel.HIGHEST;
                            signalTpye = SignalType.BUY;
                            result = "🚀有效量增价升，主力真金白银进攻，可跟随！";
                        } else if (score == 3) {
                            signalLevel = SignalLevel.HIGH;
                            signalTpye = SignalType.BUY;
                            result = "✅有效量增价升，可轻仓试错，设好止损。";
                        } else if (score == 2) {
                            signalLevel = SignalLevel.MEDIUM;
                            result = "⚪普通量增价升，可轻仓试错，设好止损。";
                        } else {
                            signalLevel = SignalLevel.LOW;
                            result = "信号不够强，建议观望。";
                        }
                    }
                }
                signalResult = "✅量增价升" + tag + HASH + result + HASH + StringUtil.joinWithIndex(HASH, reasons);
            } else {             // ❌量增价跌  卖出/底部分批吸筹
                // --- 逻辑判断 ---
                // 基础条件：必须是阴线且放量
                if (!isPositiveCandle && !isVolumeSurge) {
                    signalLevel = SignalLevel.WEAK;
                    result = "阴线但成交量未显著放大，可能是洗盘";
                } else {
                    // 进阶过滤 1: 破位加分 (最危险)
                    if (currClose < ema10) {
                        score += 2;
                        reasons.add("跌破EMA10，趋势转空");
                    }
                    if (currClose < bollInd.getMid().doubleValue()) {
                        score += 1;
                        reasons.add("️跌破BOLL中轨，弱势确认");
                    }
                    boolean isNewLow = currLow <= lowest.doubleValue();
                    if (isNewLow) {
                        score += 1;
                        reasons.add("创近期新低，恐慌盘涌出");
                    }
                    // 进阶过滤 2: 巨量加分 (极度危险)
                    boolean isHugeVolume = volumeRatio > 2.5;
                    if (isHugeVolume) {
                        score += 2;
                        reasons.add("巨量砸盘 (量比>2.5)，主力不计成本出逃");
                    }
                    // 进阶过滤 3: 位置判断 (高位 vs 低位)
                    // 如果是在高位 (例如距离高点<10%) 出现放量跌，分数再加2
                    if (highest.doubleValue() * 0.9 <= currClose && isHugeVolume) {
                        score += 2;
                        reasons.add("高位突发巨量长阴，典型的主力出货见顶信号");
                    }
                    // 最终决策
                    if (score >= 6) {
                        signalLevel = SignalLevel.HIGHEST;
                        signalTpye = SignalType.SELL;
                        result = "🔴 极度危险：放量破位大跌，立即清仓，严禁抄底";
                    } else if (score >= 4) {
                        signalLevel = SignalLevel.HIGH;
                        signalTpye = SignalType.SELL;
                        result = "⚠️ 警告：放量下跌，趋势转弱，建议减仓或离场";
                    } else if (score >= 2) {
                        signalLevel = SignalLevel.MEDIUM;
                        result = "📉 放量杀跌：切勿急于接飞刀，等待企稳信号";
                    } else {
                        signalLevel = SignalLevel.LOW;
                        result = "信号不够强，建议观望。";
                    }
                }
                signalResult = "❌量增价跌" + tag + HASH + result + HASH + StringUtil.joinWithIndex(HASH, reasons);
            }

        } else if (isVolDown) {
            if (isPriceUp) {      // ⚠️ 量缩价升 减仓/警戒 可能是主力高度控盘、锁仓拉升的“黄金信号”，也可能是买盘枯竭、诱多出货的“死亡陷阱”。
                boolean hasTopDivergence = divergenceSignal.getDivergenceType() == DivergenceType.TOP;
                boolean isNewHigh = currHigh >= highest.doubleValue();
                boolean isHighPosition = bias.doubleValue() >= 8;  // 高位(乖离率>8%)
                result = "观察，等待放量确认";
                if (isHighPosition && isNewHigh) {                  // 场景 A: 高位 + 创新高 + 背离/极度缩量 -> 危险诱多
                    signalLevel = SignalLevel.HIGH;
                    signalTpye = SignalType.SELL;
                    result = "⚠️ 警告：高位 + 创新高";
                    if (hasTopDivergence || volumeRatio < 0.5) {
                        signalLevel = SignalLevel.HIGHEST;
                        result = "🔴 极度危险：高位量缩创新高+(背离/极度缩量)=主力诱多陷阱(立即止盈/清仓)";
                    }
                } else if (ema5N.isLessThanOrEqual(ema10N)) {       // 场景 B: 下跌趋势中的缩量涨 -> 弱势反弹
                    signalLevel = SignalLevel.HIGH;
                    signalTpye = SignalType.SELL;
                    result = "⚠️ 警告：下跌趋势中的无量反弹，买盘不足，观望切勿抄底，反弹随时结束";
                    if (hasTopDivergence) {                          // 检测顶背离 (价格新高，MACD未新高)
                        signalLevel = SignalLevel.HIGHEST;
                        result = "🔴 极度危险：检测到顶背离, 价格新高，但MACD动能减弱";
                    }
                } else if (!isHighPosition) {  // 场景 C: 主升浪中段 + 缩量涨 -> 良性锁仓
                    // 即使创新高，只要乖离率不大且无明显背离，视为锁仓
                    signalLevel = SignalLevel.HIGH;
                    signalTpye = SignalType.BUY;
                    if (isNewHigh) {
                        result = "🚀 主升浪创新高，缩量表明抛压轻，主力锁仓良好";
                    } else {
                        result = "✅ 上升趋势中缩量整理后上涨，健康信号";
                    }
                    if (hasTopDivergence) {                         // 检测顶背离 (价格新高，MACD未新高)
                        signalLevel = SignalLevel.MEDIUM;
                        signalTpye = SignalType.WATCH;
                        result = "⚪ 观察等待：检测到顶背离：价格新高，但MACD动能减弱";
                    }
                }
                signalResult = "⚠️量缩价升" + tag + HASH + result;
            } else {              // ⚪ 量缩价跌 持币/观望 可能是主力洗盘、散户惜售的“黄金坑”（买入机会），也可能是无人问津、阴跌不止的“无底洞”（死亡陷阱）
                //上升趋势回调中的量缩价跌 = 利好（洗盘，抛压轻，主力未出逃）。
                //下跌趋势/高位破位后的量缩价跌 = 利空（买盘枯竭，阴跌，深不见底）。
                result = "观察，等待明确信号";
                // 支撑位：EMA20 或 BOLL下轨
                boolean atSupportBoll = currClose <= bollInd.getLower().doubleValue() * 1.01;
                // 场景 A: 上升趋势 + 回踩支撑 + 缩量 -> 良性洗盘 (GOOD_WASHOUT)
                if (currClose > ema10 && atSupportBoll) {
                    signalLevel = SignalLevel.MEDIUM;
                    signalTpye = SignalType.WATCH;
                    result = "⚪ 观察等待：上升趋势+回踩支撑+缩量->良性洗盘";
                    reasons.add("上升趋势回踩支撑(EMA10/BOLL下轨)");
                    reasons.add("成交量极度萎缩，表明主力未出逃，散户惜售");          // 关注：若次日放量阳线反包，可大胆买入
                    // 判断K线形态 (是否止跌)
                    double bodySize = (prevClose - currClose) / prevClose * 100;
                    boolean isSmallCandle = bodySize < 0.5; // 小阴线
                    double lowerShadow = currLow - currClose;
                    boolean hasLowerShadow = lowerShadow > (Math.abs(bodySize) * 0.5); // 有下影线
                    if (hasLowerShadow || isSmallCandle) {
                        signalLevel = SignalLevel.HIGH;
                        reasons.add("✨ K线出现止跌迹象(下影线/小阴线)，变盘在即");
                    }
                } else if (currClose < ema10) {                                 // 场景 B: 下跌趋势 + 无量阴跌 -> 恶性杀跌
                    signalLevel = SignalLevel.HIGHEST;
                    signalTpye = SignalType.SELL;
                    result = "🔴 极度危险：下跌趋势+无量阴跌->恶性杀跌";
                    reasons.add("处于下跌趋势中(价格<EMA10)");                 // 严禁抄底：买盘枯竭，阴跌不止，深不见底
                    reasons.add("缩量下跌无承接，少量卖单即可打压股价");
                    reasons.add("⚠'钝刀割肉'最伤人，必须等放量止跌信号");
                } else if (prevClose > ema10 && currClose < ema10) {           // 场景 C: 高位破位后的缩量跌 -> 下跌中继
                    signalLevel = SignalLevel.HIGH;
                    signalTpye = SignalType.SELL;
                    result = "⚠️ 警告：高位破位后的缩量跌->下跌中继";
                    reasons.add("⚠刚刚跌破关键支撑(EMA10)");                   // 观望：破位初期缩量，可能是下跌中继，勿急于接飞刀
                    reasons.add("缩量表明反弹无力，可能继续探底");
                }
                signalResult = "⚪ 量缩价跌" + tag + HASH + result + HASH + StringUtil.joinWithIndex(HASH, reasons);
            }

        }
        return VolumeAndPriceSignal.builder().signalType(signalTpye).signalLevel(signalLevel).signalResult(signalResult).build();
    }


    /**
     * 多因子共振信号 -> 日线指标 -- 定趋势，加股票池
     * 等待确认：背离信号出现后，不立即入场，等待价格突破背离前的趋势线、指标金叉/死叉或关键价位。
     * *
     * 趋势       EMA, MACD, ADX, CYC       确认当前是多头还是空头市场
     * 动能       RSI, KDJ, WR, CCI, MFI    寻找超买超卖后的反转或爆发点
     * 量能       VMACD, OBVMA              确认价格波动的资金含金量
     * 波动/支撑   BOLL, ATR                 确定止损空间与边界突破
     */
    public static ResonanceSignal judgeResonanceDaily(int lastIndex, Num closePrice, DtEMAIndicator ema5Ind, DtEMAIndicator ema10Ind,
                                                      DtMACDIndicator macdInd, DtADXIndicator adxInd, DtCYCIndicator cyc5Ind, DtCYCIndicator cyc13Ind, DtRSIIndicator rsiInd,
                                                      DtKDJIndicator kdjInd, DtWRIndicator wrInd, DtCCIIndicator cciInd, DtVMACDIndicator vmacdInd,
                                                      DtOBVMAIndicator obvmaInd, DtMFIIndicator mfiInd, DtBOLLIndicator bollInd, DtATRIndicator atrInd, Num highest) {

        Num ema5N = ema5Ind.getValue(lastIndex);
        Num ema10N = ema10Ind.getValue(lastIndex);
        Num cyc5 = cyc5Ind.getCyc();
        Num cyc13 = cyc13Ind.getCyc();

        DtMACDIndicator.CrossStatus macdStatus = macdInd.getCrossStatus();
        DtKDJIndicator.CrossStatus kdjStatus = kdjInd.getCrossStatus();
        DtVMACDIndicator.CrossStatus vmacdStatus = vmacdInd.getCrossStatus();
        DtOBVMAIndicator.CrossStatus obvStatus = obvmaInd.getCrossStatus();

        double buyScore = 0, sellScore = 0;
        List<String> buyReasons = new ArrayList<>();
        List<String> sellReasons = new ArrayList<>();

        // =====================  买入信号和评分 =====================
        // ===================== 趋势(EMA + MACD + ADX + CYC = 50分): 确认当前是多头还是空头市场  =====================
        // 1. EMA 多头排列
        if (closePrice.isGreaterThan(ema5N) && ema5N.isGreaterThan(ema10N)) {                   // 价格站上 EMA5/EMA10 → 隔夜安全；
            buyScore += 10;
            buyReasons.add("EMA多头排列,短期强势(价格>EMA5>EMA10)");
        } else if (ema5N.isGreaterThan(ema10N) && ema5Ind.getValue(lastIndex - 1).isLessThanOrEqual(ema10Ind.getValue(lastIndex - 1))) {      // 金叉
            buyScore += 5;
            buyReasons.add("EMA金叉");   // 金叉: 当前 MA5 > MA10; 前一刻 MA5 <= MA10
        }
        // 2. MACD 零轴上金叉
        if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {                       // 金叉且红柱放大
            buyScore += 20;
            buyReasons.add("MACD零轴上金叉且红柱放大(动能强)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("MACD零轴上金叉");
        }
        // 3. ADX (趋势滤网)：它是一个开关。如果 ADX < 20，无论 MACD 如何金叉，都视为“震荡行情”，共振信号无效。
        if (adxInd.isStrong() && adxInd.getPlusDI().isGreaterThan(adxInd.getMinusDI())) {       //	ADX > 25（趋势强度足够），且 +DI > -DI
            buyScore += 10;
            buyReasons.add("ADX趋势强(>25)且+DI>-DI");
        } else if (!adxInd.isStrong()) {
            buyScore -= 10;
            buyReasons.add("ADX=" + adxInd.getADX() + "，趋势弱，震荡行情，共振信号无效");
        }
        // 4. CYC 多头持仓条件：CYC5 > CYC13 收盘价高于短期成本线，表明短期持仓者盈利，支撑较强。配合：ADX > 25 确认趋势，ATR 波动平稳
        if (closePrice.isGreaterThan(cyc5) && cyc5.isGreaterThan(cyc13)) {
            buyScore += 10;
            buyReasons.add("CYC多头排列,支撑较强(价格>CYC5>CYC13)");
        }

        // ===================== 动能(RSI + KDJ + WR + CCI + MFI = 80分) -- 灵敏择时, 寻找超买超卖后的反转或爆发点 =====================
        // 1. RSI 在50~70之间最强
        if (rsiInd.isSeriousOversold()) {     // 向上反转信号
            buyScore += 20;
            buyReasons.add("RSI严重超卖，短线反弹(≤20)");
        } else if (rsiInd.isOversold()) {     // 向上反转信号
            buyScore += 10;
            buyReasons.add("RSI超卖(<30)");
        }
        // 2. KDJ 对短线拐点极其灵敏
        if (kdjStatus == DtKDJIndicator.CrossStatus.GOLDEN_CROSS) {   // 金叉
            if (kdjInd.isLowK() || kdjInd.isLowD()) {
                buyScore += 10;
                buyReasons.add("(KDJ低位金叉，强烈买入信号(K<20)");       // 低位金叉（K<20）：代表价格超跌后的动能反转，此时买入信号最为准确。
            } else {
                buyScore += 5;
                buyReasons.add("(KDJ金叉");
            }
        }
        if (kdjInd.isSeriousOversold()) {                             // J在50~80之间最稳； KDJ超卖区（机会显现） 精准买卖点（J值比K/D更准）
            buyScore += 10;
            buyReasons.add("(KDJ严重超卖，买入信号(J<10)");
        } else if (kdjInd.isOverbought()) {
            buyScore += 5;
            buyReasons.add("(KDJ超卖，买入信号(J<20)");
        }
        // 3. WR ≥ 80 超卖区回升并突破-50, 辅助确认超卖（避免RSI假信号） WR > 80 超卖 → 可低吸隔夜; WR从超卖区回升时配合OBV放量可加仓。
        if (wrInd.isOversold()) {
            buyScore += 10;
            buyReasons.add("WR超卖区，买入机会(≥80)");                  // 等待信号确认,股价重新站上日线均价线时，才是安全的低吸时点。
        }
        // 4. CCI  10分
        if (cciInd.isSeriousOversold()) {
            buyScore += 20;
            buyReasons.add("CCI极端超卖，严格买入(<-200)");              // 极端超卖（强烈反弹）严格买入
        } else if (cciInd.isOversold()) {
            buyScore += 10;
            buyReasons.add("CCI超卖，买入信号(<-100)");                 // 超卖（可能反弹）短线买入信号
        }
        // 5. MFI
        if (mfiInd.isOversold()) {                                    // 超卖（可能反弹，考虑买入），做多信号权重 +1
            buyScore += 10;
            buyReasons.add("MFI超卖，可能反弹(买入信号)");
        }

        // ===================== 量能(VMACD + OBVMA = 30分) -- 量能确认, 确认价格波动的资金含金量 =====================
        // 1. VMACD 量能验证真伪关键   -- 隔夜条件：VMACD 红柱 → 量价配合  15分
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {   // 金叉且红柱放大
            buyScore += 20;
            buyReasons.add("VMACD零轴上金叉且红柱放大(放量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("VMACD零轴上金叉(放量)");
        }
        // 2. OBVMA 能量潮均线 -- 隔夜条件：OBV > OBV_MA5  10分
        if (obvStatus == DtOBVMAIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("OBV金叉，资金流入(买入信号)");
        }

        // ===================== 波动/支撑(BOLL + ATR = 40分) -- 波动爆发, 确定止损空间与边界突破 =====================
        // 1. BOLL 突破下轨支撑 15分
        if (bollInd.isBreakoutDown()) {
            buyScore += 15;
            buyReasons.add("BOLL突破下轨支撑(买入信号)");
        }
        if (bollInd.isBullishBreakout()) {                              // BOLL "开口上涨"：中轨向上倾斜 + 布林带开口  -- 隔夜条件：价格在中轨之上，可持仓过夜，
            buyScore += 15;
            buyReasons.add("BOLL开口且中轨向上倾斜(买入信号)");
        }
        // 2. ATR 超短线止损, 动力确认: MTR>1.5*ATR（波动扩张）趋势加速/异动突破  确认方向后加仓
        if (atrInd.volumeConfirm() && closePrice.isGreaterThan(ema5N)) { // 价格上升趋势，且MTR>1.5*ATR（波动扩张）时入场，做多信号
            buyScore += 10;
            buyReasons.add("ATR波动扩张，价格上升且MTR>1.5*ATR");
        }


        // =====================  卖出信号和评分 =====================
        // ===================== 趋势(EMA + MACD + ADX + CYC = 50分): 确认当前是多头还是空头市场  =====================
        // 1. EMA空头排列
        if (closePrice.isLessThan(ema5N) && ema5N.isLessThan(ema10N)) {             //  -- 隔夜条件：跌破 EMA10 → 不隔夜。
            sellScore += 10;
            sellReasons.add("EMA空头排列,短期弱势(价格<EMA5<EMA10)");
        } else if (ema5N.isLessThan(ema10N) && ema5Ind.getValue(lastIndex - 1).isGreaterThan(ema10Ind.getValue(lastIndex - 1))) {      // 死叉
            sellScore += 5;
            sellReasons.add("EMA死叉");
        }
        // 2. MACD 零轴下死叉
        if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {          // 死叉且绿柱放大
            sellScore += 20;
            sellReasons.add("MACD零轴下死叉且绿柱放大(动能弱)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("MACD零轴下死叉");
        }
        // 3. ADX (趋势滤网)
        if (!adxInd.isStrong() || adxInd.getPlusDI().isLessThan(adxInd.getMinusDI())) {     //ADX < 20 趋势弱，震荡市， +DI < -DI
            sellScore += 10;
            sellReasons.add("ADX趋势弱(<20)或+DI<-DI");
        } else if (adxInd.isTooHot()) {
            sellScore += 10;
            sellReasons.add("ADX高位预警(>60)，进入过热");
        }
        // 4. CYC 空头排列 配合：ADX 确认趋势，ATR 波动平稳
        if (closePrice.isLessThan(cyc5) && cyc5.isLessThan(cyc13)) {
            sellScore += 10;
            sellReasons.add("CYC空头排列(价格>CYC5>CYC13)");
        }

        // ===================== 动能(RSI + KDJ + WR + CCI + MFI = 75分) -- 灵敏择时, 寻找超买超卖后的反转或爆发点 =====================
        // 1. RSI -- 隔夜条件：RSI6 在 50~70 之间最强；>80 不隔夜。
        if (rsiInd.isSeriousOverbought()) {        // 向下反转信号
            sellScore += 15;
            sellReasons.add("RSI严重超买，不隔夜(≥80)");
        } else if (rsiInd.isOverbought()) {         // 向下反转信号
            sellScore += 10;
            sellReasons.add("RSI超买(>70)");
        }
        // 2. KDJ高位死叉
        if (kdjStatus == DtKDJIndicator.CrossStatus.DEATH_CROSS) {    // 死叉
            if (kdjInd.isHighK() || kdjInd.isHighD()) {
                sellScore += 10;
                sellReasons.add("(KDJ高位死叉，强烈卖出信号(K>80)");
            } else {
                sellScore += 5;
                sellReasons.add("(KDJ死叉");
            }
        }
        if (kdjInd.isSeriousOverbought()) {                            //  -- 隔夜条件：J 在 50~80 之间最稳；J>90 不隔夜。   KDJ超买区（风险积聚）
            sellScore += 10;
            sellReasons.add("(KDJ严重超买，卖出信号(J>90)");
        } else if (kdjInd.isOverbought()) {
            sellScore += 5;
            sellReasons.add("(KDJ超买，卖出信号(J>80)");
        }
        // 3. WR ≤ 20 超买区时，系统发出首个减仓信号。 -- 隔夜条件：WR < 20 超买 → 不隔夜;
        if (wrInd.isOverbought()) {
            sellScore += 10;
            sellReasons.add("WR超买区，卖出信号(≤20)");
        }
        // 4. CCI 常用于判断超买超卖
        if (cciInd.isSeriousOverbought()) {                             // 极端超买（强烈回调） 严格卖出
            sellScore += 20;
            sellReasons.add("CCI极端超买，严格卖出(>200)");
        } else if (cciInd.isOverbought()) {                             // 超买（可能回调） 短线卖出信号
            sellScore += 10;
            sellReasons.add("CCI超买，卖出信号(>100)");
        }
        // 5. MFI 短期资金进出验证
        if (mfiInd.isOversold()) {                                      // MFI < 20 超卖（可能反弹，考虑买入），做多信号权重 +1
            sellScore += 10;
            sellReasons.add("MFI超卖，可能反弹(买入信号)");
        }

        // ===================== 量能(VMACD + OBVMA = 25分) -- 量能确认, 确认价格波动的资金含金量 =====================
        // 1. VMACD（成交量MACD）
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {
            sellScore += 15;
            sellReasons.add("VMACD零轴下死叉且绿柱放大(缩量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("VMACD零轴下死叉(缩量)");
        }
        // 2. OBVMA 能量潮均线
        if (obvStatus == DtOBVMAIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("OBV死叉 资金流出(卖出信号)");
        }

        // ===================== 波动/支撑(BOLL + ATR = 50分) -- 波动爆发, 确定止损空间与边界突破 =====================
        // 1. BOLL 突破上轨压力    -- 短线止盈离场点
        if (bollInd.isBreakoutUp()) {
            sellScore += 10;
            sellReasons.add("BOLL突破上轨(卖出信号)");
        }
        if (bollInd.isConsolidation()) {                                //  BOLL "收口盘整"：布林带收口 + 中轨走平或下倾  若跌破中轨则需离场
            sellScore += 10;
            sellReasons.add("BOLL开口收窄且中轨走平或下倾(卖出信号)");
        }
        // 2. ATR 超短线止损, 极端的市场情绪 ATR吊灯止损
        if (atrInd.volumeWarn() && closePrice.isLessThan(ema5N)) {      // MTR > 3 * ATR 情绪极值/变盘预警, 风险极高, 减仓、停止开仓、收紧止损
            sellScore += 15;
            sellReasons.add("ATR空头极端,利空出尽(MTR>3*ATR)");
        } else if (atrInd.volumeWarn()) {
            sellScore += 10;
            sellReasons.add("ATR变盘预警(MTR>3*ATR)");
        }
        // ATR吊灯止损 卖出规则：当前收盘价跌破止损线 close < (20日最高价 - 2.5 * ATR)
        if (closePrice.isLessThan(highest.minus(atrInd.getAtr().multipliedBy(numOf(2.5))))) {
            sellScore += 15;
            sellReasons.add("ATR跌破止损线，强力卖出(吊灯止损)");
        }

        return ResonanceSignal.builder()
                .buyScore(buyScore / 2)                                 // 总分200分，换算成100分
                .buyReason(StringUtil.joinWithIndex(HASH, buyReasons))
                .sellScore(sellScore / 2)                               // 总分200分，换算成100分
                .sellReason(StringUtil.joinWithIndex(HASH, sellReasons))
                .build();
    }


    /**
     * 多因子共振信号 -> Kline指标(1分钟) -- 确定买卖点
     * *
     * 趋势       EMA, MACD       确认当前是多头还是空头市场
     * 动能       RSI, KDJ, WR    寻找超买超卖后的反转或爆发点
     * 量能       VMACD, OBVMA    确认价格波动的资金含金量
     * 波动/支撑   BOLL            确定止损空间与边界突破
     */
    public static ResonanceSignal judgeResonanceMinute(int lastIndex, Num closePrice, DtEMAIndicator ema5Ind, DtEMAIndicator ema10Ind,
                                                       DtMACDIndicator macdInd, DtRSIIndicator rsiInd, DtKDJIndicator kdjInd, DtWRIndicator wrInd, DtVMACDIndicator vmacdInd,
                                                       DtOBVMAIndicator obvmaInd, DtBOLLIndicator bollInd, DtATRIndicator atrInd, Num highest) {

        Num ema5N = ema5Ind.getValue(lastIndex);
        Num ema10N = ema10Ind.getValue(lastIndex);

        DtMACDIndicator.CrossStatus macdStatus = macdInd.getCrossStatus();
        DtKDJIndicator.CrossStatus kdjStatus = kdjInd.getCrossStatus();
        DtVMACDIndicator.CrossStatus vmacdStatus = vmacdInd.getCrossStatus();
        DtOBVMAIndicator.CrossStatus obvStatus = obvmaInd.getCrossStatus();

        double buyScore = 0, sellScore = 0;
        List<String> buyReasons = new ArrayList<>();
        List<String> sellReasons = new ArrayList<>();

        // =====================  买入信号和评分 =====================
        // ===================== 趋势(EMA + MACD = 30分): 确认当前是多头还是空头市场  =====================
        // 1. EMA 多头排列
        if (closePrice.isGreaterThan(ema5N) && ema5N.isGreaterThan(ema10N)) {
            buyScore += 10;
            buyReasons.add("EMA多头排列,短期强势(价格>EMA5>EMA10)");
        } else if (ema5N.isGreaterThan(ema10N) && ema5Ind.getValue(lastIndex - 1).isLessThanOrEqual(ema10Ind.getValue(lastIndex - 1))) {      // 金叉
            buyScore += 5;
            buyReasons.add("EMA金叉");   // 金叉: 当前 MA5 > MA10; 前一刻 MA5 <= MA10
        }
        // 2. MACD 零轴上金叉
        if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {   // 金叉且红柱放大
            buyScore += 20;
            buyReasons.add("MACD零轴上金叉且红柱放大(动能强)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("MACD零轴上金叉");
        }

        // ===================== 动能(RSI + KDJ + WR = 50分) -- 灵敏择时, 寻找超买超卖后的反转或爆发点 =====================
        // 1. RSI 在50~70之间最强
        if (rsiInd.isSeriousOversold()) {     // 向上反转信号
            buyScore += 20;
            buyReasons.add("RSI严重超卖，短线反弹(≤20)");
        } else if (rsiInd.isOversold()) {     // 向上反转信号
            buyScore += 10;
            buyReasons.add("RSI超卖(<30)");
        }
        // 2. KDJ 对短线拐点极其灵敏
        if (kdjStatus == DtKDJIndicator.CrossStatus.GOLDEN_CROSS) {   // 金叉
            if (kdjInd.isLowK() || kdjInd.isLowD()) {
                buyScore += 10;
                buyReasons.add("(KDJ低位金叉，强烈买入信号(K<20)");       // 低位金叉（K<20）：代表价格超跌后的动能反转，此时买入信号最为准确。
            } else {
                buyScore += 5;
                buyReasons.add("(KDJ金叉");
            }
        }
        if (kdjInd.isSeriousOversold()) {                             //  KDJ超卖区（机会显现） 精准买卖点（J值比K/D更准）
            buyScore += 10;
            buyReasons.add("(KDJ严重超卖，买入信号(J<10)");
        } else if (kdjInd.isOverbought()) {
            buyScore += 5;
            buyReasons.add("(KDJ超卖，买入信号(J<20)");
        }
        // 3. WR ≥ 80 超卖区回升并突破-50, 辅助确认超卖（避免RSI假信号）
        if (wrInd.isOversold()) {
            buyScore += 10;
            buyReasons.add("WR超卖区，买入机会(≥80)");                  // 等待信号确认,股价重新站上日线均价线时，才是安全的低吸时点。
        }

        // ===================== 量能(VMACD + OBVMA = 30分) -- 量能确认, 确认价格波动的资金含金量 =====================
        // 1. VMACD 量能验证真伪关键   -- 隔夜条件：VMACD 红柱 → 量价配合  15分
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS_RED) {   // 金叉且红柱放大
            buyScore += 20;
            buyReasons.add("VMACD零轴上金叉且红柱放大(放量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("VMACD零轴上金叉(放量)");
        }
        // 2. OBVMA 能量潮均线 -- 隔夜条件：OBV > OBV_MA5  10分
        if (obvStatus == DtOBVMAIndicator.CrossStatus.GOLDEN_CROSS) {
            buyScore += 10;
            buyReasons.add("OBV金叉，资金流入(买入信号)");
        }

        // ===================== 波动/支撑(BOLL + ATR = 40分) -- 波动爆发, 确定止损空间与边界突破 =====================
        // 1. BOLL 突破下轨支撑 15分
        if (bollInd.isBreakoutDown()) {
            buyScore += 15;
            buyReasons.add("BOLL突破下轨支撑(买入信号)");
        }
        if (bollInd.isBullishBreakout()) {                              // BOLL "开口上涨"：中轨向上倾斜 + 布林带开口 15分
            buyScore += 15;
            buyReasons.add("BOLL开口且中轨向上倾斜(买入信号)");
        }
        // 2. ATR 超短线止损, 动力确认: MTR>1.5*ATR（波动扩张）趋势加速/异动突破  确认方向后加仓
        if (atrInd.volumeConfirm() && closePrice.isGreaterThan(ema5N)) { // 价格上升趋势，且MTR>1.5*ATR（波动扩张）时入场，做多信号
            buyScore += 10;
            buyReasons.add("ATR波动扩张，价格上升且MTR>1.5*ATR");
        }

        // =====================  卖出信号和评分 =====================
        // ===================== 趋势(EMA + MACD = 30分): 确认当前是多头还是空头市场  =====================
        // 1. EMA空头排列
        if (closePrice.isLessThan(ema5N) && ema5N.isLessThan(ema10N)) {
            sellScore += 10;
            sellReasons.add("EMA空头排列,短期弱势(价格<EMA5<EMA10)");
        } else if (ema5N.isLessThan(ema10N) && ema5Ind.getValue(lastIndex - 1).isGreaterThan(ema10Ind.getValue(lastIndex - 1))) {      // 死叉
            sellScore += 5;
            sellReasons.add("EMA死叉");
        }
        // 2. MACD 零轴下死叉
        if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {          // 死叉且绿柱放大
            sellScore += 20;
            sellReasons.add("MACD零轴下死叉且绿柱放大(动能弱)");
        } else if (macdStatus == DtMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("MACD零轴下死叉");
        }

        // ===================== 动能(RSI + KDJ + WR = 45分) -- 灵敏择时, 寻找超买超卖后的反转或爆发点 =====================
        // 1. RSI -- 隔夜条件：RSI6 在 50~70 之间最强
        if (rsiInd.isSeriousOverbought()) {        // 向下反转信号
            sellScore += 15;
            sellReasons.add("RSI严重超买，不隔夜(≥80)");
        } else if (rsiInd.isOverbought()) {         // 向下反转信号
            sellScore += 10;
            sellReasons.add("RSI超买(>70)");
        }
        // 2. KDJ高位死叉
        if (kdjStatus == DtKDJIndicator.CrossStatus.DEATH_CROSS) {    // 死叉
            if (kdjInd.isHighK() || kdjInd.isHighD()) {
                sellScore += 10;
                sellReasons.add("(KDJ高位死叉，强烈卖出信号(K>80)");
            } else {
                sellScore += 5;
                sellReasons.add("(KDJ死叉");
            }
        }
        if (kdjInd.isSeriousOverbought()) {                            //   KDJ超买区（风险积聚）
            sellScore += 10;
            sellReasons.add("(KDJ严重超买，卖出信号(J>90)");
        } else if (kdjInd.isOverbought()) {
            sellScore += 5;
            sellReasons.add("(KDJ超买，卖出信号(J>80)");
        }
        // 3. WR ≤ 20 超买区时，系统发出首个减仓信号。
        if (wrInd.isOverbought()) {
            sellScore += 10;
            sellReasons.add("WR超买区，卖出信号(≤20)");
        }

        // ===================== 量能(VMACD + OBVMA = 25分) -- 量能确认, 确认价格波动的资金含金量 =====================
        // 1. VMACD（成交量MACD）
        if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS_GREEN) {
            sellScore += 15;
            sellReasons.add("VMACD零轴下死叉且绿柱放大(缩量)");
        } else if (vmacdStatus == DtVMACDIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("VMACD零轴下死叉(缩量)");
        }
        // 2. OBVMA 能量潮均线
        if (obvStatus == DtOBVMAIndicator.CrossStatus.DEATH_CROSS) {
            sellScore += 10;
            sellReasons.add("OBV死叉 资金流出(卖出信号)");
        }

        // ===================== 波动/支撑(BOLL + ATR = 50分) -- 波动爆发, 确定止损空间与边界突破 =====================
        // 1. BOLL 突破上轨压力    -- 短线止盈离场点
        if (bollInd.isBreakoutUp()) {
            sellScore += 10;
            sellReasons.add("BOLL突破上轨(卖出信号)");
        }
        if (bollInd.isConsolidation()) {                                //  BOLL "收口盘整"：布林带收口 + 中轨走平或下倾  15分
            sellScore += 10;
            sellReasons.add("BOLL开口收窄且中轨走平或下倾(卖出信号)");
        }
        // 2. ATR 超短线止损, 极端的市场情绪 ATR吊灯止损
        if (atrInd.volumeWarn() && closePrice.isLessThan(ema5N)) {      // MTR > 3 * ATR 情绪极值/变盘预警, 风险极高, 减仓、停止开仓、收紧止损
            sellScore += 15;
            sellReasons.add("ATR空头极端,利空出尽(MTR>3*ATR)");
        } else if (atrInd.volumeWarn()) {
            sellScore += 10;
            sellReasons.add("ATR变盘预警(MTR>3*ATR)");
        }
        // ATR吊灯止损 卖出规则：当前收盘价跌破止损线 close < (20日最高价 - 2.5 * ATR)
        if (closePrice.isLessThan(highest.minus(atrInd.getAtr().multipliedBy(numOf(2.5))))) {
            sellScore += 15;
            sellReasons.add("ATR跌破止损线，强力卖出(吊灯止损)");
        }

        return ResonanceSignal.builder()
                .buyScore(buyScore * 100 / 150)                         // 总分140分，换算成100分
                .buyReason(StringUtil.joinWithIndex(HASH, buyReasons))
                .sellScore(sellScore / 2)                               // 总分200分，换算成100分
                .sellReason(StringUtil.joinWithIndex(HASH, sellReasons))
                .build();
    }
}
