-- Aggregate duplicates in tb_store_statistics and tb_postal_service_statistics
-- before applying unique constraints

-- 1. Aggregate store statistics and keep one row per store
WITH aggregated AS (
    SELECT MIN(id) AS min_id,
           store_id,
           SUM(total_sent) AS total_sent,
           SUM(total_delivered) AS total_delivered,
           SUM(total_returned) AS total_returned,
           SUM(sum_delivery_days) AS sum_delivery_days,
           SUM(sum_pickup_days) AS sum_pickup_days,
           MAX(updated_at) AS updated_at
    FROM tb_store_statistics
    GROUP BY store_id
)
UPDATE tb_store_statistics s
SET total_sent = a.total_sent,
    total_delivered = a.total_delivered,
    total_returned = a.total_returned,
    sum_delivery_days = a.sum_delivery_days,
    sum_pickup_days = a.sum_pickup_days,
    updated_at = a.updated_at
FROM aggregated a
WHERE s.id = a.min_id;

WITH aggregated AS (
    SELECT MIN(id) AS min_id,
           store_id
    FROM tb_store_statistics
    GROUP BY store_id
)
DELETE FROM tb_store_statistics s
USING aggregated a
WHERE s.store_id = a.store_id AND s.id <> a.min_id;

-- 2. Aggregate postal service statistics
WITH aggregated_ps AS (
    SELECT MIN(id) AS min_id,
           store_id,
           postal_service_type,
           SUM(total_sent) AS total_sent,
           SUM(total_delivered) AS total_delivered,
           SUM(total_returned) AS total_returned,
           SUM(sum_delivery_days) AS sum_delivery_days,
           SUM(sum_pickup_days) AS sum_pickup_days,
           MAX(updated_at) AS updated_at
    FROM tb_postal_service_statistics
    GROUP BY store_id, postal_service_type
)
UPDATE tb_postal_service_statistics p
SET total_sent = a.total_sent,
    total_delivered = a.total_delivered,
    total_returned = a.total_returned,
    sum_delivery_days = a.sum_delivery_days,
    sum_pickup_days = a.sum_pickup_days,
    updated_at = a.updated_at
FROM aggregated_ps a
WHERE p.id = a.min_id;

WITH aggregated_ps AS (
    SELECT MIN(id) AS min_id,
           store_id,
           postal_service_type
    FROM tb_postal_service_statistics
    GROUP BY store_id, postal_service_type
)
DELETE FROM tb_postal_service_statistics p
USING aggregated_ps a
WHERE p.store_id = a.store_id
  AND p.postal_service_type = a.postal_service_type
  AND p.id <> a.min_id;

-- 3. Add unique constraints after cleanup
ALTER TABLE tb_store_statistics
    ADD CONSTRAINT uk_store_statistics_store UNIQUE (store_id);

ALTER TABLE tb_postal_service_statistics
    ADD CONSTRAINT uk_postal_stats_store_service UNIQUE (store_id, postal_service_type);
