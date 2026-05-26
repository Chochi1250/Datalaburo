package com.DataLaburo.web.embedding;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, Long> {
    Optional<DocumentEmbedding> findByOwnerTypeAndOwnerIdAndSectionTypeAndEmbeddingModelAndNormalizerVersion(
            DocumentEmbeddingOwnerType ownerType,
            Long ownerId,
            DocumentEmbeddingSectionType sectionType,
            String embeddingModel,
            String normalizerVersion
    );

    @Query("select d.status as status, count(d) as total from DocumentEmbedding d group by d.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        DocumentEmbeddingStatus getStatus();

        long getTotal();
    }
}
