-- ==============================
-- Большой сидер для базы exchange_office
-- PostgreSQL
-- ==============================
-- Запускать после db/database.sql внутри базы exchange_office.
-- Скрипт очищает данные и наполняет таблицы предсказуемым набором.

BEGIN;

TRUNCATE TABLE
    incassations,
    exchange_operations,
    exchange_rates,
    cash_desk_balances,
    cash_desks,
    currencies
RESTART IDENTITY CASCADE;

-- ==============================
-- Валюты
-- ==============================

INSERT INTO currencies (currency_code, currency_name) VALUES
    ('MDL', 'Молдавский лей'),
    ('RON', 'Румынский лей'),
    ('EUR', 'Евро'),
    ('USD', 'Доллар США'),
    ('UAH', 'Украинская гривна'),
    ('GBP', 'Фунт стерлингов'),
    ('CHF', 'Швейцарский франк'),
    ('PLN', 'Польский злотый'),
    ('TRY', 'Турецкая лира'),
    ('CAD', 'Канадский доллар'),
    ('AUD', 'Австралийский доллар'),
    ('JPY', 'Японская иена'),
    ('CZK', 'Чешская крона'),
    ('HUF', 'Венгерский форинт'),
    ('BGN', 'Болгарский лев'),
    ('GEL', 'Грузинский лари');

-- ==============================
-- Исторические курсы за 48 дней
-- ==============================

WITH rate_seed(currency_code, buy_rate_mdl, spread_mdl, weight) AS (
    VALUES
        ('MDL'::char(3), 1.0000::numeric, 0.0000::numeric, 0),
        ('RON'::char(3), 3.7500::numeric, 0.1400::numeric, 1),
        ('EUR'::char(3), 19.3000::numeric, 0.2500::numeric, 2),
        ('USD'::char(3), 17.8000::numeric, 0.2500::numeric, 3),
        ('UAH'::char(3), 0.4300::numeric, 0.0200::numeric, 4),
        ('GBP'::char(3), 22.3000::numeric, 0.3200::numeric, 5),
        ('CHF'::char(3), 20.5000::numeric, 0.3000::numeric, 6),
        ('PLN'::char(3), 4.5000::numeric, 0.1300::numeric, 7),
        ('TRY'::char(3), 0.5500::numeric, 0.0300::numeric, 8),
        ('CAD'::char(3), 12.9000::numeric, 0.2200::numeric, 9),
        ('AUD'::char(3), 11.6000::numeric, 0.2100::numeric, 10),
        ('JPY'::char(3), 0.1200::numeric, 0.0060::numeric, 11),
        ('CZK'::char(3), 0.7800::numeric, 0.0350::numeric, 12),
        ('HUF'::char(3), 0.0500::numeric, 0.0030::numeric, 13),
        ('BGN'::char(3), 9.8000::numeric, 0.1800::numeric, 14),
        ('GEL'::char(3), 6.4500::numeric, 0.1600::numeric, 15)
),
rate_days AS (
    SELECT rate_date::date
    FROM generate_series(date '2026-04-01', date '2026-05-18', interval '1 day') AS d(rate_date)
),
calculated_rates AS (
    SELECT
        r.currency_code,
        d.rate_date,
        CASE
            WHEN r.currency_code = 'MDL' THEN 1.0000
            ELSE ROUND((
                r.buy_rate_mdl
                * (1 + ((((EXTRACT(DAY FROM d.rate_date)::int + r.weight) % 9) - 4) * 0.0015))
            )::numeric, 4)
        END AS buy_rate_mdl,
        CASE
            WHEN r.currency_code = 'MDL' THEN 1.0000
            ELSE ROUND((
                r.buy_rate_mdl
                * (1 + ((((EXTRACT(DAY FROM d.rate_date)::int + r.weight) % 9) - 4) * 0.0015))
                + r.spread_mdl
            )::numeric, 4)
        END AS sell_rate_mdl
    FROM rate_seed r
    CROSS JOIN rate_days d
)
INSERT INTO exchange_rates (currency_code, buy_rate_mdl, sell_rate_mdl, rate_date)
SELECT currency_code, buy_rate_mdl, sell_rate_mdl, rate_date
FROM calculated_rates
ORDER BY rate_date, currency_code;

