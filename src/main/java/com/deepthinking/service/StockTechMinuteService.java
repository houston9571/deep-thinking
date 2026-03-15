package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.StockTechMinute;

public interface StockTechMinuteService extends MybatisBaseService<StockTechMinute> {

    Integer syncStockTechMinute();

    void syncStockTechMinute(String stockCode);

    void syncStockTechMinuteAllDay(String stockCode);



}
