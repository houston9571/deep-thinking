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


    public void saveSystemLog(String name, int count, long millis) {
        SystemLog systemLog = SystemLog.builder().tradeDate(LocalDate.now()).name(name).count(count).millis(millis).build();
        saveOrUpdate(systemLog, new String[]{"trade_date", "name"});
    }

    public void plusSystemLog(String name, int count, long millis) {
        SystemLog systemLog = findOne(SystemLog.builder().tradeDate(LocalDate.now()).name(name).build());
        if (ObjectUtil.isEmpty(systemLog)) {
            saveSystemLog(name, count, millis);
        } else {
            systemLog.setCount(systemLog.getCount() + count);
            saveOrUpdate(systemLog, new String[]{"trade_date", "name"});
        }
    }

    public void printSystemLogs(LocalDate tradeDate) {
        SystemLog systemLog = SystemLog.builder().tradeDate(tradeDate).build();
        List<SystemLog> list = queryList(systemLog);
        for (SystemLog gg : list) {
            log.info("---> {} {} : {} {}ms", tradeDate, gg.getName(), gg.getCount(), gg.getMillis());
        }
    }
}