-- ==============================
-- Кассы
-- ==============================

INSERT INTO cash_desks (cash_desk_name, address, phone, status) VALUES
    ('Касса Центр', 'Кишинев, ул. Штефан чел Маре 25', '+37360000001', 'Работает'),
    ('Касса Ботаника', 'Кишинев, бул. Дачия 14', '+37360000002', 'Работает'),
    ('Касса Рышкановка', 'Кишинев, ул. Киевская 7', '+37360000003', 'Работает'),
    ('Касса Буюканы', 'Кишинев, ул. Ион Крянгэ 42', '+37360000004', 'Работает'),
    ('Касса Чеканы', 'Кишинев, бул. Мирча чел Бэтрын 19', '+37360000005', 'Работает'),
    ('Касса Телецентр', 'Кишинев, ул. Миорица 11', '+37360000006', 'Работает'),
    ('Касса Аэропорт', 'Кишинев, бул. Дачия 80/3', '+37360000007', 'Работает'),
    ('Касса Бельцы Центр', 'Бельцы, ул. Индепенденцей 10', '+37360000008', 'Работает'),
    ('Касса Бельцы Север', 'Бельцы, ул. Хотинская 31', '+37360000009', 'Работает'),
    ('Касса Оргеев', 'Оргеев, ул. Василе Лупу 5', '+37360000010', 'Работает'),
    ('Касса Кагул', 'Кагул, пр. Республики 28', '+37360000011', 'Работает'),
    ('Касса Унгены', 'Унгены, ул. Национальная 18', '+37360000012', 'Работает'),
    ('Касса Сороки', 'Сороки, ул. Штефан чел Маре 16', '+37360000013', 'Работает'),
    ('Касса Комрат', 'Комрат, ул. Победы 40', '+37360000014', 'Работает'),
    ('Касса Хынчешты', 'Хынчешты, ул. Кишиневская 22', '+37360000015', 'Работает'),
    ('Касса Дрокия', 'Дрокия, бул. Индепенденцей 3', '+37360000016', 'Закрыта'),
    ('Касса Единцы', 'Единцы, ул. Суворова 9', '+37360000017', 'Закрыта'),
    ('Касса Леова', 'Леова, ул. Унирий 12', '+37360000018', 'Закрыта');

-- Триггеры создали нулевые остатки, здесь задаются рабочие лимиты и суммы.
WITH balance_seed(currency_code, balance_base, balance_step, min_limit, max_limit, weight) AS (
    VALUES
        ('MDL'::char(3), 28000.00::numeric, 6200.00::numeric, 25000.00::numeric, 180000.00::numeric, 1),
        ('RON'::char(3), 1200.00::numeric, 170.00::numeric, 900.00::numeric, 9000.00::numeric, 2),
        ('EUR'::char(3), 650.00::numeric, 115.00::numeric, 500.00::numeric, 8000.00::numeric, 3),
        ('USD'::char(3), 720.00::numeric, 130.00::numeric, 500.00::numeric, 8000.00::numeric, 4),
        ('UAH'::char(3), 6000.00::numeric, 850.00::numeric, 2000.00::numeric, 40000.00::numeric, 5),
        ('GBP'::char(3), 230.00::numeric, 45.00::numeric, 200.00::numeric, 3500.00::numeric, 6),
        ('CHF'::char(3), 210.00::numeric, 42.00::numeric, 150.00::numeric, 3500.00::numeric, 7),
        ('PLN'::char(3), 900.00::numeric, 130.00::numeric, 500.00::numeric, 6000.00::numeric, 8),
        ('TRY'::char(3), 2500.00::numeric, 330.00::numeric, 1000.00::numeric, 25000.00::numeric, 9),
        ('CAD'::char(3), 310.00::numeric, 62.00::numeric, 200.00::numeric, 4000.00::numeric, 10),
        ('AUD'::char(3), 300.00::numeric, 60.00::numeric, 200.00::numeric, 4000.00::numeric, 11),
        ('JPY'::char(3), 180000.00::numeric, 6500.00::numeric, 50000.00::numeric, 450000.00::numeric, 12),
        ('CZK'::char(3), 2600.00::numeric, 410.00::numeric, 1000.00::numeric, 25000.00::numeric, 13),
        ('HUF'::char(3), 85000.00::numeric, 6200.00::numeric, 40000.00::numeric, 700000.00::numeric, 14),
        ('BGN'::char(3), 520.00::numeric, 85.00::numeric, 300.00::numeric, 5000.00::numeric, 15),
        ('GEL'::char(3), 460.00::numeric, 75.00::numeric, 250.00::numeric, 5000.00::numeric, 16)
),
generated_balances AS (
    SELECT
        cd.cash_desk_id,
        bs.currency_code,
        ROUND((
            bs.balance_base
            + cd.cash_desk_id * bs.balance_step
            + ((cd.cash_desk_id + bs.weight) % 5) * bs.balance_step * 0.70
        )::numeric, 2) AS balance,
        bs.min_limit,
        bs.max_limit
    FROM cash_desks cd
    CROSS JOIN balance_seed bs
)
INSERT INTO cash_desk_balances (cash_desk_id, currency_code, balance, min_limit, max_limit)
SELECT cash_desk_id, currency_code, balance, min_limit, max_limit
FROM generated_balances
ON CONFLICT (cash_desk_id, currency_code) DO UPDATE SET
    balance = EXCLUDED.balance,
    min_limit = EXCLUDED.min_limit,
    max_limit = EXCLUDED.max_limit;

