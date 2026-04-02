-- Lightweight user profile table, one row per user.
create table if not exists user_profile
(
	user_id bigint not null comment 'User ID (unique)',
	profile_summary varchar(1000) null comment 'Natural-language summary for LLM context',
	profile_json text null comment 'Structured profile JSON',
	version int not null default 1 comment 'Version for optimistic evolution',
	updated_at datetime not null comment 'Last update time',
	primary key (user_id)
) comment 'User profile';

