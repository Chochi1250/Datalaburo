package com.DataLaburo.web.config;

import com.DataLaburo.web.model.Skill;
import com.DataLaburo.web.model.SkillAlias;
import com.DataLaburo.web.repository.SkillAliasRepository;
import com.DataLaburo.web.repository.SkillRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SeedSkillsCatalog implements CommandLineRunner {
	private final SkillRepository skillRepository;
	private final SkillAliasRepository skillAliasRepository;

	public SeedSkillsCatalog(SkillRepository skillRepository, SkillAliasRepository skillAliasRepository) {
		this.skillRepository = skillRepository;
		this.skillAliasRepository = skillAliasRepository;
	}

	@Override
	@Transactional
	public void run(String... args) {
		// Minimal starter catalog (MVP). Grow as needed.
		Map<String, List<String>> catalog = new LinkedHashMap<>();
		catalog.put("Java", List.of());
		catalog.put("Kotlin", List.of());
		catalog.put("Python", List.of());
		catalog.put("JavaScript", List.of("JS"));
		catalog.put("TypeScript", List.of("TS"));
		catalog.put("SQL", List.of());
		catalog.put("Spring Boot", List.of("SpringBoot", "Spring", "Spring Framework"));
		catalog.put("Hibernate", List.of());
		catalog.put("Maven", List.of());
		catalog.put("Gradle", List.of());
		catalog.put("Git", List.of());
		catalog.put("Linux", List.of());
		catalog.put("Docker", List.of());
		catalog.put("Kubernetes", List.of("K8s"));
		catalog.put("AWS", List.of("Amazon Web Services"));
		catalog.put("GCP", List.of("Google Cloud", "Google Cloud Platform"));
		catalog.put("Azure", List.of("Microsoft Azure"));
		catalog.put("PostgreSQL", List.of("Postgres"));
		catalog.put("MySQL", List.of());
		catalog.put("MongoDB", List.of("Mongo"));
		catalog.put("Redis", List.of());
		catalog.put("Elasticsearch", List.of("Elastic Search", "ElasticSearch", "ES"));
		catalog.put("Node.js", List.of("Node", "Node JS", "NodeJS"));
		catalog.put("Express", List.of("Express.js", "ExpressJS"));
		catalog.put("React", List.of("React.js", "ReactJS"));
		catalog.put("Angular", List.of());
		catalog.put("Vue", List.of("Vue.js"));
		catalog.put("HTML", List.of("HTML5"));
		catalog.put("CSS", List.of("CSS3"));
		catalog.put("REST", List.of("REST API", "RESTful"));
		catalog.put("CI/CD", List.of("CI CD", "CI", "CD"));
		catalog.put("GitHub Actions", List.of("Github Actions", "GH Actions"));
		catalog.put("Jenkins", List.of());
		catalog.put("Terraform", List.of());
		catalog.put("JUnit", List.of("JUnit5", "JUnit 5"));
		catalog.put("Mockito", List.of());
		catalog.put("Selenium", List.of());
		catalog.put("Cypress", List.of());
		catalog.put("Jest", List.of());
		catalog.put("C#", List.of("C Sharp", "Csharp"));
		catalog.put(".NET", List.of("Dotnet", "ASP.NET", "ASP.NET Core", "Dot Net"));
		catalog.put("Go", List.of("Golang"));

		for (Map.Entry<String, List<String>> entry : catalog.entrySet()) {
			String name = entry.getKey();
			String nameNorm = Skill.normalizeName(name);

			Skill skill = skillRepository.findByNameNormalized(nameNorm).orElseGet(() -> {
				Skill created = new Skill();
				created.setName(name);
				return skillRepository.save(created);
			});

			if (!skill.isEnabled()) {
				skill.setEnabled(true);
				skillRepository.save(skill);
			}

			List<String> aliases = new ArrayList<>(entry.getValue());
			for (String aliasValue : aliases) {
				String aliasNorm = Skill.normalizeName(aliasValue);
				if (aliasNorm.isBlank()) continue;
				if (skillAliasRepository.existsByAliasNormalized(aliasNorm)) continue;

				SkillAlias alias = new SkillAlias();
				alias.setSkill(skill);
				alias.setAlias(aliasValue);
				skillAliasRepository.save(alias);
			}
		}
	}
}
