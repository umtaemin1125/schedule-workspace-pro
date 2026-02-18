alter table workspace_items add column if not exists template_type varchar(30);

update workspace_items
set template_type = 'free'
where template_type is null or template_type = '';

alter table workspace_items alter column template_type set not null;
