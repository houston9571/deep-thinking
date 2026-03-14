package com.deepthinking.strategy.signal;

import com.deepthinking.strategy.StrategyUtils;
import lombok.Builder;
import lombok.Data;
/**
 * 指标背离信号
 */
@Data
@Builder
public class DivergenceSignal {

    private StrategyUtils.DivergenceType divergenceType;
    private Short divergenceStrength;
    private String divergenceResult;
}
