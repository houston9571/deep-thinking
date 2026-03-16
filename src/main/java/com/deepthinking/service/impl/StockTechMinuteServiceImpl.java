package com.deepthinking.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.deepthinking.client.EastMoneyStockApi;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.common.enums.DateFormatEnum;
import com.deepthinking.common.utils.DateUtils;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.entity.StockTechMinute;
import com.deepthinking.mysql.mapper.StockTechMinuteMapper;
import com.deepthinking.service.StockPoolService;
import com.deepthinking.service.StockTechMinuteService;
import com.deepthinking.strategy.*;
import com.deepthinking.strategy.signal.DivergenceSignal;
import com.deepthinking.strategy.signal.ResonanceSignal;
import com.deepthinking.strategy.signal.VolumeAndPriceSignal;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.common.constant.Constants.*;
import static com.deepthinking.common.constant.Constants.NO;
import static com.deepthinking.common.constant.MarketType.getTradeDateStr;
import static com.deepthinking.common.constant.StockConstants.KLINE_1MIN;
import static com.deepthinking.strategy.OverNightStrategy.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTechMinuteServiceImpl extends MybatisBaseServiceImpl<StockTechMinuteMapper, StockTechMinute> implements StockTechMinuteService {

    private final StockTechMinuteMapper stockTechMinuteMapper;

    private final EastMoneyStockApi eastMoneyStockApi;

    private final StockPoolService stockPoolService;

    /**
     * 更新股票池、自选股票、持仓股票 1分钟
     */
    public Integer syncStockTechMinute() {
        List<StockPool> stocks = stockPoolService.queryStocks(getTradeDateStr());
        stocks.forEach(stock -> {
            String stockCode = stock.getStockCode();
            if (MarketType.contains(stockCode, stock.getStockName())) {
                syncStockTechMinute(stockCode);
            }
        });
        return stocks.size();
    }


    /**
     * 股票每分钟交易行情和资金流向 每1分钟
     */
    public void syncStockTechMinute(String stockCode) {
        JSONObject json = eastMoneyStockApi.getFundsFlowLines(stockCode, MarketType.getMarketCode(stockCode), KLINE_1MIN, 15);
        JSONObject data = json.getJSONObject(LABEL_DATA);
        if (ObjectUtil.isEmpty(data) || !data.containsKey("klines")) {
            log.error("未获取到页面信息 getFundsFlowLines: {}", stockCode);
            return;
        }
        JSONArray lines = data.getJSONArray("klines");
        String stockName = data.getString("name");
        if (CollectionUtil.isEmpty(lines)) {
            log.error("没有数据，可能停盘 syncStockKlineMinute: {}", stockCode);
        }
        if (lines.size() < 15) {
            log.info(">>>>>syncStockTrendsMinute k线数据未满足15条 stockCode:{} stockName:{} ", stockCode, stockName);
            return;
        }

        json = eastMoneyStockApi.getStockTrends(stockCode, MarketType.getMarketCode(stockCode), SystemClock.now());
        data = json.getJSONObject(LABEL_DATA);
        if (ObjectUtil.isEmpty(data) || !data.containsKey("trends")) {
            log.error("未获取到页面信息 getStockTrends: {}", stockCode);
            return;
        }
        JSONArray trends = data.getJSONArray("trends");

        List<StockTechMinute> techMinuteList = Lists.newArrayList();

        String[] lastLine = lines.getString(lines.size() - 1).split(COMMA);
        String tradeTime = lastLine[0];
        for (int i = trends.size() - lines.size(); i < trends.size(); i++) {
            StockTechMinute tech = StockTechMinute.builder().stockCode(stockCode).stockName(stockName).build();
            String[] trend = trends.getString(i).split(COMMA);
            if (StrUtil.equals(tradeTime, trend[0])) {
                tech.setMainNetIn(lastLine[1]);
                tech.setSmallNetIn(lastLine[2]);
                tech.setMediumNetIn(lastLine[3]);
                tech.setLargeNetIn(lastLine[4]);
                tech.setSuperLargeNetIn(lastLine[5]);
            }
            String[] t = trend[0].split("\\s+");
            tech.setTradeDate(DateUtils.parseLocalDate(t[0], DateFormatEnum.DATE));
            tech.setTradeTime(DateUtils.parseLocalTime(t[1] + ":00", DateFormatEnum.TIME));
            tech.setOpen(new BigDecimal(trend[1]));
            tech.setClose(new BigDecimal(trend[2]));
            tech.setHigh(new BigDecimal(trend[3]));
            tech.setLow(new BigDecimal(trend[4]));
            tech.setVolume(Double.valueOf(trend[5]).longValue());           // 分时成交量
            tech.setAmount(Double.valueOf(trend[6]).longValue());           // 分时成交额
            tech.setTotalVolume(Double.valueOf(trend[10]).longValue());     // 总成交量
            tech.setTotalAmount(Double.valueOf(trend[11]).longValue());     // 总成交额
            techMinuteList.add(tech);
        }
        StockTechMinute tech = calcIndicator(techMinuteList);
        saveOrUpdate(tech, new String[]{"stock_code", "trade_date", "trade_time"});
        log.info(">>>>>syncStockTrendsMinute stockCode:{} ", stockCode);
    }

    /**
     * 计算当天所有的k线指标
     */
    public void syncStockTechMinuteAllDay(String stockCode) {
        JSONObject json = eastMoneyStockApi.getFundsFlowLines(stockCode, MarketType.getMarketCode(stockCode), KLINE_1MIN, 240);
        JSONObject data = json.getJSONObject(LABEL_DATA);
        if (ObjectUtil.isEmpty(data) || !data.containsKey("klines")) {
            log.error("未获取到页面信息 getFundsFlowLines: {}", stockCode);
            return;
        }
        JSONArray lines = data.getJSONArray("klines");
        String stockName = data.getString("name");

        json = eastMoneyStockApi.getStockTrends(stockCode, MarketType.getMarketCode(stockCode), SystemClock.now());
        data = json.getJSONObject(LABEL_DATA);
        if (ObjectUtil.isEmpty(data) || !data.containsKey("trends")) {
            log.error("未获取到页面信息 getStockTrends: {}", stockCode);
            return;
        }
        JSONArray trends = data.getJSONArray("trends");

        for (int i = 0; i < 16; i++) {      // 竞价集合09:31之前的数据不要
            trends.removeFirst();
        }
        int count = 0;
        for (int i = 0; i <= trends.size() - 15; i++) {      // 竞价集合09:30之前的数据不要
            List<StockTechMinute> techMinuteList = Lists.newArrayList();
            int end = i + 15;
            for (int j = i; j < end; j++) {                 // 每次获取15条进行计算
                StockTechMinute tech = StockTechMinute.builder().stockCode(stockCode).stockName(stockName).build();
                String[] trend = trends.getString(j).split(COMMA);
                if (j == end - 1) {
                    String[] lastLine = lines.getString(j).split(COMMA);
                    tech.setMainNetIn(lastLine[1]);
                    tech.setSmallNetIn(lastLine[2]);
                    tech.setMediumNetIn(lastLine[3]);
                    tech.setLargeNetIn(lastLine[4]);
                    tech.setSuperLargeNetIn(lastLine[5]);
                }
                String[] t = trend[0].split("\\s+");
                tech.setTradeDate(DateUtils.parseLocalDate(t[0], DateFormatEnum.DATE));
                tech.setTradeTime(DateUtils.parseLocalTime(t[1] + ":00", DateFormatEnum.TIME));
                tech.setOpen(new BigDecimal(trend[1]));
                tech.setClose(new BigDecimal(trend[2]));
                tech.setHigh(new BigDecimal(trend[3]));
                tech.setLow(new BigDecimal(trend[4]));
                tech.setVolume(Double.valueOf(trend[5]).longValue());           // 分时成交量
                tech.setAmount(Double.valueOf(trend[6]).longValue());           // 分时成交额
                tech.setTotalVolume(Double.valueOf(trend[10]).longValue());     // 总成交量
                tech.setTotalAmount(Double.valueOf(trend[11]).longValue());     // 总成交额
                techMinuteList.add(tech);
            }
            StockTechMinute tech = calcIndicator(techMinuteList);
            saveOrUpdate(tech, new String[]{"stock_code", "trade_date", "trade_time"});
            count++;
        }
        log.info(">>>>>syncStockTrendsMinuteAll stockCode:{} count:{}", stockCode, count);
    }


    /**
     * ================= 实时计算分时指标 ====================
     * 日线共振：确定股票能不能做
     * 分时指标：确定什么时候买
     * 双重共振：胜率可达 70%~85%（超短线 1-3 天）
     * 所有指标周期统一，无滞后、无冲突
     */
    public static StockTechMinute calcIndicator(List<StockTechMinute> list) {
        int size = list.size();
        StockTechMinute tech = list.getLast();
        // 至少需要10分钟数据（适配分时MA10/BOLL10）
        if (list.size() < 15) {
            log.warn("分时数据不足不计算，必须满足15条");
            return tech;
        }

        BaseBarSeries series = new BaseBarSeriesBuilder().withName(tech.getStockCode() + "_Minute").build();
        for (StockTechMinute t : list) {
            Instant tr = t.getTradeDate().atTime(t.getTradeTime()).atZone(ZoneId.of(ZONE_ID)).toInstant();
            series.addBar(new BaseBar(Duration.ofMinutes(1), tr.minusSeconds(60), tr,
                    DecimalNum.valueOf(t.getOpen()),
                    DecimalNum.valueOf(t.getHigh()),
                    DecimalNum.valueOf(t.getLow()),
                    DecimalNum.valueOf(t.getClose()),
                    DecimalNum.valueOf(t.getVolume()),
                    DecimalNum.valueOf(t.getAmount()),
                    1));
        }

        int lastIndex = series.getEndIndex();
        ClosePriceIndicator closePriceInd = new ClosePriceIndicator(series);
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);

        double ma5 = new SMAIndicator(volumeIndicator, 5).getValue(lastIndex).doubleValue();
        tech.setVolumeRatio(BigDecimal.valueOf(tech.getVolume() / ma5));

        // ===================== 趋势(EMA + MACD): 确认当前是多头还是空头市场  =====================
        // 1. EMA（指数移动平均） 短线参数：5 10   确定当前波段的多空基调
        DtEMAIndicator ema5Ind = new DtEMAIndicator(closePriceInd, 5);
        DtEMAIndicator ema10Ind = new DtEMAIndicator(closePriceInd, 10);
        Num ema5 = ema5Ind.getEMA();
        Num ema10 = ema10Ind.getEMA();
        tech.setEma5(ema5.bigDecimalValue());
        tech.setEma10(ema10.bigDecimalValue());

        // 2. MACD（平滑异同移动平均指数）（趋势+动能） 短线参数(fast=5, slow=13, signal=2)   零轴确定长短周期动量方向    -- 隔夜条件：MACD红柱、DIF > DEA。
        DtMACDIndicator macdInd = new DtMACDIndicator(closePriceInd, 5, 13, 2);    // 柱状图 (Histogram) = MACD线 - 信号线
        tech.setMacdDif(macdInd.getDIF().bigDecimalValue());
        tech.setMacdDea(macdInd.getDEA().bigDecimalValue());
        tech.setMacdBar(macdInd.getHistogram().bigDecimalValue());
        tech.setMacdStatus(macdInd.getCrossStatus());

        // 3. BIAS 乖离率
        DtBIASIndicator biasInd = new DtBIASIndicator(series, 5);
        Num bias = biasInd.getBias();
        tech.setBias(bias.bigDecimalValue());


        // ===================== 动能(RSI + KDJ + WR) -- 灵敏择时, 寻找超买超卖后的反转或爆发点 =====================
        // 1. RSI（相对强弱指标） 超短线最灵：6    衡量市场强弱与超买超卖
        DtRSIIndicator rsiInd = new DtRSIIndicator(series, 6);
        tech.setRsi6(rsiInd.getRSI().bigDecimalValue());

        // 2. KDJ（随机指标）短线参数：5 2 2   对短线拐点极其灵敏   默认算法可能与通达信/同花顺略有差异（平滑方式）
        DtKDJIndicator kdjInd = new DtKDJIndicator(series, 5, 2, 2);
        tech.setKdjK(kdjInd.getK().bigDecimalValue());
        tech.setKdjD(kdjInd.getD().bigDecimalValue());
        tech.setKdjJ(kdjInd.getJ().bigDecimalValue());
        tech.setKdjStatus(kdjInd.getCrossStatus());

        // 3. WR（威廉指标）极短线参数：6   用于1分钟或5分钟线，适合捕捉极速脉冲行情，预判趋势衰减
        DtWRIndicator wrInd = new DtWRIndicator(series, 6);
        tech.setWr6(wrInd.getWr().bigDecimalValue());


        // ===================== 量能(VMACD + OBVMA) -- 量能确认, 确认价格波动的资金含金量 =====================
        // 1. VMACD（成交量MACD）  短线参数：5,13,1   量平滑异同平均，量化资金动能    -- 隔夜条件：VMACD 红柱 → 量价配合
        DtVMACDIndicator vmacdInd = new DtVMACDIndicator(volumeIndicator, 5, 13, 2);
        DtVMACDIndicator.CrossStatus vmacdStatus = vmacdInd.getCrossStatus();
        tech.setVmacdDif(vmacdInd.getDIF().bigDecimalValue());
        tech.setVmacdDea(vmacdInd.getDEA().bigDecimalValue());
        tech.setVmacdBar(vmacdInd.getHistogram().bigDecimalValue());
        tech.setVmacdStatus(vmacdStatus);

        // 2. OBV_MA 能量潮均线确认资金流入流出     -- 隔夜条件：OBV > OBV_MA5
        DtOBVMAIndicator obvmaInd = new DtOBVMAIndicator(series, 5);
        tech.setObv(obvmaInd.getObv().longValue());
        tech.setObvMa5(obvmaInd.getObvMa().longValue());
        tech.setObvStatus(obvmaInd.getCrossStatus());

        // ===================== 波动/支撑(BOLL + ATR) -- 波动爆发, 确定止损空间与边界突破 =====================
        // 1. BOLL（布林带）短线参数：10 2  衡量价格相对于波动的边界位置
        DtBOLLIndicator bollInd = new DtBOLLIndicator(series, 10, 2);
        tech.setBollMid(bollInd.getMid().bigDecimalValue());
        tech.setBollUpper(bollInd.getUpper().bigDecimalValue());
        tech.setBollLower(bollInd.getLower().bigDecimalValue());
        tech.setBollMouthStatus(bollInd.getMouthStatus());
        tech.setBollMidTrend(bollInd.getMidTrend());

        // 2. ATR 波动率 计算超短线止损
        DtATRIndicator atrInd = new DtATRIndicator(series, 7);
        tech.setMtr(atrInd.getMtr().bigDecimalValue());
        tech.setAtr(atrInd.getAtr().bigDecimalValue());
        tech.setAtrStrong(atrInd.isAtrStrong() ? YES : NO);


        // ======================== 策略辅助 ======================== //

        Num highest = new HighestValueIndicator(new HighPriceIndicator(series), size / 2).getValue(lastIndex);
        Num lowest = new LowestValueIndicator(new LowPriceIndicator(series), size / 2).getValue(lastIndex);
        Num closePrice = closePriceInd.getValue(lastIndex);

        // ======================== 指标背离 Kline 6项 ========================
        DivergenceSignal dvg = judgeDivergence(closePrice, highest, lowest, macdInd, vmacdInd, kdjInd, rsiInd, obvmaInd, wrInd, null, null);
        tech.setDivergenceType(dvg.getDivergenceType());
        tech.setDivergenceStrength(dvg.getDivergenceStrength());
        tech.setDivergenceResult(dvg.getDivergenceResult());
        log.info("-----计算背离：{} {}_{}_{}", series.getName(), dvg.getDivergenceType(), dvg.getDivergenceStrength(), dvg.getDivergenceResult());

        // ======================== 多因子共振信号 Kline ========================
        ResonanceSignal resonance = judgeResonanceMinute(lastIndex, closePrice, ema5Ind, ema10Ind, macdInd, rsiInd, kdjInd, wrInd, vmacdInd, obvmaInd, bollInd, atrInd, highest);
        tech.setBuyScore(resonance.getBuyScore());
        tech.setBuyReason(resonance.getBuyReason());
        tech.setSellScore(resonance.getSellScore());
        tech.setSellReason(resonance.getSellReason());

        // ======================== 量价关系 ========================
        VolumeAndPriceSignal volumeAndPriceSignal = calcVolumeAndPrice(series, highest, lowest, ema5, ema10, bias, tech.getVolumeRatio(), obvmaInd, bollInd, dvg);
        tech.setSignalType(volumeAndPriceSignal.getSignalType());
        tech.setSignalLevel(volumeAndPriceSignal.getSignalLevel().getLevel());
        tech.setSignalResult(volumeAndPriceSignal.getSignalResult());

        return tech;
    }

}

