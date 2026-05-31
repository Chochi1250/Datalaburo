alter table candidate_profiles
    add column target_role varchar(64) not null default 'UNDECIDED',
    add column target_seniority varchar(32) not null default 'ANY',
    add column search_mode varchar(32) not null default 'FOCUSED';
