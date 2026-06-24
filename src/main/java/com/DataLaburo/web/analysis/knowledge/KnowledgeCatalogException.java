package com.DataLaburo.web.analysis.knowledge;

public class KnowledgeCatalogException extends IllegalStateException {
    public KnowledgeCatalogException(String message) {
        super(message);
    }

    public KnowledgeCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
