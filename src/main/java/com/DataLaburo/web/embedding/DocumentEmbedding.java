package com.DataLaburo.web.embedding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "document_embeddings",
        indexes = {
                @Index(name = "idx_document_embeddings_owner", columnList = "owner_type, owner_id"),
                @Index(name = "idx_document_embeddings_status", columnList = "status"),
                @Index(name = "idx_document_embeddings_source_text_hash", columnList = "source_text_hash"),
                @Index(name = "idx_document_embeddings_model_dimensions", columnList = "embedding_model, embedding_dimensions")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_document_embeddings_owner_section_model",
                        columnNames = {"owner_type", "owner_id", "section_type", "embedding_model", "normalizer_version"}
                )
        }
)
public class DocumentEmbedding {
    public static final String DEFAULT_EMBEDDING_MODEL = "BAAI/bge-m3";
    public static final int DEFAULT_EMBEDDING_DIMENSIONS = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 32)
    private DocumentEmbeddingOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section_type", nullable = false, length = 64)
    private DocumentEmbeddingSectionType sectionType;

    @Column(name = "source_text_hash", nullable = false, length = 128)
    private String sourceTextHash;

    @Column(name = "embedding_model", nullable = false, length = 128)
    private String embeddingModel = DEFAULT_EMBEDDING_MODEL;

    @Column(name = "embedding_dimensions", nullable = false)
    private Integer embeddingDimensions = DEFAULT_EMBEDDING_DIMENSIONS;

    @Column(name = "normalizer_version", nullable = false, length = 64)
    private String normalizerVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentEmbeddingStatus status = DocumentEmbeddingStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_embedded_at")
    private Instant lastEmbeddedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DocumentEmbeddingOwnerType getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(DocumentEmbeddingOwnerType ownerType) {
        this.ownerType = ownerType;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public DocumentEmbeddingSectionType getSectionType() {
        return sectionType;
    }

    public void setSectionType(DocumentEmbeddingSectionType sectionType) {
        this.sectionType = sectionType;
    }

    public String getSourceTextHash() {
        return sourceTextHash;
    }

    public void setSourceTextHash(String sourceTextHash) {
        this.sourceTextHash = sourceTextHash;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(Integer embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getNormalizerVersion() {
        return normalizerVersion;
    }

    public void setNormalizerVersion(String normalizerVersion) {
        this.normalizerVersion = normalizerVersion;
    }

    public DocumentEmbeddingStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentEmbeddingStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastEmbeddedAt() {
        return lastEmbeddedAt;
    }

    public void setLastEmbeddedAt(Instant lastEmbeddedAt) {
        this.lastEmbeddedAt = lastEmbeddedAt;
    }
}
