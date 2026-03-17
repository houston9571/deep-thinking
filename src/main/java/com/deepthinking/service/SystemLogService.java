package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.SystemLog;
import com.deepthinking.mysql.entity.TradeCalendar;

import java.time.LocalDate;
import java.util.List;

public interface SystemLogService extends MybatisBaseService<SystemLog> {


    void saveSystemLog(String name, int count, long millis);

    void plusSystemLog(String name, int count, long millis);

    void printSystemLogs(LocalDate tradeDate);
}