package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockTechDaily;

public interface StockTechDailyService extends MybatisBaseService<StockTechDaily> {

    StockTechDaily getStockTechDaily(String stockCode, String tradeDate) ;

    StockTechDaily calcStockTechDaily(String stockCode, String tradeDate);


}
