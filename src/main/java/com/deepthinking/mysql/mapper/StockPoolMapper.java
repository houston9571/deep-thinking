package com.deepthinking.mysql.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepthinking.mysql.entity.StockPool;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface StockPoolMapper extends BaseMapper<StockPool> {

    @Select("SELECT DISTINCT stock_code, stock_name, trade_date FROM stock_pool WHERE trade_date=(SELECT MAX(trade_date) FROM stock_pool)")
    List<StockPool> queryLastDay();

}
