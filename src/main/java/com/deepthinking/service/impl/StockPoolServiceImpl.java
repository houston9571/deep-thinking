package com.deepthinking.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.mapper.StockPoolMapper;
import com.deepthinking.service.*;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.deepthinking.common.constant.Constants.YI;
import static com.deepthinking.common.constant.MarketType.getTradeDateStr;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPoolServiceImpl extends MybatisBaseServiceImpl<StockPoolMapper, StockPool> implements StockPoolService {

    private final StockPoolMapper stockPoolMapper;

    private final StockKlineMinuteService stockKlineMinuteService;

    private final StockTechMinuteService stockTechMinuteService;

    private final StockKlineDailyService stockKlineDailyService;

    private final StockTechDailyService stockTechDailyService;

    private final StockInfoService stockInfoService;

    public List<StockPool> queryStockPool() {
        List<StockPool> stocks = stockPoolMapper.queryLastDay(getTradeDateStr());

        return stocks;
    }


    /**
     * 根据股票池更新个股分时数据 1分钟
     */
    public Result<Integer> syncStockCalcKlineIndicators() {
        List<StockPool> stocks = stockPoolMapper.queryLastDay(getTradeDateStr());
        stocks.forEach(stock -> {
            String stockCode = stock.getStockCode();
            if (MarketType.contains(stockCode, stock.getStockName())) {
                stockKlineMinuteService.syncStockKlineMinute(stockCode);
                stockTechMinuteService.syncStockTrendsMinute(stockCode);
            }
        });
        return Result.success(stocks.size());
    }

    /**
     * 精选股票加入股票池，同时更新日线数据和日线指标
     */
    public void addStockPools(Map<String, StockPool> map) {
        List<StockKlineDaily> stockKlineDailyList = stockKlineDailyService.getStockKlineDailyList(getTradeDateStr());
        List<StockPool> stockPools = Lists.newArrayList();
        for (StockKlineDaily stock : stockKlineDailyList) {
            if (map.containsKey(stock.getStockCode()) && isPassedStrategy(stock)) {         // top20概念板块匹配, 策略精选后加入股票池
                stockPools.add(map.get(stock.getStockCode()));
            }
        }
        log.info(">>>>>addStockPools top20概念板块匹配, 策略精选后加入股票池:{} ", stockPools.size());
        saveOrUpdateBatch(stockPools, new String[]{"stock_code", "trade_date"});
        // todo 计算日线指标
    }

    public void addStockPools(List<StockKlineDaily> stockKlineDailyList) {
        List<StockPool> stockPools = Lists.newArrayList();
        for (StockKlineDaily stock : stockKlineDailyList) {
            if (isPassedStrategy(stock)) {                  // 策略精选后加入股票池
                StockPool p = new StockPool();
                BeanUtil.copyProperties(stock, p, true);
                stockPools.add(p);
            }
        }
        log.info(">>>>>addStockPools 日线行情更新, 策略精选后加入股票池:{} ", stockPools.size());
        saveOrUpdateBatch(stockPools, new String[]{"stock_code", "trade_date"});
        // todo 计算日线指标
    }

    /**
     * 根据策略精选股票
     */
    boolean isPassedStrategy(StockKlineDaily daily) {
        String stockCode = daily.getStockCode();
        StockInfo stockInfo = stockInfoService.getStockInfo(stockCode);
        // 基本面过滤
        boolean noPass = daily.getFreeMarketCap() < 30 * YI || daily.getFreeMarketCap() > 1000 * YI;
        noPass = noPass || daily.getChangePercent().doubleValue() > 7 || daily.getChangePercent().doubleValue() < 2;
        noPass = noPass || daily.getMainNetIn().doubleValue() < 0;
        noPass = noPass || daily.getVolumeRatio().doubleValue() < 1.2;

        if (noPass) {
            return false;
        }

        // 技术指标过滤
//        StockTechDaily techDaily = stockTechDailyService.getAndCalcStockTechDaily(stockCode);
//        if (ObjectUtil.isEmpty(techDaily)) {
//            return false;
//        }

        return true;
    }
}
