package com.DataLaburo.web.repository;

import com.DataLaburo.web.domain.JobOffer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
}

