alter table login_session
    alter column operator_id drop not null;

alter table login_session
    add column principal_type varchar(16),
    add column principal_id uuid,
    add column family_id uuid,
    add column previous_refresh_token_hash varchar(64),
    add column rotated_at timestamptz,
    add column revoked_at timestamptz;

update login_session
set principal_type = 'OPERATOR',
    principal_id = operator_id,
    family_id = id
where principal_type is null;

alter table login_session
    alter column principal_type set not null,
    alter column principal_id set not null,
    alter column family_id set not null;

alter table login_session
    add constraint ck_login_session_principal_type
        check (principal_type in ('OPERATOR', 'USER'));

create index idx_login_session_principal on login_session (principal_type, principal_id);
create index idx_login_session_family_id on login_session (family_id);
create unique index uq_login_session_refresh_token_hash on login_session (refresh_token_hash);
create index idx_login_session_previous_refresh_token_hash
    on login_session (previous_refresh_token_hash)
    where previous_refresh_token_hash is not null;
