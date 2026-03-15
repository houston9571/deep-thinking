package com.deepthinking.service;

import com.deepthinking.mysql.vo.DragonDetailPartner;
import com.deepthinking.mysql.vo.DragonDetailStockKline;

import java.util.List;

public interface DragonStockService {

    List<DragonDetailStockKline> queryDragonStockList();

    List<DragonDetailStockKline> queryDragonStockDetail(String stockCode);

    List<DragonDetailPartner> queryDragonPartnerDetail(String partnerCode);

    Long countDragonStock();

    Integer syncDragonStockList(String date);

}
