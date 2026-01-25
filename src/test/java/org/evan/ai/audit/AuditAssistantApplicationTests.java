package org.evan.ai.audit;

import org.junit.jupiter.api.Test;

/**
 * Basic tests for the Audit Assistant application.
 * Note: Full context load tests are disabled as they require AI service configuration.
 */
class AuditAssistantApplicationTests {

    @Test
    void applicationClassExists() {
        // Verify the main application class exists
        AuditAssistantApplication app = new AuditAssistantApplication();
        assert app != null;
    }
}
