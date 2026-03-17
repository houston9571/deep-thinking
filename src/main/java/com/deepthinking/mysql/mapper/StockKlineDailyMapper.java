package com.deepthinking.mysql.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepthinking.mysql.entity.StockKlineDaily;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockKlineDailyMapper extends BaseMapper<StockKlineDaily> {



    @Select("select s.* from concept_stock c left join stock_kline_daily s on c.stock_code=s.stock_code where c.concept_code=#{conceptCode} and s.trade_date=#{tradeDate} ")
    List<StockKlineDaily> getStockKlineDailyByConcept(String conceptCode, String tradeDate);

    @Select("SELECT * FROM stock_kline_daily WHERE stock_code = #{stockCode} ORDER BY trade_date DESC LIMIT #{limit} ")
    List<StockKlineDaily> getStockKlineDailyLimit(String stockCode, int limit);

}
