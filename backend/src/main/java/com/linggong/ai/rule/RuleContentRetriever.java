package com.linggong.ai.rule;

import dev.langchain4j.rag.content.retriever.ContentRetriever;

/**
 * 平台规则知识库检索器（可插拔）。
 *
 * <p>把 langchain4j 的 {@link ContentRetriever} 收口成平台自己的接口：@AiService 只认
 * {@code ruleBm25Retriever} 这个 bean 名，换检索实现（比如二期上向量 embedding）时
 * 只换一个实现类，问答链路不动。
 */
public interface RuleContentRetriever extends ContentRetriever {
}
