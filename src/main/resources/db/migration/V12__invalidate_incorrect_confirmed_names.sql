-- Приведение подтверждённых ФИО к актуальным требованиям валидации.
-- Все записи, нарушающие правила валидатора, переводятся в статус неподтверждённых,
-- чтобы бот повторно запросил корректное значение у покупателя.
WITH normalized AS (
    SELECT c.id,
           c.full_name,
           CASE
               WHEN c.full_name IS NULL THEN NULL
               ELSE btrim(
                       regexp_replace(
                           regexp_replace(
                               regexp_replace(c.full_name, '\\s+', ' ', 'g'),
                               '\\s*-\\s*', '-', 'g'
                           ),
                           '\\s*''\\s*', '''', 'g'
                       )
                   )
           END AS normalized_full_name
    FROM tb_customers AS c
    WHERE c.name_source = 'USER_CONFIRMED'
), analyzed AS (
    SELECT n.id,
           n.normalized_full_name,
           CASE
               WHEN n.normalized_full_name IS NULL THEN 0
               ELSE (
                   SELECT COUNT(word)
                   FROM unnest(regexp_split_to_array(n.normalized_full_name, '[-\\s]+')) AS word
                   WHERE word <> ''
               )
           END AS word_count
    FROM normalized AS n
)
UPDATE tb_customers AS c
SET name_source    = 'MERCHANT_PROVIDED',
    name_updated_at = NOW()
FROM analyzed AS a
WHERE c.id = a.id
  AND (
        a.normalized_full_name IS NULL
        OR a.normalized_full_name = ''
        OR length(a.normalized_full_name) < 2
        OR length(a.normalized_full_name) > 100
        OR lower(a.normalized_full_name) IN ('да', 'верно', 'ок', 'окей', 'ага', 'yes', 'y')
        OR a.normalized_full_name !~ '^[\\p{L}\\s''-]+$'
        OR a.word_count < 2
      );
