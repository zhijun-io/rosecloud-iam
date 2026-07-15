create table tenant (
    id uuid primary key,
    name varchar(255) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table iam_user (
    id uuid primary key,
    email varchar(320) not null unique,
    email_verified_at timestamptz,
    password_hash varchar(255) not null,
    totp_secret_ciphertext text not null,
    totp_secret_key_id varchar(64) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table membership (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    user_id uuid not null references iam_user (id),
    role_code varchar(64) not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

-- History rows (REMOVED) must not block a later re-invite Membership (iam-v1).
create unique index uq_membership_non_removed_pair
    on membership (tenant_id, user_id)
    where status <> 'REMOVED';

create table invitation (
    id uuid primary key,
    tenant_id uuid not null references tenant (id),
    email varchar(320) not null,
    role_code varchar(64) not null,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    created_at timestamptz not null
);

create table outbox_message (
    id uuid primary key,
    aggregate_type varchar(128) not null,
    aggregate_id uuid not null,
    event_type varchar(128) not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz
);

create index idx_tenant_status on tenant (status);
create index idx_invitation_tenant_id on invitation (tenant_id);
create index idx_invitation_expires_at on invitation (expires_at);
create index idx_membership_tenant_id on membership (tenant_id);
create index idx_membership_user_id on membership (user_id);
create index idx_outbox_message_event_type on outbox_message (event_type);
create index idx_outbox_message_published_at on outbox_message (published_at);
