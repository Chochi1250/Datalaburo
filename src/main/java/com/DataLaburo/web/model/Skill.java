package com.DataLaburo.web.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.text.Normalizer;
import java.util.Locale;

@Entity
@Table(
		name = "skills",
		indexes = {
				@Index(name = "idx_skills_name_normalized", columnList = "name_normalized", unique = true)
		}
)
public class Skill {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 128)
	private String name;

	@Column(name = "name_normalized", nullable = false, length = 128, unique = true)
	private String nameNormalized;

	@Column(nullable = false)
	private boolean enabled = true;

	@PrePersist
	@PreUpdate
	void normalize() {
		this.nameNormalized = normalizeName(this.name);
	}

	public static String normalizeName(String s) {
		if (s == null) {
			return "";
		}
		String trimmed = s.trim().toLowerCase(Locale.ROOT);
		String noAccents = Normalizer.normalize(trimmed, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		// Keep letters/digits and a small set of skill-relevant symbols; everything else becomes spaces.
		String cleaned = noAccents.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}#+./\\-]+", " ");
		return cleaned.trim().replaceAll("\\s+", " ");
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNameNormalized() {
		return nameNormalized;
	}

	public void setNameNormalized(String nameNormalized) {
		this.nameNormalized = nameNormalized;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}

