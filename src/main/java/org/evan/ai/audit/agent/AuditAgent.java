package org.evan.ai.audit.agent;

/**
 * Base interface for all audit agents.
 */
public interface AuditAgent {

    /**
     * Get the name of the agent.
     */
    String getName();

    /**
     * Get the description of the agent.
     */
    String getDescription();

    /**
     * Execute the agent's task.
     */
    AgentResult execute(AgentContext context);

    /**
     * Check if this agent can handle the given context.
     */
    default boolean canHandle(AgentContext context) {
        return true;
    }
}
