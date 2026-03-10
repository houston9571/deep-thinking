package com.deepthinking.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.deepthinking.common.constant.Constants;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.common.utils.RedisUtils;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.entity.StockTechDaily;
import com.deepthinking.mysql.mapper.StockPoolMapper;
import com.deepthinking.service.*;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.service.impl.StockKlineDailyServiceImpl.KLINE_LIST_KEY;

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
        List<StockPool> stocks = stockPoolMapper.queryLastDay();

        return stocks;
    }


    /**
     * 根据股票池更新个股分时数据 1分钟
     */
    public Result<Integer> syncStockCalcKlineIndicators() {
        List<StockPool> stocks = stockPoolMapper.queryLastDay();
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
    public void addStockPools(List<StockPool> pools) {
        Map<String, StockPool> map = pools.stream().collect(Collectors.toMap(StockPool::getStockCode, s -> s));
        List<StockKlineDaily> stockKlineDailyList = stockKlineDailyService.syncStockKlineDailyList();       // 缓存29分钟失效后，全量同步股票日线实时行情
        List<StockPool> stockPools = Lists.newArrayList();
        stockKlineDailyList.forEach(stock -> {
            if (isPassedStrategy(stock)) {           // 匹配到top25概念板块加入股票池
                if(map.containsKey(stock.getStockCode())){
                    stockPools.add(map.get(stock.getStockCode()));
                }else {                              // 策略精选后加入股票池
                    StockPool p = new StockPool();
                    BeanUtil.copyProperties(stock, p, true);
                    stockPools.add(p);
                }
            }
        });
        saveOrUpdateBatch(stockPools, new String[]{"stock_code", "trade_date"});
    }

    // todo 加入计算日线指标  筛选股票进入股票池

    /**
     *
     */
    boolean isPassedStrategy(StockKlineDaily daily) {
        String stockCode = daily.getStockCode();

        StockInfo stockInfo = stockInfoService.getStockInfo(stockCode);
        // 基本面过滤


        // 技术指标过滤
        StockTechDaily techDaily = stockTechDailyService.getAndCalcStockTechDaily(stockCode);
        if (ObjectUtil.isEmpty(techDaily)) {
            return false;
        }

        return true;
    }
}
