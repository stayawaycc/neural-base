package com.neuralbase.news;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** AI 主题判定:英文按词边界匹配(避免 "Airfoil" 误判),中文按子串匹配。入库前兜底过滤用。 */
public final class AiKeywordFilter {

    private static final Pattern EN = Pattern.compile(
        "\\b(ai|llms?|gpts?|llama|claude|gemini|deepseek|mistral|qwen|openai|anthropic|chatgpt|"
        + "copilots?|transformers?|diffusion|neural|machine learning|deep learning|rag|embeddings?|"
        + "fine-tun(ing|ed)?|multimodal|agents?|chatbots?|prompt|pytorch|tensorflow|langchain|"
        + "inference|vision|asr|tts)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final List<String> ZH = List.of(
        "人工智能", "大模型", "机器学习", "深度学习", "神经网络", "智能体",
        "多模态", "生成式", "提示词", "微调", "具身智能", "文生图", "文生视频");

    public static boolean isAiRelated(String title) {
        if (title == null || title.isBlank()) return false;
        String t = title.strip();
        if (EN.matcher(t).find()) return true;
        String lower = t.toLowerCase(Locale.ROOT);
        return ZH.stream().anyMatch(lower::contains);
    }

    private AiKeywordFilter() {
    }
}
