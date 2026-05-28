with profile_seed(name, cv_text) as (
    values
        (
            'DIAG - Backend Trainee Projects',
            'Backend trainee developer. Academic and personal projects building REST APIs with Java, Spring Boot, Maven, JUnit, Mockito, PostgreSQL, MySQL, Docker and Git. Built a small job matching API and CRUD services. Looking for junior backend, trainee backend or backend developer roles. No professional seniority yet.'
        ),
        (
            'DIAG - Backend Senior Java Cloud',
            'Senior backend engineer with 7 years of professional experience. Java, Spring Boot, microservices, REST APIs, PostgreSQL, MySQL, Kafka, Docker, Kubernetes, AWS, CI/CD, Git, Linux and observability. Led backend migrations and production services. Interested in senior backend, platform backend and cloud backend roles.'
        ),
        (
            'DIAG - IT Support Analyst Junior',
            'Junior IT support analyst. Help desk, service desk, ticket triage, Windows Server basics, Linux basics, Active Directory, Office 365, networking fundamentals, SQL basics, incident documentation, ITIL foundations and customer support. Interested in technical support, application support junior and IT analyst roles.'
        ),
        (
            'DIAG - Data BI SQL Profile',
            'Data and BI profile focused on SQL, PostgreSQL, SQL Server, Power BI, Excel, dashboards, reporting, ETL basics, data quality and business analysis. Academic projects with Python for data cleaning and visualization. Interested in data analyst, BI analyst and SQL reporting roles.'
        ),
        (
            'DIAG - Backend Strong Partial DevOps Transfer',
            'Backend developer with strong Java, Spring Boot, REST APIs, PostgreSQL, Docker, Git and automated testing experience. Built backend services and deployments with Docker Compose. Partial transferability toward DevOps and cloud: basic Kubernetes, Linux, CI/CD concepts and AWS fundamentals. Interested in backend roles with cloud growth.'
        )
)
update candidate_profiles profile
set cv_text = profile_seed.cv_text,
    updated_at = now()
from profile_seed
where profile.name = profile_seed.name;

with profile_seed(name, cv_text) as (
    values
        (
            'DIAG - Backend Trainee Projects',
            'Backend trainee developer. Academic and personal projects building REST APIs with Java, Spring Boot, Maven, JUnit, Mockito, PostgreSQL, MySQL, Docker and Git. Built a small job matching API and CRUD services. Looking for junior backend, trainee backend or backend developer roles. No professional seniority yet.'
        ),
        (
            'DIAG - Backend Senior Java Cloud',
            'Senior backend engineer with 7 years of professional experience. Java, Spring Boot, microservices, REST APIs, PostgreSQL, MySQL, Kafka, Docker, Kubernetes, AWS, CI/CD, Git, Linux and observability. Led backend migrations and production services. Interested in senior backend, platform backend and cloud backend roles.'
        ),
        (
            'DIAG - IT Support Analyst Junior',
            'Junior IT support analyst. Help desk, service desk, ticket triage, Windows Server basics, Linux basics, Active Directory, Office 365, networking fundamentals, SQL basics, incident documentation, ITIL foundations and customer support. Interested in technical support, application support junior and IT analyst roles.'
        ),
        (
            'DIAG - Data BI SQL Profile',
            'Data and BI profile focused on SQL, PostgreSQL, SQL Server, Power BI, Excel, dashboards, reporting, ETL basics, data quality and business analysis. Academic projects with Python for data cleaning and visualization. Interested in data analyst, BI analyst and SQL reporting roles.'
        ),
        (
            'DIAG - Backend Strong Partial DevOps Transfer',
            'Backend developer with strong Java, Spring Boot, REST APIs, PostgreSQL, Docker, Git and automated testing experience. Built backend services and deployments with Docker Compose. Partial transferability toward DevOps and cloud: basic Kubernetes, Linux, CI/CD concepts and AWS fundamentals. Interested in backend roles with cloud growth.'
        )
)
insert into candidate_profiles(name, cv_text, created_at, updated_at)
select profile_seed.name, profile_seed.cv_text, now(), now()
from profile_seed
where not exists (
    select 1
    from candidate_profiles profile
    where profile.name = profile_seed.name
);

select id, name
from candidate_profiles
where name in (
    'DIAG - Backend Trainee Projects',
    'DIAG - Backend Senior Java Cloud',
    'DIAG - IT Support Analyst Junior',
    'DIAG - Data BI SQL Profile',
    'DIAG - Backend Strong Partial DevOps Transfer'
)
order by id;
