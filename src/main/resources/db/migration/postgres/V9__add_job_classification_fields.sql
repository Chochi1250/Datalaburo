alter table jobs
    add column role_family varchar(64),
    add column role_specialty varchar(128),
    add column role_seniority varchar(32),
    add column work_modality varchar(32),
    add column employment_type varchar(32),
    add column classification_version varchar(64),
    add column classified_at timestamp(6) with time zone;
