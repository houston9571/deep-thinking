package com.deepthinking.rest;

import com.deepthinking.ext.base.PageInfo;
import com.deepthinking.ext.base.PageResult;
import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.service.StockInfoService;
import com.deepthinking.service.StockKlineDailyService;
import com.deepthinking.service.StockKlineMinuteService;
import com.deepthinking.service.StockPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.dtflys.forest.backend.ContentType.APPLICATION_JSON;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "stock", produces = APPLICATION_JSON)
public class StockRest {

    private final StockInfoService stockInfoService;

    private final StockPoolService stockPoolService;

    private final StockKlineDailyService stockKlineDailyService;

    private final StockKlineMinuteService stockKlineMinuteService;


    @GetMapping("daily/{stockCode}")
    public StockKlineDaily getStockKlineDaily(@PathVariable String stockCode) {
        return stockKlineDailyService.getStockKlineDaily(stockCode);
    }


    /**
     * 同步更新股票基本信息，所属概念
     */
    @PostMapping("")
    public PageResult<StockPool> stockList(@RequestBody PageInfo<StockInfo> pageInfo) {
        pageInfo.startPage();
        List<StockPool> list = stockPoolService.queryStockPool();
        return PageResult.success(list);
    }

    @PostMapping("setSort")
    public void setSort() {

    }
}
