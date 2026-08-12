package com.neuralbase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String title;
    private String fileType;
    private String filePath;
    private String sourceUrl;
    private String status;
    private Integer chunkCount;
    private String error;
    private OffsetDateTime createdAt;
}
