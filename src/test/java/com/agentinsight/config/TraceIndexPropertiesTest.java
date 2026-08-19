package com.agentinsight.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIndexPropertiesTest {

    @Test
    void defaultsToTheExistingTraceIndex() {
        assertThat(new TraceIndexProperties().getTraceIndex()).isEqualTo("rag-traces");
    }

    @Test
    void acceptsADeploymentSpecificTraceIndex() {
        TraceIndexProperties properties = new TraceIndexProperties();

        properties.setTraceIndex("production-agent-traces");

        assertThat(properties.getTraceIndex()).isEqualTo("production-agent-traces");
    }
}