-- Несколько касс намеренно выбиваются из лимитов для проверки рекомендаций.
UPDATE cash_desk_balances
SET balance = 210000.00
WHERE cash_desk_id = 5 AND currency_code = 'MDL';

UPDATE cash_desk_balances
SET balance = 180.00
WHERE cash_desk_id = 2 AND currency_code = 'EUR';

UPDATE cash_desk_balances
SET balance = 230.00
WHERE cash_desk_id = 11 AND currency_code = 'USD';

UPDATE cash_desk_balances
SET balance = 215000.00
WHERE cash_desk_id = 13 AND currency_code = 'MDL';

-- ==============================
-- Операции обмена
-- 520 строк, направления чередуются:
-- - клиент продает валюту кассе: FOREIGN -> MDL по buy_rate_mdl
-- - клиент покупает валюту: MDL -> FOREIGN по sell_rate_mdl
-- ==============================

WITH generated_operations AS (
    SELECT
        n,
        ((n - 1) % 18) + 1 AS cash_desk_id,
        (
            (date '2026-04-01' + ((n - 1) % 48))::timestamp
            + make_interval(hours => 8 + (n % 10), mins => (n * 7) % 60)
        ) AS operation_date,
        (ARRAY[
            'RON','EUR','USD','UAH','GBP','CHF','PLN','TRY',
            'CAD','AUD','JPY','CZK','HUF','BGN','GEL'
        ]::char(3)[])[((n - 1) % 15) + 1] AS foreign_code,
        (n % 2 = 0) AS client_sells_foreign
    FROM generate_series(1, 520) AS s(n)
),
operation_amounts AS (
    SELECT
        g.*,
        CASE g.foreign_code
            WHEN 'JPY' THEN (10000 + (g.n % 45) * 1200)::numeric
            WHEN 'HUF' THEN (25000 + (g.n % 50) * 2500)::numeric
            WHEN 'UAH' THEN (900 + (g.n % 35) * 110)::numeric
            WHEN 'TRY' THEN (700 + (g.n % 35) * 90)::numeric
            WHEN 'CZK' THEN (800 + (g.n % 35) * 120)::numeric
            ELSE (40 + (g.n % 28) * 15)::numeric
        END AS foreign_amount,
        (950 + ((g.n * 137) % 6800))::numeric AS mdl_amount
    FROM generated_operations g
),
rated_operations AS (
    SELECT
        oa.*,
        er.buy_rate_mdl,
        er.sell_rate_mdl
    FROM operation_amounts oa
    JOIN exchange_rates er ON er.currency_code = oa.foreign_code
        AND er.rate_date = oa.operation_date::date
)
INSERT INTO exchange_operations
    (cash_desk_id, operation_date, currency_from, currency_to, amount_from, rate, amount_to)
