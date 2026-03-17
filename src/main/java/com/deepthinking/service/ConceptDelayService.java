package com.deepthinking.service;

import com.deepthinking.mysql.MybatisBaseService;
import com.deepthinking.mysql.entity.ConceptDelay;

import java.util.List;

public interface ConceptDelayService extends MybatisBaseService<ConceptDelay> {


     List<List<ConceptDelay>> queryConceptTradeList(int orderBy, int days, int top);

     int syncConceptTradeList(int top);




}
