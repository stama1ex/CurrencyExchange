-- ==============================
-- База данных: Касса обмена валют
-- PostgreSQL
-- ==============================
-- ВАЖНО:
-- 1) CREATE DATABASE exchange_office выполняется отдельно в базе postgres.
-- 2) Этот скрипт выполняется уже внутри базы exchange_office.

-- ==============================
-- Удаление объектов, если они уже существуют
-- ==============================

DROP VIEW IF EXISTS cash_desk_status;
DROP VIEW IF EXISTS exchange_operations_view;
DROP VIEW IF EXISTS incassations_view;

DROP TABLE IF EXISTS incassations;
DROP TABLE IF EXISTS exchange_operations;
DROP TABLE IF EXISTS exchange_rates;
DROP TABLE IF EXISTS cash_desk_balances;
DROP TABLE IF EXISTS cash_desks;
DROP TABLE IF EXISTS currencies;

DROP FUNCTION IF EXISTS ensure_cash_desk_balances_for_currency();
DROP FUNCTION IF EXISTS ensure_currency_balances_for_cash_desk();

-- ==============================
-- Таблица: currencies / Валюты
-- ==============================

CREATE TABLE currencies (
    currency_code CHAR(3) PRIMARY KEY,
    currency_name VARCHAR(50) NOT NULL
);

-- ==============================
-- Таблица: exchange_rates / Курсы валют
-- ==============================

