create table platform_operator (
    id uuid primary key,
    status varchar(32) not null,
    password_hash varchar(255) not null,
    totp_secret_ciphertext text not null,
    totp_secret_key_id varchar(64) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table operator_setup_token (
    id uuid primary key,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    begun_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table login_session (
    id uuid primary key,
    operator_id uuid not null references platform_operator (id),
    refresh_token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table audit_event (
    id uuid primary key,
    event_type varchar(128) not null,
    actor_id uuid,
    details text,
    created_at timestamptz not null
);

create index idx_operator_setup_token_expires_at on operator_setup_token (expires_at);
create index idx_login_session_operator_id on login_session (operator_id);
create index idx_audit_event_event_type on audit_event (event_type);
