package com.deepthinking.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alicp.jetcache.anno.Cached;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepthinking.client.EastMoneyStockApi;
import com.deepthinking.common.constant.MarketType;
import com.deepthinking.common.utils.RedisUtils;
import com.deepthinking.ext.base.Result;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.ConceptInfo;
import com.deepthinking.mysql.entity.ConceptStock;
import com.deepthinking.mysql.entity.StockInfo;
import com.deepthinking.mysql.mapper.StockInfoMapper;
import com.deepthinking.service.ConceptInfoService;
import com.deepthinking.service.ConceptStockService;
import com.deepthinking.service.StockInfoService;
import com.deepthinking.sprider.SpriderTemplateParser;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.deepthinking.common.constant.Constants.*;
import static com.deepthinking.common.constant.MarketType.*;
import static com.deepthinking.common.enums.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockInfoServiceImpl extends MybatisBaseServiceImpl<StockInfoMapper, StockInfo> implements StockInfoService {

    private static final String CACHE_KEY = "StockInfo:";

    private final StockInfoMapper stockInfoMapper;

    private final ConceptInfoService conceptInfoService;

    private final ConceptStockService conceptStockService;

    private final EastMoneyStockApi eastMoneyStockApi;


    @Autowired
    SpriderTemplateParser spiderTemplateParser;



    @Cached(name = CACHE_KEY, key = "#stockCode", expire = 7, timeUnit = TimeUnit.DAYS)
    public StockInfo getStockInfo(String stockCode) {
        return findOne(StockInfo.builder().stockCode(stockCode).build());
    }


    public Result<Integer> syncStockInfoAll() {
        List<StockInfo> updates = findAll();
        log.error(">>>>>syncStockInfoAll start. 更新：{}", updates.size());
        for (StockInfo daily : updates) {
            if (MarketType.contains(daily.getStockCode(), daily.getStockName())) {
                Result<StockInfo> result = syncStockInfo(daily.getStockCode());
                if (result.isSuccess()) {
                    syncStockConceptList(daily.getStockCode());
                }
            }
        }
        log.error(">>>>>syncStockInfoAll end. 更新：{}", updates.size());

        List<StockInfo> inserts = stockInfoMapper.queryStockInfoNotIn();
        log.error(">>>>>syncStockInfoAll start. 新增：{}", inserts.size());
        for (StockInfo daily : inserts) {
            if (MarketType.contains(daily.getStockCode(), daily.getStockName())) {
                Result<StockInfo> result = syncStockInfo(daily.getStockCode());
                if (result.isSuccess()) {
                    syncStockConceptList(daily.getStockCode());
                }
            }
        }
        log.error(">>>>>syncStockInfoAll end. 新增：{}", inserts.size());
        return Result.success(inserts.size() + updates.size());
    }


    /**
     * 股票基本信息
     */
    public Result<StockInfo> syncStockInfo(String stockCode) {
        try {
            String tpl = "S01-overview.json";
            List<Map<String, String>[]> factors = spiderTemplateParser.parserAsMap(tpl, stockCodeMap(stockCode));
            if (CollectionUtils.isEmpty(factors)) {
                return Result.fail(NOT_GET_PAGE_ERROR, "tlp:" + tpl);
            }
            Map<String, String>[] maps = factors.getFirst();
            if (ArrayUtils.isEmpty(maps)) {
                return Result.fail(NOT_GET_PAGE_ERROR, "factors:" + JSONObject.toJSONString(factors));
            }

            StockInfo stockInfo = BeanUtil.fillBeanWithMap(maps[0], new StockInfo(), true);
            saveOrUpdate(stockInfo, new String[]{"stock_code"});
            RedisUtils.set(CACHE_KEY + stockInfo.getStockCode(), stockInfo);

            return Result.success(stockInfo);
        } catch (Exception e) {
            log.error(">>>>>getStockInfo error. {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 个股所属概念
     */
    private void syncStockConceptList(String stockCode) {
        try {
            JSONObject json = eastMoneyStockApi.syncStockConcepts(stockCode, getMarket(stockCode));
            JSONObject result = json.getJSONObject(LABEL_RESULT);
            if (ObjectUtil.isEmpty(result) || !result.containsKey(LABEL_DATA)) {
                Result.fail(NOT_GET_PAGE_ERROR, "getStockConceptList result is null");
                return;
            }
            JSONArray data = result.getJSONArray(LABEL_DATA);
            if (CollectionUtils.isEmpty(data)) {
                Result.fail(NOT_GET_PAGE_ERROR, "getStockConceptList data is null");
                return;
            }
            JSONObject d;
            List<ConceptStock> conceptStockList = Lists.newArrayList();
            for (int i = 0; i < data.size(); i++) {
                d = data.getJSONObject(i);
                String conceptCode = d.getString("NEW_BOARD_CODE");
                if (!conceptInfoService.exist(new LambdaQueryWrapper<ConceptInfo>().eq(ConceptInfo::getConceptCode, conceptCode))) {
                    // 添加不存在的概念名称
                    conceptInfoService.save(ConceptInfo.builder().conceptCode(conceptCode).conceptName(d.getString("BOARD_NAME")).type(d.getString("BOARD_TYPE")).level(d.getString("BOARD_LEVEL")).build());
                }
                conceptStockList.add(ConceptStock.builder().stockCode(stockCode).conceptCode(conceptCode).build());
            }
            conceptStockService.delete(new LambdaQueryWrapper<ConceptStock>().eq(ConceptStock::getStockCode, stockCode));
            conceptStockService.saveBatch(conceptStockList);
        } catch (Exception e) {
            log.error(">>>>>getStockConceptList error. {}", e.getMessage());
            Result.fail(e.getMessage());
            return;
        }
        Result.success();
    }


}
