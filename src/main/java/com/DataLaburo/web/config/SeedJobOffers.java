package com.DataLaburo.web.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class SeedJobOffers {
	// Legacy: JOB_OFFERS was a placeholder table seeded with 5 demo offers.
	// The system now treats JOBS as the single source of truth (captured by the plugin),
	// so this seed is intentionally disabled.
}
