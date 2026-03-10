package com.deepthinking.service;

import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;

import java.util.List;

public interface StockPoolService extends MybatisBaseService<StockPool> {

    List<StockPool> queryStockPool();

    Result<Integer> syncStockMinuteDateFromPool();

    void addStockPoolWithConcept(List<StockPool> list);

    void addStockPoolWithKlineDaily(List<StockKlineDaily> list);
}