SELECT
    cash_desk_id,
    operation_date,
    CASE WHEN client_sells_foreign THEN foreign_code ELSE 'MDL'::char(3) END AS currency_from,
    CASE WHEN client_sells_foreign THEN 'MDL'::char(3) ELSE foreign_code END AS currency_to,
    CASE WHEN client_sells_foreign THEN foreign_amount ELSE mdl_amount END AS amount_from,
    CASE WHEN client_sells_foreign THEN buy_rate_mdl ELSE sell_rate_mdl END AS rate,
    CASE
        WHEN client_sells_foreign THEN ROUND((foreign_amount * buy_rate_mdl)::numeric, 2)
        ELSE ROUND((mdl_amount / sell_rate_mdl)::numeric, 2)
    END AS amount_to
FROM rated_operations
ORDER BY n;

-- ==============================
-- Инкассации и пополнения
-- 160 строк в разных статусах
-- ==============================

WITH generated_incassations AS (
    SELECT
        n,
        ((n - 1) % 18) + 1 AS cash_desk_id,
        (
            (date '2026-04-03' + ((n - 1) % 46))::timestamp
            + make_interval(hours => 10 + (n % 7), mins => (n * 11) % 60)
        ) AS incassation_date,
        (ARRAY[
            'MDL','EUR','USD','RON','UAH','GBP','CHF','PLN',
            'TRY','CAD','AUD','JPY','CZK','HUF','BGN','GEL'
        ]::char(3)[])[((n - 1) % 16) + 1] AS currency_code,
        CASE WHEN n % 2 = 0 THEN 'Инкассация' ELSE 'Пополнение' END AS operation_type,
        CASE
            WHEN n % 7 = 0 THEN 'Отменена'
            WHEN n % 3 = 0 THEN 'Выполнена'
            ELSE 'Создана'
        END AS status
    FROM generate_series(1, 160) AS s(n)
),
incassation_amounts AS (
    SELECT
        g.*,
        CASE g.currency_code
            WHEN 'MDL' THEN (7000 + (g.n % 18) * 2500)::numeric
            WHEN 'JPY' THEN (25000 + (g.n % 20) * 6000)::numeric
            WHEN 'HUF' THEN (40000 + (g.n % 25) * 8500)::numeric
            WHEN 'UAH' THEN (2500 + (g.n % 18) * 700)::numeric
            WHEN 'TRY' THEN (1500 + (g.n % 18) * 500)::numeric
            WHEN 'CZK' THEN (1800 + (g.n % 18) * 520)::numeric
            ELSE (250 + (g.n % 18) * 95)::numeric
        END AS amount
    FROM generated_incassations g
)
INSERT INTO incassations
    (cash_desk_id, incassation_date, currency_code, operation_type, amount, status, note)
SELECT
    cash_desk_id,
    incassation_date,
    currency_code,
    operation_type,
    amount,
    status,
    CASE
        WHEN status = 'Отменена' THEN 'Заявка отменена после сверки остатков'
        WHEN operation_type = 'Инкассация' THEN 'Плановая инкассация по итогам смены'
        ELSE 'Пополнение кассы перед пиковыми часами'
    END || ' #' || n AS note
FROM incassation_amounts
ORDER BY n;

COMMIT;

-- Контрольная сводка после запуска.
SELECT 'currencies' AS table_name, COUNT(*) AS rows_count FROM currencies
UNION ALL
SELECT 'exchange_rates', COUNT(*) FROM exchange_rates
UNION ALL
SELECT 'cash_desks', COUNT(*) FROM cash_desks
UNION ALL
SELECT 'cash_desk_balances', COUNT(*) FROM cash_desk_balances
UNION ALL
SELECT 'exchange_operations', COUNT(*) FROM exchange_operations
UNION ALL
SELECT 'incassations', COUNT(*) FROM incassations
ORDER BY table_name;
