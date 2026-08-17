package com.neuralbase.news;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiKeywordFilterTest {

    @Test
    void englishWordBoundaryMatches() {
        assertThat(AiKeywordFilter.isAiRelated("AI agent 框架")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("OpenAI 发布新模型")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("Machine Learning paper")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("LLMs 评测")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("Fine-tuning 指南")).isTrue();
    }

    @Test
    void chineseSubstringMatches() {
        assertThat(AiKeywordFilter.isAiRelated("大模型评测")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("多模态对齐")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("智能体架构")).isTrue();
    }

    @Test
    void nonAiTitlesRejected() {
        // "airfoil" 以 "ai" 开头但非词边界,不得误判为 AI
        assertThat(AiKeywordFilter.isAiRelated("Airfoil")).isFalse();
        assertThat(AiKeywordFilter.isAiRelated("维护笔记")).isFalse();
        assertThat(AiKeywordFilter.isAiRelated("如何保养汽车")).isFalse();
        assertThat(AiKeywordFilter.isAiRelated("Rust 异步运行时")).isFalse();
    }

    @Test
    void blankOrNullRejected() {
        assertThat(AiKeywordFilter.isAiRelated(null)).isFalse();
        assertThat(AiKeywordFilter.isAiRelated("  ")).isFalse();
        assertThat(AiKeywordFilter.isAiRelated("")).isFalse();
    }

    @Test
    void mixedCaseMatches() {
        assertThat(AiKeywordFilter.isAiRelated("TRAINING a GPT From Scratch")).isTrue();
        assertThat(AiKeywordFilter.isAiRelated("chatbot architecture")).isTrue();
    }
}
