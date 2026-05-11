package com.DataLaburo.web.repository;

import com.DataLaburo.web.model.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {
    List<CandidateProfile> findAllByOrderByUpdatedAtDescIdDesc();
}
