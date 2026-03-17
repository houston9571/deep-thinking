package com.deepthinking.task;

import cn.hutool.core.date.StopWatch;
import com.deepthinking.common.thread.Threads;
import com.deepthinking.common.utils.OSUtils;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.entity.DragonStock;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;

import static com.deepthinking.common.constant.MarketType.*;

//@Lazy
@Slf4j
@Component
@RequiredArgsConstructor
class SyncTask {

    private final StockPoolService stockPoolService;

    private final StockInfoService stockInfoService;

    private final StockKlineMinuteService stockKlineMinuteService;

    private final StockTechMinuteService stockTechMinuteService;

    private final StockKlineDailyService stockKlineDailyService;

    private final StockTechDailyService stockTechDailyService;

    private final ConceptDelayService conceptDelayService;

    private final DragonDeptService dragonDeptService;

    private final DragonStockService dragonStockService;

    private final DragonStockDetailService dragonStockDetailService;

    private final SystemLogService systemLogService;


    @Scheduled(cron = "0 03,33 * * * ?")
    void systemInfo() {
        LinkedHashMap map = (LinkedHashMap) OSUtils.getSystemInfo().get("JVM");
        log.info(" --> {}:{}", "TotalMemory", map.get("TotalMemory"));
        log.info(" --> {}:{}", "MaxMemory", map.get("MaxMemory"));
        log.info(" --> {}:{}", "FreeMemory", map.get("FreeMemory"));
        log.info(" --> {}:{}", "RealUsage", map.get("RealUsage"));
        systemLogService.printSystemLogs(LocalDate.now());
    }

    /**************************** 股票行情 ***********************************/

    /**
     * 股票Kline分时数据 每分钟更新（只更新股票池、自选股票、持仓股票）
     */
    @Scheduled(cron = "0 0/1 9-12,13-15 ? * 1-5")
    void syncStockCalcKlineIndicators() {
        if (isTradeTime() && LocalTime.now().isAfter(MORNING_0945)) {                   // 09:45开始同步，k线指标才能满足数量15
            StopWatch watch = new StopWatch();
            log.info(" --> 同步股票K线行情【stock_kline_minute】开始 ");
            watch.start();
            int size = stockKlineMinuteService.syncStockKlineMinute();
            watch.stop();
            systemLogService.plusSystemLog("stock_kline_minute", size, watch.getLastTaskTimeMillis());
            log.info(" --> 同步股票K线行情【stock_kline_minute】结束: {} {}ms", size, watch.getLastTaskTimeMillis());

            log.info(" --> 计算股票K线指标【stock_tech_minute】开始 ");
            watch.start();
            size = stockTechMinuteService.syncStockTechMinute();
            watch.stop();
            systemLogService.plusSystemLog("stock_tech_minute", size, watch.getLastTaskTimeMillis());
            log.info(" --> 计算股票K线指标【stock_tech_minute】结束: {} {}ms", size, watch.getLastTaskTimeMillis());
        }
    }

    /**
     * 股票日线实时行情数据  每10分钟更新（更新所有A股股票，不包含688、920、ST，计算日线指标）
     */
//    @Scheduled(cron = "0 42/30  9-11 ? * 1-5 ")       // → 触发时间：09:42, 10:12, 10:42, 11:12, 11:42
//    @Scheduled(cron = "0 12/30 13-15 ? * 1-5 ")       // → 触发时间：13:12, 13:42, 14:12, 14:42, 15:12
    @Scheduled(cron = "0 0/10 9-12,13-15 ? * 1-5 ")
    void syncStockKlineDailyList() {
        if (isTradeTime()) {
            Threads.sleep(20_000);
            syncStockKlineDailyAndCalcIndicator();
        }
    }

    @Scheduled(cron = "0 2 15 ? * 1-5 ")
    void syncStockKlineDailyList2() {
        if (isTradeDate()) {
            syncStockKlineDailyAndCalcIndicator();
        }
    }

    private void syncStockKlineDailyAndCalcIndicator() {
        String tradeDate = getTradeDateStr();
        log.info(" --> 同步股票日线行情及股票池筛选【stock_kline_daily】开始");
        StopWatch watch = new StopWatch();
        watch.start();
        List<StockKlineDaily> list = stockKlineDailyService.syncStockKlineDailyList(tradeDate);      // 全量同步股票日线实时行情
        watch.stop();
        systemLogService.saveSystemLog("stock_kline_daily", list.size(), watch.getLastTaskTimeMillis());
        log.info(" --> 同步股票日线行情及股票池筛选【stock_kline_daily】结束：{} {}ms", list, watch.getLastTaskTimeMillis());
        // 计算日线指标
        for (StockKlineDaily stock : list) {
            stockTechDailyService.calcStockTechDaily(stock.getStockCode(), tradeDate);
        }
    }

