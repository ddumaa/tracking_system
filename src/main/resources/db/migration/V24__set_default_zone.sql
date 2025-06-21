ALTER TABLE tb_scheduled_task_config ALTER COLUMN zone SET DEFAULT 'UTC';
UPDATE tb_scheduled_task_config SET zone='UTC' WHERE zone IS NULL OR zone='';

