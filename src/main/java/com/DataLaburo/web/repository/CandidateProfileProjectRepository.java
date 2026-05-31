package com.DataLaburo.web.repository;

import com.DataLaburo.web.model.CandidateProfileProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CandidateProfileProjectRepository extends JpaRepository<CandidateProfileProject, Long> {
    List<CandidateProfileProject> findByCandidateProfileIdOrderByCreatedAtDescIdDesc(Long candidateProfileId);

    Optional<CandidateProfileProject> findByIdAndCandidateProfileId(Long id, Long candidateProfileId);
}
