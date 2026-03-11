package com.deepthinking.mysql.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepthinking.mysql.entity.StockPool;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockPoolMapper extends BaseMapper<StockPool> {

    @Select("SELECT stock_code, stock_name, trade_date, price, change_percent, turnover, amplitude FROM stock_pool WHERE trade_date=#{tradeDate}")
    List<StockPool> queryLastDay(String tradeDate);

}
