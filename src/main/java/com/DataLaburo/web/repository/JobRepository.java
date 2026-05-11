package com.DataLaburo.web.repository;

import com.DataLaburo.web.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {
    Optional<Job> findTopBySourceAndExternalJobIdOrderByIdDesc(String source, String externalJobId);

    Optional<Job> findTopBySourceUrlAndTitleAndCompanyOrderByIdDesc(String sourceUrl, String title, String company);

    List<Job> findAllByOrderByCreatedAtDescIdDesc();

    @Query("select count(j) from Job j where j.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    // Avoid trim() on @Lob/CLOB fields (not supported by Hibernate JPQL validation for some dialects).
    @Query("select count(j) from Job j where j.description is not null and j.description <> ''")
    long countWithDescription();

    // Avoid trim() on @Lob/CLOB fields (not supported by Hibernate JPQL validation for some dialects).
    @Query("select count(j) from Job j where j.requirementsText is not null and j.requirementsText <> ''")
    long countWithRequirementsText();
}
