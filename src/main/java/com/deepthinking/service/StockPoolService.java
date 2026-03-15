package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockPool;

import java.util.List;

public interface StockPoolService extends MybatisBaseService<StockPool> {

    List<StockPool> queryStocks(String tradeDate) ;

    Integer execStockPoolSelection(String tradeDate) ;

}
