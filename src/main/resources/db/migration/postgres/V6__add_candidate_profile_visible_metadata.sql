alter table candidate_profiles
    add column headline varchar(180),
    add column summary text,
    add column declared_skills_text text,
    add column linkedin_url varchar(2048),
    add column github_url varchar(2048),
    add column portfolio_url varchar(2048);
