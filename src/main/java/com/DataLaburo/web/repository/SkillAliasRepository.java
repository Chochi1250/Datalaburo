package com.DataLaburo.web.repository;

import com.DataLaburo.web.model.SkillAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, Long> {
	@Query("select sa from SkillAlias sa join fetch sa.skill s where s.enabled = true")
	List<SkillAlias> findAllWithEnabledSkill();

	boolean existsByAliasNormalized(String aliasNormalized);
}
