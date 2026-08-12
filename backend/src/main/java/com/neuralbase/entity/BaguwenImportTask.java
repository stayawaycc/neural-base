package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("baguwen_import_task")
public class BaguwenImportTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String url;
    private String status;    // PENDING / PROCESSING / SUCCESS / FAILED
    private String errorMsg;
    private Long articleId;
    private Integer attempt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
