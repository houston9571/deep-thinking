package com.deepthinking.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.deepthinking.common.constant.Constants.ID;

/**
 *
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@TableName("system_log")
public class SystemLog extends BaseEntity {

    @TableId(value = ID, type = IdType.AUTO)
    private Integer id;

    private String name;

    private Integer count;

    private Long millis;

    private String remark;

    private LocalDate tradeDate;


}
