alter table users add column if not exists nickname varchar(120);

update users
set nickname = split_part(email, '@', 1)
where nickname is null or nickname = '';

alter table users alter column nickname set not null;
