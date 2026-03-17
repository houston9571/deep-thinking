package com.deepthinking.mysql.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepthinking.mysql.entity.StockKlineDaily;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockKlineDailyMapper extends BaseMapper<StockKlineDaily> {



    @Select("SELECT * FROM stock_kline_daily WHERE stock_code = #{stockCode} ORDER BY trade_date DESC LIMIT #{limit} ")
    List<StockKlineDaily> getStockKlineDailyLimit(String stockCode, int limit);

}
