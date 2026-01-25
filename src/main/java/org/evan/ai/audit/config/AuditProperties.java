package org.evan.ai.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the Audit Assistant application.
 */
@ConfigurationProperties(prefix = "audit")
public class AuditProperties {

    private KnowledgeBase knowledgeBase = new KnowledgeBase();
    private DocumentConfig document = new DocumentConfig();

    public KnowledgeBase getKnowledgeBase() {
        return knowledgeBase;
    }

    public void setKnowledgeBase(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    public DocumentConfig getDocument() {
        return document;
    }

    public void setDocument(DocumentConfig document) {
        this.document = document;
    }

    /**
     * Knowledge base configuration.
     */
    public static class KnowledgeBase {
        private List<String> documents = new ArrayList<>();

        public List<String> getDocuments() {
            return documents;
        }

        public void setDocuments(List<String> documents) {
            this.documents = documents;
        }
    }

    /**
     * Document parsing configuration.
     */
    public static class DocumentConfig {
        private List<String> questionMarkers = new ArrayList<>();
        private List<String> answerMarkers = new ArrayList<>();

        public List<String> getQuestionMarkers() {
            return questionMarkers;
        }

        public void setQuestionMarkers(List<String> questionMarkers) {
            this.questionMarkers = questionMarkers;
        }

        public List<String> getAnswerMarkers() {
            return answerMarkers;
        }

        public void setAnswerMarkers(List<String> answerMarkers) {
            this.answerMarkers = answerMarkers;
        }
    }
}
