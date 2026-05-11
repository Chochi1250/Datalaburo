package com.DataLaburo.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
		name = "skill_aliases",
		indexes = {
				@Index(name = "idx_skill_aliases_skill_id", columnList = "skill_id"),
				@Index(name = "idx_skill_aliases_alias_normalized", columnList = "alias_normalized", unique = true)
		}
)
public class SkillAlias {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "skill_id", nullable = false)
	private Skill skill;

	@Column(nullable = false, length = 128)
	private String alias;

	@Column(name = "alias_normalized", nullable = false, length = 128, unique = true)
	private String aliasNormalized;

	@PrePersist
	@PreUpdate
	void normalize() {
		this.aliasNormalized = Skill.normalizeName(this.alias);
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Skill getSkill() {
		return skill;
	}

	public void setSkill(Skill skill) {
		this.skill = skill;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getAliasNormalized() {
		return aliasNormalized;
	}

	public void setAliasNormalized(String aliasNormalized) {
		this.aliasNormalized = aliasNormalized;
	}
}