CREATE TABLE exchange_rates (
    rate_id SERIAL PRIMARY KEY,
    currency_code CHAR(3) NOT NULL,
    buy_rate_mdl NUMERIC(10, 4) NOT NULL CHECK (buy_rate_mdl > 0),
    sell_rate_mdl NUMERIC(10, 4) NOT NULL CHECK (sell_rate_mdl > 0),
    rate_date DATE NOT NULL DEFAULT CURRENT_DATE,

    FOREIGN KEY (currency_code) REFERENCES currencies(currency_code)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- ==============================
-- Таблица: cash_desks / Кассы фирмы
-- Одна запись = одна точка обмена валют
-- Остатки денег хранятся в cash_desk_balances
-- ==============================

CREATE TABLE cash_desks (
    cash_desk_id SERIAL PRIMARY KEY,
    cash_desk_name VARCHAR(100) NOT NULL,
    address VARCHAR(150) NOT NULL,
    phone VARCHAR(30),

    status VARCHAR(20) NOT NULL CHECK (status IN ('Работает', 'Закрыта'))
);

-- ==============================
-- Таблица: cash_desk_balances / Остатки касс по валютам
-- Одна строка = баланс конкретной валюты в конкретной кассе
-- ==============================

CREATE TABLE cash_desk_balances (
    cash_desk_id INTEGER NOT NULL,
    currency_code CHAR(3) NOT NULL,
    balance NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    min_limit NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (min_limit >= 0),
    max_limit NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (max_limit >= min_limit),

    PRIMARY KEY (cash_desk_id, currency_code),

    FOREIGN KEY (cash_desk_id) REFERENCES cash_desks(cash_desk_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    FOREIGN KEY (currency_code) REFERENCES currencies(currency_code)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

CREATE FUNCTION ensure_cash_desk_balances_for_currency()
RETURNS trigger AS $$
BEGIN
    INSERT INTO cash_desk_balances (cash_desk_id, currency_code, balance, min_limit, max_limit)
    SELECT cash_desk_id, NEW.currency_code, 0, 0, 0
    FROM cash_desks
    ON CONFLICT (cash_desk_id, currency_code) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER currencies_after_insert_balance
AFTER INSERT ON currencies
FOR EACH ROW
EXECUTE FUNCTION ensure_cash_desk_balances_for_currency();

CREATE FUNCTION ensure_currency_balances_for_cash_desk()
RETURNS trigger AS $$
BEGIN
    INSERT INTO cash_desk_balances (cash_desk_id, currency_code, balance, min_limit, max_limit)
    SELECT NEW.cash_desk_id, currency_code, 0, 0, 0
    FROM currencies
    ON CONFLICT (cash_desk_id, currency_code) DO NOTHING;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER cash_desks_after_insert_balance
AFTER INSERT ON cash_desks
FOR EACH ROW
EXECUTE FUNCTION ensure_currency_balances_for_cash_desk();

-- ==============================
-- Таблица: exchange_operations / Операции обмена
-- ==============================

CREATE TABLE exchange_operations (
    operation_id SERIAL PRIMARY KEY,
    cash_desk_id INTEGER NOT NULL,
    operation_date TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
    currency_from CHAR(3) NOT NULL,
    currency_to CHAR(3) NOT NULL,
    amount_from NUMERIC(12, 2) NOT NULL CHECK (amount_from > 0),
    rate NUMERIC(10, 4) NOT NULL CHECK (rate > 0),
    amount_to NUMERIC(12, 2) NOT NULL CHECK (amount_to > 0),

    CHECK (currency_from <> currency_to),

    FOREIGN KEY (cash_desk_id) REFERENCES cash_desks(cash_desk_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    FOREIGN KEY (currency_from) REFERENCES currencies(currency_code)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    FOREIGN KEY (currency_to) REFERENCES currencies(currency_code)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- ==============================
-- Таблица: incassations / Инкассации и пополнения
-- ==============================

CREATE TABLE incassations (
    incassation_id SERIAL PRIMARY KEY,
    cash_desk_id INTEGER NOT NULL,
    incassation_date TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL,
    currency_code CHAR(3) NOT NULL,
    operation_type VARCHAR(20) NOT NULL CHECK (operation_type IN ('Инкассация', 'Пополнение')),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('Создана', 'Выполнена', 'Отменена')),
    note TEXT,

    FOREIGN KEY (cash_desk_id) REFERENCES cash_desks(cash_desk_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    FOREIGN KEY (currency_code) REFERENCES currencies(currency_code)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- ==============================
-- INSERT: currencies
-- ==============================

INSERT INTO currencies (currency_code, currency_name) VALUES
    ('MDL', 'Молдавский лей'),
    ('RON', 'Румынский лей'),
    ('EUR', 'Евро'),
    ('USD', 'Доллар США');

-- ==============================
-- INSERT: exchange_rates
-- Курсы указаны относительно MDL
-- buy_rate_mdl  = курс покупки валюты кассой
-- sell_rate_mdl = курс продажи валюты кассой
-- ==============================

INSERT INTO exchange_rates (currency_code, buy_rate_mdl, sell_rate_mdl, rate_date) VALUES
    ('MDL', 1.0000, 1.0000, '2026-05-01'),
    ('RON', 3.7500, 3.9000, '2026-05-01'),
    ('EUR', 19.3000, 19.5500, '2026-05-01'),
    ('USD', 17.8000, 18.0500, '2026-05-01');

-- ==============================
-- INSERT: cash_desks
-- ==============================

INSERT INTO cash_desks
(cash_desk_name, address, phone, status)
VALUES
    ('Касса Центр', 'Кишинёв, ул. Штефан чел Маре 25', '+37360000001', 'Работает'),
    ('Касса Ботаника', 'Кишинёв, бул. Дачия 14', '+37360000002', 'Работает'),
    ('Касса Бельцы', 'Бельцы, ул. Индепенденцей 10', '+37360000003', 'Работает'),
    ('Касса Оргеев', 'Оргеев, ул. Василе Лупу 5', '+37360000004', 'Закрыта');

-- Триггер уже создал нулевые остатки для всех валют; ниже задаем стартовые значения.
INSERT INTO cash_desk_balances (cash_desk_id, currency_code, balance, min_limit, max_limit) VALUES
    (1, 'MDL', 75000.00, 30000.00, 180000.00), (1, 'RON', 3000.00, 1000.00, 8000.00), (1, 'EUR', 2500.00, 500.00, 6000.00), (1, 'USD', 1800.00, 500.00, 5000.00),
    (2, 'MDL', 12000.00, 25000.00, 120000.00), (2, 'RON', 800.00, 1000.00, 6000.00),  (2, 'EUR', 400.00, 500.00, 4000.00),  (2, 'USD', 300.00, 500.00, 3500.00),
    (3, 'MDL', 155000.00, 30000.00, 200000.00),(3, 'RON', 5000.00, 1000.00, 9000.00), (3, 'EUR', 4200.00, 500.00, 7000.00), (3, 'USD', 3500.00, 500.00, 6500.00),
    (4, 'MDL', 6000.00, 20000.00, 90000.00),  (4, 'RON', 200.00, 800.00, 5000.00),  (4, 'EUR', 100.00, 300.00, 3000.00),  (4, 'USD', 150.00, 300.00, 3000.00)
ON CONFLICT (cash_desk_id, currency_code) DO UPDATE SET
    balance = EXCLUDED.balance,
    min_limit = EXCLUDED.min_limit,
    max_limit = EXCLUDED.max_limit;

-- ==============================
-- INSERT: exchange_operations
-- ==============================

INSERT INTO exchange_operations
(cash_desk_id, operation_date, currency_from, currency_to, amount_from, rate, amount_to)
VALUES
    (1, '2026-05-01', 'EUR', 'MDL', 100.00, 19.3000, 1930.00),
    (1, '2026-05-01', 'MDL', 'USD', 1805.00, 18.0500, 100.00),
    (2, '2026-05-02', 'RON', 'MDL', 200.00, 3.7500, 750.00),
    (3, '2026-05-02', 'USD', 'MDL', 300.00, 17.8000, 5340.00),
    (3, '2026-05-03', 'MDL', 'EUR', 3910.00, 19.5500, 200.00);

-- ==============================
-- INSERT: incassations
-- ==============================

INSERT INTO incassations
(cash_desk_id, incassation_date, currency_code, operation_type, amount, status, note)
VALUES
    (1, '2026-05-02', 'MDL', 'Инкассация', 30000.00, 'Создана', 'Слишком большой остаток в кассе'),
    (2, '2026-05-02', 'MDL', 'Пополнение', 15000.00, 'Создана', 'Недостаточно наличных MDL'),
    (3, '2026-05-03', 'EUR', 'Инкассация', 1000.00, 'Выполнена', 'Передано в центральный офис'),
    (4, '2026-05-03', 'MDL', 'Пополнение', 20000.00, 'Отменена', 'Касса временно закрыта');

-- ==============================
-- Представление: cash_desk_status
-- Показывает общий остаток кассы в MDL и рекомендацию
-- ==============================

CREATE VIEW cash_desk_status AS
WITH desk_status AS (
    SELECT
        cd.cash_desk_id,
        cd.cash_desk_name,
        cd.address,
        ROUND(
            COALESCE(SUM(
                COALESCE(cdb.balance, 0) *
                CASE
                    WHEN c.currency_code = 'MDL' THEN 1
                    ELSE COALESCE(latest.sell_rate_mdl, 0)
                END
            ), 0),
            2
        ) AS total_balance_mdl,
        BOOL_OR(COALESCE(cdb.balance, 0) > COALESCE(cdb.max_limit, 0)) AS needs_incassation,
        BOOL_OR(COALESCE(cdb.balance, 0) < COALESCE(cdb.min_limit, 0)) AS needs_replenishment
    FROM cash_desks cd
    CROSS JOIN currencies c
    LEFT JOIN cash_desk_balances cdb ON cdb.cash_desk_id = cd.cash_desk_id
        AND cdb.currency_code = c.currency_code
    LEFT JOIN LATERAL (
        SELECT er.sell_rate_mdl
        FROM exchange_rates er
        WHERE er.currency_code = c.currency_code
        ORDER BY er.rate_date DESC, er.rate_id DESC
        LIMIT 1
    ) latest ON TRUE
    GROUP BY cd.cash_desk_id, cd.cash_desk_name, cd.address
)
SELECT
    cash_desk_id,
    cash_desk_name,
    address,
    total_balance_mdl,
    CASE
        WHEN needs_incassation AND needs_replenishment THEN 'Нужны инкассация и пополнение'
        WHEN needs_incassation THEN 'Нужна инкассация'
        WHEN needs_replenishment THEN 'Нужно пополнение'
        ELSE 'Норма'
    END AS recommendation
FROM desk_status;

-- ==============================
-- Представление: exchange_operations_view
-- ==============================

CREATE VIEW exchange_operations_view AS
SELECT
    eo.operation_id,
    eo.operation_date,
    cd.cash_desk_name,
    cf.currency_name AS currency_from_name,
    eo.currency_from,
    eo.amount_from,
    ct.currency_name AS currency_to_name,
    eo.currency_to,
    eo.amount_to,
    eo.rate
FROM exchange_operations eo
JOIN cash_desks cd ON eo.cash_desk_id = cd.cash_desk_id
JOIN currencies cf ON eo.currency_from = cf.currency_code
JOIN currencies ct ON eo.currency_to = ct.currency_code;

-- ==============================
-- Представление: incassations_view
-- ==============================

CREATE VIEW incassations_view AS
SELECT
    i.incassation_id,
    i.incassation_date,
    cd.cash_desk_name,
    i.operation_type,
    i.currency_code,
    c.currency_name,
    i.amount,
    i.status,
    i.note
FROM incassations i
JOIN cash_desks cd ON i.cash_desk_id = cd.cash_desk_id
JOIN currencies c ON i.currency_code = c.currency_code;

-- ==============================
-- Примеры запросов
-- ==============================

-- SELECT * FROM currencies;
-- SELECT * FROM exchange_rates;
-- SELECT * FROM cash_desks;
-- SELECT * FROM cash_desk_balances ORDER BY cash_desk_id, currency_code;
-- SELECT * FROM exchange_operations;
-- SELECT * FROM incassations;

-- SELECT * FROM cash_desk_status;
-- SELECT * FROM cash_desk_status WHERE recommendation <> 'Норма';
-- SELECT * FROM exchange_operations_view;
-- SELECT * FROM incassations_view;
