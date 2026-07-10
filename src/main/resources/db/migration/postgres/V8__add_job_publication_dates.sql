alter table jobs
    add column published_at_estimated timestamp(6) with time zone,
    add column posted_at_observed_at timestamp(6) with time zone;
