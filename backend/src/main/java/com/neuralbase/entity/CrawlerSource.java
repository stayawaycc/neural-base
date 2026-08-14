package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("crawler_source")
public class CrawlerSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String url;
    private String type;
    private String selector;
    private String category;
    private Boolean enabled;
    private Integer freqMin;
    private OffsetDateTime lastCrawl;
}
