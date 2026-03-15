package com.deepthinking.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alicp.jetcache.anno.Cached;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.entity.StockTechDaily;
import com.deepthinking.mysql.mapper.StockPoolMapper;
import com.deepthinking.service.StockInfoService;
import com.deepthinking.service.StockKlineDailyService;
import com.deepthinking.service.StockPoolService;
import com.deepthinking.service.StockTechDailyService;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.deepthinking.common.constant.Constants.DAYS_1;
import static com.deepthinking.strategy.SelectionStrategy.passBasicSelection;
import static com.deepthinking.strategy.SelectionStrategy.passIndicatorSelection;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPoolServiceImpl extends MybatisBaseServiceImpl<StockPoolMapper, StockPool> implements StockPoolService {

    private static final String CACHE_KEY = "StockPool:";

    private final StockPoolMapper stockPoolMapper;

    private final StockKlineDailyService stockKlineDailyService;

    private final StockTechDailyService stockTechDailyService;

    private final StockInfoService stockInfoService;

    @Cached(name = CACHE_KEY, key = "#tradeDate", expire = DAYS_1)
    public List<StockPool> queryStocks(String tradeDate) {
        List<StockPool> stocks = stockPoolMapper.queryStocks(tradeDate);
        return stocks;
    }




    /**
     * 根据策略精选股票
     * 买入核心：只做「日线高分共振 + 分时高分共振 + 放量 + 筹码集中」的票，只选 “双重强力买入” 的股票
     */
    public Integer execStockPoolSelection(String tradeDate) {
        List<StockKlineDaily> stockKlineDailyList = stockKlineDailyService.getStockKlineDailyList(tradeDate);
        List<StockPool> stockPools = Lists.newArrayList();
        for (StockKlineDaily stock : stockKlineDailyList) {
            String stockCode = stock.getStockCode();
            StockInfo stockInfo = stockInfoService.getStockInfo(stockCode);
            if (!passBasicSelection(stockInfo, stock)) {                  // 核心结论：隔夜持仓需精选基本面稳健、现金流充沛、行业抗风险的股票。
                continue;
            }
            StockTechDaily techDaily = stockTechDailyService.getStockTechDaily(stockCode, tradeDate);

            if (!passIndicatorSelection(techDaily)) {                     // 指标和原则
                continue;
            }
            StockPool p = new StockPool();
            BeanUtil.copyProperties(stock, p, true);
            stockPools.add(p);
        }
        log.info(">>>>>addStockPools 日线行情更新, 策略精选后加入股票池:{} ", stockPools.size());
        saveOrUpdateBatch(stockPools, new String[]{"stock_code", "trade_date"});
        return stockPools.size();
    }

}
