package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.TradeCalendar;

import java.util.List;
import java.util.Map;

public interface TradeCalendarService extends MybatisBaseService<TradeCalendar> {


    int genYearCalendar();

    List<String> statStockKlineDaily();
}