package com.deepthinking.strategy.signal;

import lombok.Builder;
import lombok.Data;

/**
 * 指标共振信号
 */
@Data
@Builder
public class ResonanceSignal {

    private double buyScore;
    private String buyReason;
    private double sellScore;
    private String sellReason;

}
