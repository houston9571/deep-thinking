package com.deepthinking.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.deepthinking.client.EastMoneyH5Api;
import com.deepthinking.client.EastMoneyStockApi;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.common.constant.StockCodeUtils;
import com.deepthinking.common.enums.DateFormatEnum;
import com.deepthinking.common.utils.DateUtils;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockKlineMinute;
import com.deepthinking.mysql.entity.StockPool;
import com.deepthinking.mysql.mapper.StockKlineMinuteMapper;
import com.deepthinking.service.StockKlineMinuteService;
import com.deepthinking.service.StockPoolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.hutool.core.text.StrPool.COMMA;
import static com.deepthinking.common.constant.Constants.LABEL_DATA;
import static com.deepthinking.common.constant.MarketType.getTradeDateStr;
import static com.deepthinking.common.constant.StockConstants.KLINE_1MIN;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockKlineMinuteServiceImpl extends MybatisBaseServiceImpl<StockKlineMinuteMapper, StockKlineMinute> implements StockKlineMinuteService {

    private final StockKlineMinuteMapper stockKlineMinuteMapper;

    private final EastMoneyStockApi eastMoneyStockApi;

    private final EastMoneyH5Api eastMoneyH5Api;

    private final StockPoolService stockPoolService;

    /**
     * 更新股票池、自选股票、持仓股票 1分钟
     */
    public Integer syncStockKlineMinute() {
        List<StockPool> stocks = stockPoolService.queryStocks(getTradeDateStr());
        stocks.forEach(stock -> {
            String stockCode = stock.getStockCode();
            if (MarketType.contains(stockCode, stock.getStockName())) {
                syncStockKlineMinute(stockCode);
            }
        });
        return stocks.size();
    }

    /**
     * 股票实时交易行情和资金流向 每1分钟
     */
    public void syncStockKlineMinute(String stockCode) {
        JSONObject kline = eastMoneyStockApi.getStockTradeRealtime(stockCode, MarketType.getMarketCode(stockCode), SystemClock.now());
        StockKlineMinute stockKlineMinute = JSONObject.parseObject(kline.getString(LABEL_DATA), StockKlineMinute.class);
//        String transactionDate = kline.getJSONObject(LABEL_DATA).getJSONArray("f80").getJSONObject(1).getString("e");

        JSONObject flow = eastMoneyStockApi.getFundsFlowLines(stockCode, MarketType.getMarketCode(stockCode), KLINE_1MIN, 1);
        JSONObject data = flow.getJSONObject(LABEL_DATA);
        if (ObjectUtil.isEmpty(data) || !data.containsKey("klines")) {
            log.error("未获取到页面信息 syncStockKlineMinute: {}", stockCode);
            return;
        }

        JSONArray lines = data.getJSONArray("klines");
        if (CollectionUtil.isEmpty(lines)) {
            log.error("没有数据，可能停盘 syncStockKlineMinute: {}", stockCode);
            return;
        }
        String[] line = lines.getString(0).split(COMMA);
        String[] t = line[0].split("\\s+");
        stockKlineMinute.setTradeDate(DateUtils.parseLocalDate(t[0], DateFormatEnum.DATE));
        stockKlineMinute.setTradeTime(DateUtils.parseLocalTime(t[1] + ":00", DateFormatEnum.TIME));
        stockKlineMinute.setMainNetIn(line[1]);
        stockKlineMinute.setSmallNetIn(line[2]);
        stockKlineMinute.setMediumNetIn(line[3]);
        stockKlineMinute.setLargeNetIn(line[4]);
        stockKlineMinute.setSuperLargeNetIn(line[5]);
        saveOrUpdate(stockKlineMinute, new String[]{"stock_code", "trade_date", "trade_time"});
    }


    public Result<JSONObject> getFirstRequest2Data(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("fc", StockCodeUtils.buildSecId(code));
        eastMoneyH5Api.getFirstRequest2Data(params);
        return Result.success();
    }

}
