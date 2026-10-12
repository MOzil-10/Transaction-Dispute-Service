-- ============================================================================
-- V4__seed_local_data.sql
--
-- NOTE: numbered V4 so it is unique within the local profile's merged Flyway
-- location set (classpath:db/migration,classpath:db/seed). It must sort AFTER
-- the schema migrations V1 (schema), V2 (category column), V3 (refund audit),
-- V5 (account version) so the columns it populates already exist. Runs ONLY
-- under the local profile.
--
-- LOCAL / DOCKER ONLY seed data. This migration lives in classpath:db/seed,
-- which is added to Flyway's locations ONLY by the `local` profile
-- (application-local.yaml). It is NOT on the production migration path
-- (classpath:db/migration) and NOT on the H2 test path (classpath:db/migration-h2),
-- so it never runs against production or the test suite.
--
-- ----------------------------------------------------------------------------
-- SCENARIO COVERAGE (so a reviewer can exercise every dispute path)
--
-- The dispute window is 60 days: a transaction can be disputed only while
-- (today - transaction_date) <= 60. We date rows deliberately and name their
-- reference so the scenario is obvious in the UI:
--
--   *-PRE60-*   dated well inside the window  -> DISPUTABLE
--   *-EDGE60-*  dated ~57 days ago (near the boundary, still inside) -> DISPUTABLE
--   *-POST60-*  dated > 60 days ago -> WINDOW CLOSED (POST /disputes -> 400)
--   *-AUTO-*    DEBICHECK + recent  -> AUTO-APPROVED + immediate refund
--   *-MANUAL-*  STANDARD_EFT        -> QUEUED_FOR_AGENT (manual investigation)
--   *-NODISPUTE-* GAMBLING / IN_STORE_RETAIL / P2P_TRANSFER -> not disputable
--
-- Decision routing (RiskScoreEngine + DecisionMatrix, threshold 60):
--   DEBICHECK base 30; STANDARD_EFT base 50; reason adds 5..20. So a DEBICHECK
--   dispute with a low/medium reason auto-approves; STANDARD_EFT routes to an agent.
--
-- Each disputable transaction can be disputed ONCE (duplicate guard returns 400),
-- so the POST60 rows are the reliable way to re-demonstrate the window rule.
--
-- Account types use Capitec's real-world personal product names:
--   MAIN_ACCOUNT - primary transactional account; SAVINGS_PLAN - a savings plan.
--
-- Shared password for all three customers: Password123!
-- (BCrypt hash below, produced by the app's own BCryptPasswordEncoder.)
-- ============================================================================

-- Shared BCrypt hash of "Password123!"
-- $2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe

-- ---- Customers --------------------------------------------------------------
INSERT INTO customers (customer_id, customer_number, first_name, last_name, email, password_hash) VALUES
    (1000001, 'CUST-TREVOR-001', 'Trevor', 'Sesoko', 'trevor.sesoko@example.co.za', '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000002, 'CUST-MESUT-002',  'Mesut',  'Ozil',   'mesut.ozil@example.co.za',    '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000003, 'CUST-DONALD-003', 'Donald', 'Duck',   'donald.duck@example.co.za',   '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    -- Capitec reviewer accounts (share these three; keep the three above for your own testing).
    (1000004, 'CUST-DISPUTEX-004', 'Thandeka', 'Nkosi',    'thandeka.nkosi@disputex.co.za', '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000005, 'CUST-DISPUTEX-005', 'Sipho',    'Dlamini',  'sipho.dlamini@disputex.co.za',  '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000006, 'CUST-DISPUTEX-006', 'Lerato',   'Mahlangu', 'lerato.mahlangu@disputex.co.za','$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe');

-- ---- Accounts (one per customer) -------------------------------------------
INSERT INTO accounts (account_id, customer_id, account_number, account_type, balance, currency) VALUES
    (5000001, 1000001, '1001000001', 'MAIN_ACCOUNT', 25000.00, 'ZAR'),
    (5000002, 1000002, '1001000002', 'MAIN_ACCOUNT', 18000.00, 'ZAR'),
    (5000003, 1000003, '1001000003', 'SAVINGS_PLAN',  9500.00, 'ZAR'),
    -- Capitec reviewer accounts
    (5000004, 1000004, '1001000004', 'MAIN_ACCOUNT', 32000.00, 'ZAR'),
    (5000005, 1000005, '1001000005', 'MAIN_ACCOUNT', 15400.00, 'ZAR'),
    (5000006, 1000006, '1001000006', 'SAVINGS_PLAN', 21750.00, 'ZAR');

-- ============================================================================
-- Transactions. The first SIX rows of each account are the demo-critical,
-- self-labelled scenarios (PRE60 / EDGE60 / POST60 / AUTO / MANUAL / NODISPUTE);
-- the remaining rows are realistic filler so the history looks real.
-- ============================================================================

-- ---- Trevor Sesoko (account 5000001) ---------------------------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000001, 5000001, 'TXN-TREVOR-AUTO-PRE60',      'VIRGIN ACTIVE GYM'   ,  450.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '10 days'),  -- DEBICHECK + in window -> AUTO-APPROVE + refund
    (2000002, 5000001, 'TXN-TREVOR-MANUAL-PRE60',    'ABSA HOME LOAN'      , 8200.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '7 days'),   -- STANDARD_EFT + in window -> QUEUED_FOR_AGENT
    (2000033, 5000001, 'TXN-TREVOR-EDGE60',          'TAKEALOT'            , 1299.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '57 days'),  -- near boundary, STILL disputable
    (2000011, 5000001, 'TXN-TREVOR-POST60',          'OLD FINANCE LOAN'    , 2500.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '90 days'),  -- WINDOW CLOSED (disputable category, blocked only by the 60-day rule)
    (2000017, 5000001, 'TXN-TREVOR-NODISPUTE-GAMB',  'BETWAY'              ,  500.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '6 days'),   -- category not disputable
    (2000003, 5000001, 'TXN-TREVOR-NODISPUTE-POS',   'PICK N PAY'          ,  860.75, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),   -- in-store POS, not disputable
    -- Realistic filler
    (2000012, 5000001, 'TXN-TREVOR-MTN-012',  'MTN'                 ,  599.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000013, 5000001, 'TXN-TREVOR-INS-013',  'MOMENTUM INSURANCE'  ,  780.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '12 days'),
    (2000014, 5000001, 'TXN-TREVOR-MUN-014',  'CITY OF JOHANNESBURG', 2150.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '14 days'),
    (2000015, 5000001, 'TXN-TREVOR-SHO-015',  'SHOPRITE'            ,  645.20, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000016, 5000001, 'TXN-TREVOR-UE-016',   'UBER EATS'           ,  289.50, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '1 days'),
    (2000018, 5000001, 'TXN-TREVOR-HB-018',   'HOLLYWOODBETS'       ,  250.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '9 days'),
    (2000019, 5000001, 'TXN-TREVOR-TAK-019',  'TAKEALOT'            ,  430.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '16 days'),
    (2000021, 5000001, 'TXN-TREVOR-ESK-021',  'ESKOM'               , 1142.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '20 days'),
    (2000024, 5000001, 'TXN-TREVOR-MTN-024',  'MTN'                 ,  599.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '35 days'),
    (2000026, 5000001, 'TXN-TREVOR-NET-026',  'NETFLIX'             ,  199.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '26 days'),
    (2000030, 5000001, 'TXN-TREVOR-MUN-030',  'JOBURG WATER'        ,  890.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '38 days'),
    (2000032, 5000001, 'TXN-TREVOR-P2P-032',  'EFT TO T MOKOENA'    , 1200.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',    'COMPLETED', now() - INTERVAL '11 days');

-- ---- Mesut Ozil (account 5000002) ------------------------------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000004, 5000002, 'TXN-MESUT-AUTO-PRE60',     'PLANET FITNESS'    ,  399.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '9 days'),
    (2000005, 5000002, 'TXN-MESUT-MANUAL-PRE60',   'CAPITEC VEHICLE LOAN', 5600.00, 'ZAR', 'STANDARD_EFT', 'LOAN',          'COMPLETED', now() - INTERVAL '6 days'),
    (2000047, 5000002, 'TXN-MESUT-EDGE60',         'TAKEALOT'          ,  899.99, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '58 days'),
    (2000058, 5000002, 'TXN-MESUT-POST60',         'OLD FINANCE LOAN'  , 3200.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '95 days'),
    (2000045, 5000002, 'TXN-MESUT-NODISPUTE-GAMB', 'BETWAY'            ,  750.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '7 days'),
    (2000006, 5000002, 'TXN-MESUT-NODISPUTE-POS',  'PICK N PAY'        , 1240.30, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '1 days'),
    -- Realistic filler
    (2000040, 5000002, 'TXN-MESUT-VOD-040',  'VODACOM'           ,  699.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000041, 5000002, 'TXN-MESUT-INS-041',  'DISCOVERY INSURE'  ,  899.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '5 days'),
    (2000042, 5000002, 'TXN-MESUT-MUN-042',  'CITY OF CAPE TOWN' , 1870.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '8 days'),
    (2000043, 5000002, 'TXN-MESUT-SHO-043',  'SHOPRITE'          ,  432.10, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000044, 5000002, 'TXN-MESUT-UE-044',   'UBER EATS'         ,  198.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '4 days'),
    (2000049, 5000002, 'TXN-MESUT-VOD-049',  'VODACOM'           ,  699.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '33 days'),
    (2000050, 5000002, 'TXN-MESUT-ESK-050',  'ESKOM'             , 1340.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '15 days'),
    (2000051, 5000002, 'TXN-MESUT-UE-051',   'UBER EATS'         ,  265.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '17 days'),
    (2000053, 5000002, 'TXN-MESUT-BET-053',  'BETWAY'            ,  300.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '21 days'),
    (2000056, 5000002, 'TXN-MESUT-TAK-056',  'TAKEALOT'          , 2100.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '31 days'),
    (2000061, 5000002, 'TXN-MESUT-P2P-061',  'EFT TO M OZIL'     ,  900.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',    'COMPLETED', now() - INTERVAL '13 days'),
    (2000062, 5000002, 'TXN-MESUT-GYM-062',  'PLANET FITNESS'    ,  399.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '69 days');

-- ---- Donald Duck (account 5000003, SAVINGS_PLAN) ---------------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000007, 5000003, 'TXN-DONALD-AUTO-PRE60',     'SILVER SNEAKERS GYM', 299.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '8 days'),
    (2000008, 5000003, 'TXN-DONALD-MANUAL-PRE60',   'FNB PERSONAL LOAN'  , 3100.00, 'ZAR', 'STANDARD_EFT', 'LOAN',           'COMPLETED', now() - INTERVAL '5 days'),
    (2000077, 5000003, 'TXN-DONALD-EDGE60',         'TAKEALOT'           ,  675.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',  'COMPLETED', now() - INTERVAL '56 days'),
    (2000092, 5000003, 'TXN-DONALD-POST60',         'OLD CLOTHING STORE' ,  410.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',  'COMPLETED', now() - INTERVAL '100 days'),
    (2000075, 5000003, 'TXN-DONALD-NODISPUTE-GAMB', 'BETWAY'             ,  220.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',       'COMPLETED', now() - INTERVAL '7 days'),
    (2000091, 5000003, 'TXN-DONALD-NODISPUTE-P2P',  'EFT TO D DUCK'      ,  450.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',   'COMPLETED', now() - INTERVAL '14 days'),
    -- Realistic filler
    (2000070, 5000003, 'TXN-DONALD-VOD-070',  'VODACOM'            ,  499.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000071, 5000003, 'TXN-DONALD-INS-071',  'OUTSURANCE'         ,  640.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '6 days'),
    (2000072, 5000003, 'TXN-DONALD-MUN-072',  'ETHEKWINI MUNICIPAL', 1420.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '9 days'),
    (2000073, 5000003, 'TXN-DONALD-SHO-073',  'SHOPRITE'           ,  365.80, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000074, 5000003, 'TXN-DONALD-UE-074',   'UBER EATS'          ,  155.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '1 days'),
    (2000079, 5000003, 'TXN-DONALD-VOD-079',  'VODACOM'            ,  499.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '34 days'),
    (2000080, 5000003, 'TXN-DONALD-ESK-080',  'ESKOM'              ,  760.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '16 days'),
    (2000081, 5000003, 'TXN-DONALD-UE-081',   'UBER EATS'          ,  132.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '18 days'),
    (2000084, 5000003, 'TXN-DONALD-SPO-084',  'SPOTIFY'            ,   69.99, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '24 days'),
    (2000086, 5000003, 'TXN-DONALD-TAK-086',  'TAKEALOT'           , 1180.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '32 days'),
    (2000088, 5000003, 'TXN-DONALD-LOAN-088', 'FNB PERSONAL LOAN'  , 3100.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '36 days');

-- ============================================================================
-- CAPITEC REVIEWER DATA
-- Three extra accounts (login details shared with Capitec) carrying the full
-- labelled scenario set, so a reviewer can exercise every dispute path:
-- AUTO-PRE60, MANUAL-PRE60, EDGE60, POST60, NODISPUTE-GAMB, NODISPUTE-POS/P2P.
-- ============================================================================

-- ---- Thandeka Nkosi (account 5000004) --------------------------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000501, 5000004, 'TXN-THANDEKA-AUTO-PRE60',     'VIRGIN ACTIVE GYM' ,  450.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '10 days'),  -- DEBICHECK + in window -> AUTO-APPROVE + refund
    (2000502, 5000004, 'TXN-THANDEKA-MANUAL-PRE60',   'ABSA HOME LOAN'    , 7400.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '6 days'),   -- STANDARD_EFT + in window -> QUEUED_FOR_AGENT
    (2000503, 5000004, 'TXN-THANDEKA-EDGE60',         'TAKEALOT'          , 1150.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '57 days'),  -- near boundary, STILL disputable
    (2000504, 5000004, 'TXN-THANDEKA-POST60',         'OLD FINANCE LOAN'  , 2900.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '92 days'),  -- WINDOW CLOSED (blocked only by the 60-day rule)
    (2000505, 5000004, 'TXN-THANDEKA-NODISPUTE-GAMB', 'BETWAY'            ,  600.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '6 days'),   -- category not disputable
    (2000506, 5000004, 'TXN-THANDEKA-NODISPUTE-POS',  'PICK N PAY'        ,  915.40, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),   -- in-store POS, not disputable
    -- Realistic filler
    (2000507, 5000004, 'TXN-THANDEKA-VOD-507',  'VODACOM'            ,  649.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000508, 5000004, 'TXN-THANDEKA-INS-508',  'MOMENTUM INSURANCE' ,  820.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '11 days'),
    (2000509, 5000004, 'TXN-THANDEKA-MUN-509',  'CITY OF TSHWANE'    , 1980.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '13 days'),
    (2000510, 5000004, 'TXN-THANDEKA-SHO-510',  'SHOPRITE'           ,  712.60, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000511, 5000004, 'TXN-THANDEKA-UE-511',   'UBER EATS'          ,  245.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '1 days'),
    (2000512, 5000004, 'TXN-THANDEKA-ESK-512',  'ESKOM'              , 1210.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '19 days'),
    (2000513, 5000004, 'TXN-THANDEKA-TAK-513',  'TAKEALOT'           ,  540.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '27 days'),
    (2000514, 5000004, 'TXN-THANDEKA-VOD-514',  'VODACOM'            ,  649.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '35 days'),
    (2000515, 5000004, 'TXN-THANDEKA-P2P-515',  'EFT TO N NKOSI'     , 1100.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',    'COMPLETED', now() - INTERVAL '12 days');

-- ---- Sipho Dlamini (account 5000005) ---------------------------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000521, 5000005, 'TXN-SIPHO-AUTO-PRE60',     'PLANET FITNESS'     ,  399.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '9 days'),
    (2000522, 5000005, 'TXN-SIPHO-MANUAL-PRE60',   'CAPITEC VEHICLE LOAN', 4800.00, 'ZAR', 'STANDARD_EFT', 'LOAN',          'COMPLETED', now() - INTERVAL '5 days'),
    (2000523, 5000005, 'TXN-SIPHO-EDGE60',         'TAKEALOT'           ,  825.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '58 days'),
    (2000524, 5000005, 'TXN-SIPHO-POST60',         'OLD FINANCE LOAN'   , 3400.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '96 days'),
    (2000525, 5000005, 'TXN-SIPHO-NODISPUTE-GAMB', 'HOLLYWOODBETS'      ,  480.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '7 days'),
    (2000526, 5000005, 'TXN-SIPHO-NODISPUTE-POS',  'WOOLWORTHS'         , 1065.90, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '1 days'),
    -- Realistic filler
    (2000527, 5000005, 'TXN-SIPHO-MTN-527',  'MTN'                ,  599.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000528, 5000005, 'TXN-SIPHO-INS-528',  'DISCOVERY INSURE'   ,  940.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '8 days'),
    (2000529, 5000005, 'TXN-SIPHO-MUN-529',  'CITY OF CAPE TOWN'  , 1760.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '10 days'),
    (2000530, 5000005, 'TXN-SIPHO-SHO-530',  'CHECKERS'           ,  388.45, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000531, 5000005, 'TXN-SIPHO-UE-531',   'UBER EATS'          ,  210.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '3 days'),
    (2000532, 5000005, 'TXN-SIPHO-ESK-532',  'ESKOM'              , 1290.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '15 days'),
    (2000533, 5000005, 'TXN-SIPHO-NET-533',  'NETFLIX'            ,  199.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '22 days'),
    (2000534, 5000005, 'TXN-SIPHO-MTN-534',  'MTN'                ,  599.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '34 days'),
    (2000535, 5000005, 'TXN-SIPHO-P2P-535',  'EFT TO S DLAMINI'   ,  750.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',    'COMPLETED', now() - INTERVAL '14 days');

-- ---- Lerato Mahlangu (account 5000006, SAVINGS_PLAN) -----------------------
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    -- Demo-critical, self-labelled scenarios
    (2000541, 5000006, 'TXN-LERATO-AUTO-PRE60',     'SILVER SNEAKERS GYM',  299.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '8 days'),
    (2000542, 5000006, 'TXN-LERATO-MANUAL-PRE60',   'FNB PERSONAL LOAN'  , 2700.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '4 days'),
    (2000543, 5000006, 'TXN-LERATO-EDGE60',         'TAKEALOT'           ,  590.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '56 days'),
    (2000544, 5000006, 'TXN-LERATO-POST60',         'OLD CLOTHING STORE' ,  480.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '98 days'),
    (2000545, 5000006, 'TXN-LERATO-NODISPUTE-GAMB', 'BETWAY'             ,  260.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING',        'COMPLETED', now() - INTERVAL '7 days'),
    (2000546, 5000006, 'TXN-LERATO-NODISPUTE-P2P',  'EFT TO L MAHLANGU'  ,  520.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER',    'COMPLETED', now() - INTERVAL '13 days'),
    -- Realistic filler
    (2000547, 5000006, 'TXN-LERATO-VOD-547',  'VODACOM'            ,  549.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000548, 5000006, 'TXN-LERATO-INS-548',  'OUTSURANCE'         ,  710.00, 'ZAR', 'DEBICHECK',    'LOAN',            'COMPLETED', now() - INTERVAL '6 days'),
    (2000549, 5000006, 'TXN-LERATO-MUN-549',  'ETHEKWINI MUNICIPAL', 1510.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '9 days'),
    (2000550, 5000006, 'TXN-LERATO-SHO-550',  'SHOPRITE'           ,  402.30, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000551, 5000006, 'TXN-LERATO-UE-551',   'UBER EATS'          ,  178.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY',   'COMPLETED', now() - INTERVAL '1 days'),
    (2000552, 5000006, 'TXN-LERATO-ESK-552',  'ESKOM'              ,  830.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL',       'COMPLETED', now() - INTERVAL '16 days'),
    (2000553, 5000006, 'TXN-LERATO-SPO-553',  'SPOTIFY'            ,   69.99, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '24 days'),
    (2000554, 5000006, 'TXN-LERATO-TAK-554',  'TAKEALOT'           , 1340.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL',   'COMPLETED', now() - INTERVAL '32 days'),
    (2000555, 5000006, 'TXN-LERATO-VOD-555',  'VODACOM'            ,  549.00, 'ZAR', 'DEBICHECK',    'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '36 days');

-- ---- Advance sequences past the seeded IDs ---------------------------------
-- So that newly registered customers / provisioned accounts / future
-- transactions / disputes do NOT collide with the explicit IDs above.
SELECT setval('customer_seq',    1000100, true);
SELECT setval('account_seq',     5000100, true);
SELECT setval('transaction_seq', 2000600, true);
SELECT setval('dispute_seq',     3000100, true);
