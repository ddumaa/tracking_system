UPDATE tb_users
SET subscription_plan_id = (SELECT id FROM subscription_plans WHERE name = 'FREE')
WHERE subscription_plan_id IS NULL;