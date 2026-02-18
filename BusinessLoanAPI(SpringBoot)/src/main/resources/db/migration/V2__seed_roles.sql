INSERT INTO app_role (id, name)
VALUES
    (gen_random_uuid(), 'APPLICANT'),
    (gen_random_uuid(), 'LOAN_OFFICER'),
    (gen_random_uuid(), 'ADMIN')
ON CONFLICT (name) DO NOTHING;
