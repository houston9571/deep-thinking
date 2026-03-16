package com.deepthinking.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alicp.jetcache.anno.CacheUpdate;
import com.alicp.jetcache.anno.Cached;
import com.deepthinking.client.EastMoneyStockApi;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.common.enums.DateFormatEnum;
import com.deepthinking.common.utils.DateUtils;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.StockKlineDaily;
import com.deepthinking.mysql.mapper.StockKlineDailyMapper;
import com.deepthinking.service.StockKlineDailyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.deepthinking.common.constant.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockKlineDailyServiceImpl extends MybatisBaseServiceImpl<StockKlineDailyMapper, StockKlineDaily> implements StockKlineDailyService {

    private static final String CACHE_KEY = "StockKlineDaily:";

    private final StockKlineDailyMapper stockKlineDailyMapper;

    private final EastMoneyStockApi eastMoneyStockApi;


    @Cached(name = CACHE_KEY, key = "#stockCode", expire = MINS_30)
    public StockKlineDaily getStockKlineDaily(String stockCode) {
        return stockKlineDailyMapper.getStockKlineDailyLimit(stockCode, 1).getFirst();
    }

    @Cached(name = CACHE_KEY, key = "#stockCode+'_'+#tradeDate", expire = DAYS_1)
    public List<StockKlineDaily> getStockKlineDailyLimit(String stockCode, String tradeDate, int limit) {
        return stockKlineDailyMapper.getStockKlineDailyLimit(stockCode, limit);
    }


    @Cached(name = CACHE_KEY, key = "#tradeDate", expire = DAYS_1)
    public List<StockKlineDaily> getStockKlineDailyList(String tradeDate) {
        log.info(">>>>>getStockKlineDailyList 全量加载日线数据，未击中缓存");
        return queryList(StockKlineDaily.builder().tradeDate(DateUtils.parseLocalDate(tradeDate, DateFormatEnum.DATE)).build());
    }

    /**
     * 获取股票实时交易列表，排除688 920 ST
     */
//    @CacheInvalidate(name = CACHE_KEY, key = "#tradeDate")
    @CacheUpdate(name = CACHE_KEY, key = "#tradeDate", value = "result")
    public List<StockKlineDaily> syncStockKlineDailyList(String tradeDate) {
        String fields = "f1,f2,f3,f4,f5,f6,f7,f8,f9,f10,f12,f14,f15,f16,f17,f18,f20,f21,f23,f24,f34,f35,f37,f40,f41,f45,f46,f48,f49,f57,f64,f65,f66,f69,f70,f71,f72,f75,f76,f77,f78,f81,f82,f83,f84,f87,f109,f129,f297";
        int total = 0, pageNum = 0, pageSize = 100;
        List<StockKlineDaily> list = new ArrayList<>(5000);
        while (true) {
            JSONObject json = eastMoneyStockApi.getStockTradeList(fields, ++pageNum, pageSize, System.currentTimeMillis());
            JSONObject data = json.getJSONObject(LABEL_DATA);
            if (Objects.isNull(data) || !data.containsKey("diff")) {
                break;
            }
            JSONArray array = data.getJSONArray("diff");
            total = data.getInteger(LABEL_TOTAL);
            log.info(">>>>>syncStockTradeList pageNum:{} data:{} total:{}", pageNum, array.size(), total);
            for (int i = 0; i < array.size(); i++) {
                try {
                    StockKlineDaily daily = JSONObject.parseObject(array.getString(i), StockKlineDaily.class);
                    if (MarketType.contains(daily.getStockCode(), daily.getStockName())) {
                        if (ObjectUtil.isNotEmpty(daily.getClose())) {      // 昨收*涨跌
                            daily.setLimitUp(daily.getClose().multiply(MarketType.getChangeLimit(daily.getStockCode())));
                            daily.setLimitDown(daily.getClose().multiply(MarketType.getChangeLimit(daily.getStockCode())));
                        }
                        daily.setBuyVolumeRatio(BigDecimal.valueOf(daily.getBuyVolume() * 100).divide(BigDecimal.valueOf(daily.getBuyVolume() + daily.getSellVolume()), SCALE2, ROUND_MODE));
                        daily.setMainIn(daily.getSuperLargeIn() + daily.getLargeIn());
                        daily.setMainOut(daily.getSuperLargeOut() + daily.getLargeOut());
                        daily.setMainNetIn(daily.getSuperLargeNetIn() + daily.getLargeNetIn());
                        daily.setMainNetRatio(daily.getSuperLargeNetRatio().add(daily.getLargeNetRatio()));
                        daily.setRetailIn(daily.getMediumIn() + daily.getSmallIn());
                        daily.setRetailOut(daily.getMediumOut() + daily.getSmallOut());
                        daily.setRetailNetIn(daily.getMediumNetIn() + daily.getSmallNetIn());
                        daily.setRetailNetRatio(daily.getMediumNetRatio().add(daily.getSmallNetRatio()));
                        list.add(daily);
                    }
                } catch (Exception e) {
                    log.error(">>>>>syncStockTradeList JSONObject.parseObject error. {} {}", array.getString(i), e.getMessage());
                }
            }
            if (array.size() < pageSize) {
                break;
            }
        }
        try {
            log.info(">>>>>syncStockTradeList read finished total:{} list:{} ", total, list.size());
            if (!CollectionUtils.isEmpty(list)) {
                saveOrUpdateBatch(list, new String[]{"stock_code", "trade_date"});
            }
        } catch (Exception e) {
            log.error(">>>>>syncStockTradeList saveBatch error. {}", e.getMessage());
        }
        return list;
    }

}

