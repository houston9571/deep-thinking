package com.deepthinking.service;

import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockPool;

public interface StockPoolService extends MybatisBaseService<StockPool> {

    Result<Integer> syncStockMinuteDateFromPool();

}
