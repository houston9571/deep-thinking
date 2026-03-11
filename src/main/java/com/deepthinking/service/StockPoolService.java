package com.deepthinking.service;

import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;

import java.util.List;
import java.util.Map;

public interface StockPoolService extends MybatisBaseService<StockPool> {

    List<StockPool> queryStockPool();

    Result<Integer> syncStockCalcKlineIndicators();

    void addStockPools(Map<String, StockPool> map);

    void addStockPools(List<StockKlineDaily> stockKlineDailyList);
}
