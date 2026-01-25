package org.evan.ai.audit.agent;
import java.util.Map;
/**
 * Result of an agent execution.
 */
public record AgentResult(
        boolean success,
        String message,
        Map<String, Object> data
) {
    public static AgentResult success(String message) {
        return new AgentResult(true, message, Map.of());
    }
    public static AgentResult success(String message, Map<String, Object> data) {
        return new AgentResult(true, message, data);
    }
    public static AgentResult failure(String message) {
        return new AgentResult(false, message, Map.of());
    }
}
