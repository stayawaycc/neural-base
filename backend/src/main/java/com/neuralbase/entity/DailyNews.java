package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("daily_news")
public class DailyNews {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private String url;
    private String title;
    private String category;
    private String summary;
    private Boolean isRead;
    private OffsetDateTime createdAt;

    /** 非持久化:列表查询时由服务层填充来源名,供前端展示。 */
    @TableField(exist = false)
    private String sourceName;
}
