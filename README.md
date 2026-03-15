
Deep Thinking Quant System

1. 股票基本信息,所属概念


2. 股票实时交易行情,实时资金流向


3. 龙虎榜信息，主力买入





########### 数据同步策略 ############
1. 股票列表 每10分钟更新一次，计算一次日线指标
   /task/stock/daily -- stock_kline_daily -> stock_tech_daily   筛选股票入股票池 stock_pools
2. Kline分时数据 每分钟更新
    
2. 板块列表 
   每5分钟  更新top2  筛选股票入股票池   concept_daily  concept_stock  stock_pools
   15：10  全量更新   127.0.0.1/task/concept/daily -- concept_daily concept_stock


2. 龙虎榜数据
   龙虎榜机构列表更新 17:00  127.0.0.1/task/dragon/dept/2026-02-10  -- dragon_dept org_dept
   龙虎榜个股列表更新 17:10  127.0.0.1/task/dragon/stock/2026-02-10  -- dragon_stock dragon_stock_detail


4.  