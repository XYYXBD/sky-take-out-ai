package com.sky.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RetrievalTool {
    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Tool("当用户询问菜单、菜品、食材、口味、餐厅信息等知识库相关问题时调用此工具进行检索")
    public String retrieveFromKnowledgeBase(String question) {
        // 构建检索器
        ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(0.75)
                .maxResults(3)
                .build();

        // 执行检索
        List<Content> contents = retriever.retrieve(Query.from(question));

        if (contents.isEmpty()) {
            return "知识库中未找到相关信息";
        }

        // 拼接检索结果返回给模型
        return contents.stream()
                .map(c -> c.textSegment().text())
                .collect(Collectors.joining("\n---\n"));
    }
}
