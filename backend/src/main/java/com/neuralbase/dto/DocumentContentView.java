package com.neuralbase.dto;

/** 文档阅读器「取内容」接口返回体(规格 4.3)。 */
public record DocumentContentView(Long id, String title, String fileType, String content) {
}
