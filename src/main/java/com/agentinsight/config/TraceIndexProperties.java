package com.agentinsight.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@ConfigurationProperties(prefix = "agent-insight.elasticsearch")
public class TraceIndexProperties {

    @NotBlank
    private String traceIndex = "rag-traces";

    public String getTraceIndex() {
        return traceIndex;
    }

    public void setTraceIndex(String traceIndex) {
        this.traceIndex = traceIndex;
    }
}
