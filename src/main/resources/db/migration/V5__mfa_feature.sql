-- Platform MfaFeature (default off) + optional TOTP columns + challenge/recovery/step-up support.

create table platform_setting (
    setting_key varchar(64) primary key,
    value_bool boolean not null,
    updated_at timestamptz not null
);

insert into platform_setting (setting_key, value_bool, updated_at)
values ('mfa_feature', false, now());

alter table platform_operator
    alter column totp_secret_ciphertext drop not null,
    alter column totp_secret_key_id drop not null,
    add column pending_totp_secret_ciphertext text,
    add column pending_totp_secret_key_id varchar(64);

alter table iam_user
    alter column totp_secret_ciphertext drop not null,
    alter column totp_secret_key_id drop not null,
    add column pending_totp_secret_ciphertext text,
    add column pending_totp_secret_key_id varchar(64);

create table factor_challenge (
    id uuid primary key,
    principal_type varchar(16) not null,
    principal_id uuid not null,
    factor_kind varchar(32) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null
);

create index idx_factor_challenge_principal on factor_challenge (principal_type, principal_id);

create table recovery_code (
    id uuid primary key,
    principal_type varchar(16) not null,
    principal_id uuid not null,
    code_hash varchar(64) not null,
    used_at timestamptz,
    created_at timestamptz not null
);

create index idx_recovery_code_principal on recovery_code (principal_type, principal_id);

alter table login_session
    add column step_up_satisfied_at timestamptz;
