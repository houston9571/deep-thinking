package com.deepthinking.mysql.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepthinking.mysql.entity.TradeCalendar;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface TradeCalendarMapper extends BaseMapper<TradeCalendar> {

    @Select(" SELECT JSON_OBJECT(trade_date, COUNT(1)) AS res  FROM stock_kline_daily GROUP BY trade_date ORDER BY trade_date DESC")
    List<String> statStockKlineDaily();
}