    /**
     * 股票池策略精选
     * 根据基本面、隔夜原则、技术指标初步选入股票池
     */
    @Scheduled(cron = "0 32 14 ? * 1-5 ")
    void syncStockPool() {
        if (isTradeDate()) {
            log.info(" --> 执行股票池策略精选【stock_pool】开始 ");
            StopWatch watch = new StopWatch();
            watch.start();
            int size = stockPoolService.execStockPoolSelection(getTradeDateStr());
            watch.stop();
            systemLogService.saveSystemLog("stock_pool", size, watch.getLastTaskTimeMillis());
            log.info(" --> 执行股票池策略精选【stock_pool】结束：{} {}ms", size, watch.getLastTaskTimeMillis());
        }
    }


    /**
     * 股票基本信息及所属概念  每天凌晨5点更新一次（更新所有A股股票，不包含688、920、ST）
     * 1. 更新股票基本信息，包括增量新股
     * 2. 更新个股所属概念
     * 3. 维护概念名称表
     */
    @Scheduled(cron = "0 0 5 ? * 1-5 ")
    void syncStockInfo() {
        if (isTradeDate()) {
            log.info(" --> 同步股票基本信息【stock_info】开始");
            StopWatch watch = new StopWatch();
            watch.start();
            int size = stockInfoService.syncStockInfoAll();
            watch.stop();
            systemLogService.saveSystemLog("stock_info", size, watch.getLastTaskTimeMillis());
            log.info(" --> 同步股票基本信息【stock_info】结束 {} {}ms", size, watch.getLastTaskTimeMillis());
        }
    }

    /**************************** 概念板块 ***********************************/

    /**
     * 概念排名  每5分钟更新（更新涨跌幅top100）
     */
    @Scheduled(cron = "0 0/5 9-12,13-15 ? * 1-5 ")
    void syncConceptDaily() {
        if (isTradeTime()) {
            Threads.sleep(10_000);
            syncConceptTradeList();
        }
    }

    @Scheduled(cron = "0 3 15 ? * 1-5")
    void syncConceptDaily2() {
        if (isTradeDate()) {
            Threads.sleep(10_000);
            syncConceptTradeList();
        }
    }

    private void syncConceptTradeList() {
        log.info(" --> 同步概念板块【concept_daily】开始 top100");
        StopWatch watch = new StopWatch();
        watch.start();
        int size = conceptDelayService.syncConceptTradeList(100);
        watch.stop();
        systemLogService.saveSystemLog("stock_info", 100, watch.getLastTaskTimeMillis());
        log.info(" --> 同步概念板块【concept_daily】结束 top100 {} {}ms", size, watch.getLastTaskTimeMillis());
    }

    /**************************** 龙虎榜 ***********************************/

    /**
     * 1. 龙虎榜每日活跃营业部列表
     * 2. 龙虎榜个股买卖数据和个股前5买卖详情
     */
    @Scheduled(cron = "0 10,20,30 17 ? * 1-5 ")
    void syncDragonDeptList() {
        if (isTradeDate()) {
            log.info(" --> 同步龙虎榜【dragon_dept】开始");
            StopWatch watch = new StopWatch();
            Long count = dragonDeptService.countDragonDept();
            if (count < 10) {
                watch.start();
                int size = dragonDeptService.syncDragonDeptList(getTradeDateStr());
                watch.stop();
                systemLogService.saveSystemLog("dragon_dept", size, watch.getLastTaskTimeMillis());
                log.info(" --> 同步龙虎榜【dragon_dept】结束: {} {}ms", size, watch.getLastTaskTimeMillis());
            } else {
                log.info(" --> 同步龙虎榜【dragon_dept】重复同步 count:{}", count);
            }

            Threads.sleep(30_000);
            log.info(" --> 同步龙虎榜【dragon_stock】开始");
            count = dragonStockService.countDragonStock();
            if (count < 10) {
                watch.start();
                List<DragonStock> list = dragonStockService.syncDragonStockList(getTradeDateStr());
                watch.stop();
                systemLogService.saveSystemLog("dragon_stock", list.size(), watch.getLastTaskTimeMillis());
                log.info(" --> 同步龙虎榜【dragon_stock】结束: {} {}ms", list.size(), watch.getLastTaskTimeMillis());

                log.info(" --> 同步龙虎榜详情【dragon_stock_detail】开始");
                watch.start();
                int size = dragonStockDetailService.syncDragonStockDetailList(list);
                watch.stop();
                systemLogService.saveSystemLog("dragon_stock_detail", size, watch.getLastTaskTimeMillis());
                log.info(" --> 同步龙虎榜详情【dragon_stock_detail】结束: {} {}ms", size, watch.getLastTaskTimeMillis());
            } else {
                log.info(" --> 同步龙虎榜【dragon_stock】重复同步 count:{}", count);
            }
        }
    }


}
