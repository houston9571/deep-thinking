package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockKlineDaily;

import java.time.LocalDate;
import java.util.List;

public interface StockKlineDailyService extends MybatisBaseService<StockKlineDaily> {

    StockKlineDaily getStockKlineDaily(String stockCode);

    List<StockKlineDaily> getStockKlineDailyLimit(String stockCode, int limit);

    List<StockKlineDaily> getStockKlineDailyList(String tradeDate);

    List<StockKlineDaily> syncStockKlineDailyList(String tradeDate);


}
