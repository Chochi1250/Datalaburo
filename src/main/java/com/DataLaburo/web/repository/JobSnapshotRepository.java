package com.DataLaburo.web.repository;

import com.DataLaburo.web.model.JobSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSnapshotRepository extends JpaRepository<JobSnapshot, Long> {
    Optional<JobSnapshot> findTopByJobIdOrderByCapturedAtDescIdDesc(Long jobId);

    Optional<JobSnapshot> findTopByJobIdAndPostedAtTextOrderByCapturedAtDescIdDesc(Long jobId, String postedAtText);
}
