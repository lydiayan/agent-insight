package com.agentinsight.repository.es;

import com.agentinsight.entity.es.AgentSpanDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent Span ES Repository
 */
@Repository
public interface AgentSpanRepository extends ElasticsearchRepository<AgentSpanDocument, String> {

    List<AgentSpanDocument> findByTraceId(String traceId);
}
