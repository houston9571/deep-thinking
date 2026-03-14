package com.deepthinking.strategy.signal;

import com.deepthinking.strategy.StrategyUtils;
import lombok.Builder;
import lombok.Data;

/**
 * 量价关系信号
 */
@Data
@Builder
public class VolumeAndPriceSignal {

    private StrategyUtils.SignalType signalType;
    private StrategyUtils.SignalLevel signalLevel;
    private String signalResult;
}
