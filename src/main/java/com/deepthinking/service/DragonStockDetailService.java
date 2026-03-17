package com.deepthinking.service;

import com.deepthinking.mysql.entity.DragonStock;
import com.deepthinking.mysql.entity.DragonStockDetail;

import java.time.LocalDate;
import java.util.List;

public interface DragonStockDetailService {


    List<List<DragonStockDetail>> queryDragonStockDetailWithPartner();

    int syncDragonStockDetailList(List<DragonStock> list) ;

}
