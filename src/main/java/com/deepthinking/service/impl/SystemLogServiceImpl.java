package com.deepthinking.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.deepthinking.mysql.MybatisBaseServiceImpl;
import com.deepthinking.mysql.entity.SystemLog;
import com.deepthinking.mysql.mapper.SystemLogMapper;
import com.deepthinking.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl extends MybatisBaseServiceImpl<SystemLogMapper, SystemLog> implements SystemLogService {


    private final SystemLogMapper systemLogMapper;


    public void saveSystemLog(String name, int count) {
        SystemLog systemLog = SystemLog.builder().tradeDate(LocalDate.now()).name(name).count(count).build();
        saveOrUpdate(systemLog, new String[]{"stock_code", "name"});
    }

    public void plusSystemLog(String name, int count) {
        SystemLog systemLog = findOne(SystemLog.builder().tradeDate(LocalDate.now()).name(name).build());
        if (ObjectUtil.isEmpty(systemLog)) {
            systemLog.setCount(count);
            save(systemLog);
        }else {
            systemLog.setCount(systemLog.getCount()+count);
            saveOrUpdate(systemLog, new String[]{"stock_code", "name"});
        }
    }

    public void printSystemLogs(LocalDate tradeDate) {
        SystemLog systemLog = SystemLog.builder().tradeDate(tradeDate).build();
        List<SystemLog> list = queryList(systemLog);
        list.forEach(gg -> log.info("---> {} {} : {}", tradeDate, gg.getName(), gg.getCount()));
    }
}
