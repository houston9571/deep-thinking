package com.deepthinking.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.deepthinking.common.constant.Constants;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.mapper.StockPoolMapper;
import com.deepthinking.service.StockKlineMinuteService;
import com.deepthinking.service.StockPoolService;
import com.deepthinking.service.StockTechMinuteService;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

import static cn.hutool.core.text.StrPool.COMMA;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPoolServiceImpl extends MybatisBaseServiceImpl<StockPoolMapper, StockPool> implements StockPoolService {

    private final StockPoolMapper stockPoolMapper;

    private final StockKlineMinuteService stockKlineMinuteService;

    private final StockTechMinuteService stockTechMinuteService;

    public List<StockPool> queryStockPool() {
        List<StockPool> stocks = stockPoolMapper.queryLastDay();

        return stocks;
    }


    /**
     * 根据股票池更新个股分时数据 1分钟
     */
    public Result<Integer> syncStockMinuteDateFromPool() {
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
     * 精选股票加入股票池
     * 从缓存中获取klineDaily，如果没有生成，则不参加策略精选
     */
    public void addStockPoolWithConcept(List<StockPool> list) {
        List<StockPool> pools = Lists.newArrayList();
        list.forEach(stock -> {
            StockKlineDaily daily = StockKlineDaily.builder().stockCode(stock.getStockCode()).build();
            if (isPassedStrategy(daily)) {
                pools.add(stock);
            }
        });
        saveOrUpdateBatch(pools, new String[]{"stock_code", "trade_date"});
    }

    public void addStockPoolWithKlineDaily(List<StockKlineDaily> list) {
        List<StockPool> pools = Lists.newArrayList();
        list.forEach(daily -> {
            if (isPassedStrategy(daily)) {
                StockPool stock = new StockPool();
                BeanUtil.copyProperties(daily, stock, true);
                pools.add(stock);
            }
        });
        saveOrUpdateBatch(pools, new String[]{"stock_code", "trade_date"});
    }

    /**
     * 交易日09:42第一次同步klineDaily后，使用当天Kline数据判断
     */
    boolean isPassedStrategy(StockKlineDaily daily) {

        return true;
    }
}
