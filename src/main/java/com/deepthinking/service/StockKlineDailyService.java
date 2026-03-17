package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockKlineDaily;

import java.util.List;

public interface StockKlineDailyService extends MybatisBaseService<StockKlineDaily> {

    List<StockKlineDaily> getStockKlineDailyByConcept(String conceptCode, String tradeDate);

    StockKlineDaily getStockKlineDaily(String stockCode);

    List<StockKlineDaily> getStockKlineDailyLimit(String stockCode, String tradeDate, int limit);

    List<StockKlineDaily> getStockKlineDailyList(String tradeDate);

    List<StockKlineDaily> syncStockKlineDailyList(String tradeDate);


}
