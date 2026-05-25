package com.aiops.alert.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * aiops.* 配置项映射。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aiops")
public class AiopsProperties {

    private Llm llm = new Llm();
    private Engine engine = new Engine();
    private Simulator simulator = new Simulator();
    private Incident incident = new Incident();

    @Data
    public static class Llm {
        private boolean enabled = true;
        private String baseUrl = "https://api.openai.com/v1";
        private String apiKey = "";
        private String defaultModel = "gpt-4o-mini";
        private int timeoutSeconds = 60;
    }

    @Data
    public static class Engine {
        private boolean enabled = true;
        private int intervalSeconds = 30;
    }

    @Data
    public static class Simulator {
        private boolean enabled = true;
        private int intervalSeconds = 15;
    }

    @Data
    public static class Incident {
        private int mergeWindowMinutes = 30;
    }
}
