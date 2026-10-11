-- ============================================================================
-- V4__seed_local_data.sql
--
-- NOTE: numbered V4 so it is unique within the local profile's merged Flyway
-- location set (classpath:db/migration,classpath:db/seed). It must sort AFTER
-- the schema migrations V1 (schema), V2 (category column), V3 (refund audit)
-- so the columns it populates already exist. Runs ONLY under the local profile.
--
-- LOCAL / DOCKER ONLY seed data. This migration lives in classpath:db/seed,
-- which is added to Flyway's locations ONLY by the `local` profile
-- (application-local.yaml). It is NOT on the production migration path
-- (classpath:db/migration) and NOT on the H2 test path (classpath:db/migration-h2),
-- so it never runs against production or the test suite.
--
-- Seeds 3 customers (shared password: Password123!), one account each, and a
-- realistic mix of transactions. mandate_type reflects how the money moves:
--   * DEBICHECK    -> authenticated recurring debit-order mandate (gyms, insurance,
--                     credit instalments). Base risk score 30.
--   * STANDARD_EFT -> standard debit order / non-mandated pull (loans here, and the
--                     least-wrong fit for POS/online card purchases, since the model
--                     has no CARD type). Base risk score 50.
-- NOTE: a Pick n Pay / Takealot purchase is a point-of-sale/online CARD payment, not
-- a DebiCheck mandate - so those rows use STANDARD_EFT, not DEBICHECK.
-- Category eligibility (not mandate type) decides disputability:
--   disputable: ONLINE_RETAIL, GYM_MEMBERSHIP, LOAN, MOBILE_CONTRACT, MUNICIPAL
--   NOT disputable: IN_STORE_RETAIL, P2P_TRANSFER
-- Each disputable transaction can be disputed ONCE (duplicate guard returns 400).
--
-- Account types use Capitec's real-world personal product names:
--   * MAIN_ACCOUNT  - Capitec's primary transactional/savings account (ex "Global One")
--   * SAVINGS_PLAN  - a named savings plan account
-- (see https://www.capitecbank.co.za/personal/save/main-account/)
--
-- The password_hash below is a BCrypt hash of "Password123!" produced by the
-- application's own BCryptPasswordEncoder.
-- ============================================================================

-- Shared BCrypt hash of "Password123!"
-- $2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe

-- ---- Customers --------------------------------------------------------------
INSERT INTO customers (customer_id, customer_number, first_name, last_name, email, password_hash) VALUES
    (1000001, 'CUST-TREVOR-001', 'Trevor', 'Sesoko', 'trevor.sesoko@example.co.za', '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000002, 'CUST-MESUT-002',  'Mesut',  'Ozil',   'mesut.ozil@example.co.za',    '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe'),
    (1000003, 'CUST-DONALD-003', 'Donald', 'Duck',   'donald.duck@example.co.za',   '$2a$10$Z3lDZ/EdlZvvCk7kCp4CyuYwlxvfaEMYE1n1KEuGSw3vT4pAUieRe');

-- ---- Accounts (one per customer, real Capitec personal product types) -------
INSERT INTO accounts (account_id, customer_id, account_number, account_type, balance, currency) VALUES
    (5000001, 1000001, '1001000001', 'MAIN_ACCOUNT', 25000.00, 'ZAR'),
    (5000002, 1000002, '1001000002', 'MAIN_ACCOUNT', 18000.00, 'ZAR'),
    (5000003, 1000003, '1001000003', 'SAVINGS_PLAN',  9500.00, 'ZAR');

-- ---- Transactions -----------------------------------------------------------
-- 26 transactions per account (78 total). mandate_type reflects how money moves;
-- category drives disputability. Disputable: ONLINE_RETAIL, FOOD_DELIVERY, GYM_MEMBERSHIP,
-- MOBILE_CONTRACT, LOAN, MUNICIPAL. NOT disputable: IN_STORE_RETAIL, P2P_TRANSFER, GAMBLING.
-- Note: insurance debits reuse LOAN category (no dedicated insurance category in the model).

-- Trevor Sesoko (account 5000001) - 26 transactions
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000001, 5000001, 'TXN-TREVOR-DC-001', 'VIRGIN ACTIVE GYM'       ,    450.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '10 days'),
    (2000002, 5000001, 'TXN-TREVOR-LOAN-002', 'ABSA HOME LOAN'          ,   8200.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '7 days'),
    (2000003, 5000001, 'TXN-TREVOR-PNP-003', 'PICK N PAY'              ,    860.75, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000010, 5000001, 'TXN-TREVOR-TAK-010', 'TAKEALOT'                ,   1299.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000011, 5000001, 'TXN-TREVOR-OLD-011', 'OLD FINANCE LOAN'        ,   2500.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '90 days'),
    (2000012, 5000001, 'TXN-TREVOR-MTN-012', 'MTN'                     ,    599.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000013, 5000001, 'TXN-TREVOR-INS-013', 'MOMENTUM INSURANCE'      ,    780.00, 'ZAR', 'DEBICHECK', 'LOAN', 'COMPLETED', now() - INTERVAL '12 days'),
    (2000014, 5000001, 'TXN-TREVOR-MUN-014', 'CITY OF JOHANNESBURG'    ,   2150.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '14 days'),
    (2000015, 5000001, 'TXN-TREVOR-SHO-015', 'SHOPRITE'                ,    645.20, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000016, 5000001, 'TXN-TREVOR-UE-016', 'UBER EATS'               ,    289.50, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '1 days'),
    (2000017, 5000001, 'TXN-TREVOR-BET-017', 'BETWAY'                  ,    500.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '6 days'),
    (2000018, 5000001, 'TXN-TREVOR-HB-018', 'HOLLYWOODBETS'           ,    250.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '9 days'),
    (2000019, 5000001, 'TXN-TREVOR-TAK-019', 'TAKEALOT'                ,    430.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '16 days'),
    (2000020, 5000001, 'TXN-TREVOR-UE-020', 'UBER EATS'               ,    175.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '18 days'),
    (2000021, 5000001, 'TXN-TREVOR-ESK-021', 'ESKOM'                   ,   1142.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '20 days'),
    (2000022, 5000001, 'TXN-TREVOR-SHO-022', 'SHOPRITE'                ,    980.40, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '22 days'),
    (2000023, 5000001, 'TXN-TREVOR-GYM-023', 'VIRGIN ACTIVE GYM'       ,    450.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '40 days'),
    (2000024, 5000001, 'TXN-TREVOR-MTN-024', 'MTN'                     ,    599.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '35 days'),
    (2000025, 5000001, 'TXN-TREVOR-BET-025', 'BETWAY'                  ,   1000.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '24 days'),
    (2000026, 5000001, 'TXN-TREVOR-NET-026', 'NETFLIX'                 ,    199.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '26 days'),
    (2000027, 5000001, 'TXN-TREVOR-UE-027', 'UBER EATS'               ,    210.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '28 days'),
    (2000028, 5000001, 'TXN-TREVOR-HB-028', 'HOLLYWOODBETS'           ,    320.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '30 days'),
    (2000029, 5000001, 'TXN-TREVOR-SHO-029', 'SHOPRITE'                ,    512.75, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '33 days'),
    (2000030, 5000001, 'TXN-TREVOR-MUN-030', 'JOBURG WATER'            ,    890.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '38 days'),
    (2000031, 5000001, 'TXN-TREVOR-TAK-031', 'TAKEALOT'                ,   1560.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '45 days'),
    (2000032, 5000001, 'TXN-TREVOR-P2P-032', 'EFT TO T MOKOENA'        ,   1200.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER', 'COMPLETED', now() - INTERVAL '11 days');

-- Mesut Ozil (account 5000002) - 26 transactions
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000004, 5000002, 'TXN-MESUT-DC-004', 'PLANET FITNESS'          ,    399.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '9 days'),
    (2000005, 5000002, 'TXN-MESUT-LOAN-005', 'CAPITEC VEHICLE LOAN'    ,   5600.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '6 days'),
    (2000006, 5000002, 'TXN-MESUT-PNP-006', 'PICK N PAY'              ,   1240.30, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '1 days'),
    (2000040, 5000002, 'TXN-MESUT-VOD-040', 'VODACOM'                 ,    699.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000041, 5000002, 'TXN-MESUT-INS-041', 'DISCOVERY INSURE'        ,    899.00, 'ZAR', 'DEBICHECK', 'LOAN', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000042, 5000002, 'TXN-MESUT-MUN-042', 'CITY OF CAPE TOWN'       ,   1870.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '8 days'),
    (2000043, 5000002, 'TXN-MESUT-SHO-043', 'SHOPRITE'                ,    432.10, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000044, 5000002, 'TXN-MESUT-UE-044', 'UBER EATS'               ,    198.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000045, 5000002, 'TXN-MESUT-BET-045', 'BETWAY'                  ,    750.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '7 days'),
    (2000046, 5000002, 'TXN-MESUT-HB-046', 'HOLLYWOODBETS'           ,    400.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '10 days'),
    (2000047, 5000002, 'TXN-MESUT-TAK-047', 'TAKEALOT'                ,    899.99, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '12 days'),
    (2000048, 5000002, 'TXN-MESUT-GYM-048', 'PLANET FITNESS'          ,    399.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '39 days'),
    (2000049, 5000002, 'TXN-MESUT-VOD-049', 'VODACOM'                 ,    699.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '33 days'),
    (2000050, 5000002, 'TXN-MESUT-ESK-050', 'ESKOM'                   ,   1340.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '15 days'),
    (2000051, 5000002, 'TXN-MESUT-UE-051', 'UBER EATS'               ,    265.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '17 days'),
    (2000052, 5000002, 'TXN-MESUT-SHO-052', 'SHOPRITE'                ,    720.60, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '19 days'),
    (2000053, 5000002, 'TXN-MESUT-BET-053', 'BETWAY'                  ,    300.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '21 days'),
    (2000054, 5000002, 'TXN-MESUT-NET-054', 'NETFLIX'                 ,    199.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '23 days'),
    (2000055, 5000002, 'TXN-MESUT-HB-055', 'HOLLYWOODBETS'           ,    150.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '27 days'),
    (2000056, 5000002, 'TXN-MESUT-TAK-056', 'TAKEALOT'                ,   2100.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '31 days'),
    (2000057, 5000002, 'TXN-MESUT-MUN-057', 'CITY OF CAPE TOWN'       ,   1650.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '44 days'),
    (2000058, 5000002, 'TXN-MESUT-LOAN-058', 'OLD FINANCE LOAN'        ,   3200.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '95 days'),
    (2000059, 5000002, 'TXN-MESUT-UE-059', 'UBER EATS'               ,    142.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '25 days'),
    (2000060, 5000002, 'TXN-MESUT-SHO-060', 'SHOPRITE'                ,    388.90, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '29 days'),
    (2000061, 5000002, 'TXN-MESUT-P2P-061', 'EFT TO M OZIL'           ,    900.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER', 'COMPLETED', now() - INTERVAL '13 days'),
    (2000062, 5000002, 'TXN-MESUT-GYM-062', 'PLANET FITNESS'          ,    399.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '69 days');

-- Donald Duck (account 5000003) - 26 transactions
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000007, 5000003, 'TXN-DONALD-DC-007', 'SILVER SNEAKERS GYM'     ,    299.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '8 days'),
    (2000008, 5000003, 'TXN-DONALD-LOAN-008', 'FNB PERSONAL LOAN'       ,   3100.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '5 days'),
    (2000009, 5000003, 'TXN-DONALD-PNP-009', 'PICK N PAY'              ,    540.00, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '3 days'),
    (2000070, 5000003, 'TXN-DONALD-VOD-070', 'VODACOM'                 ,    499.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000071, 5000003, 'TXN-DONALD-INS-071', 'OUTSURANCE'              ,    640.00, 'ZAR', 'DEBICHECK', 'LOAN', 'COMPLETED', now() - INTERVAL '6 days'),
    (2000072, 5000003, 'TXN-DONALD-MUN-072', 'ETHEKWINI MUNICIPAL'     ,   1420.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '9 days'),
    (2000073, 5000003, 'TXN-DONALD-SHO-073', 'SHOPRITE'                ,    365.80, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days'),
    (2000074, 5000003, 'TXN-DONALD-UE-074', 'UBER EATS'               ,    155.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '1 days'),
    (2000075, 5000003, 'TXN-DONALD-BET-075', 'BETWAY'                  ,    220.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '7 days'),
    (2000076, 5000003, 'TXN-DONALD-HB-076', 'HOLLYWOODBETS'           ,    180.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '11 days'),
    (2000077, 5000003, 'TXN-DONALD-TAK-077', 'TAKEALOT'                ,    675.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '13 days'),
    (2000078, 5000003, 'TXN-DONALD-GYM-078', 'SILVER SNEAKERS GYM'     ,    299.00, 'ZAR', 'DEBICHECK', 'GYM_MEMBERSHIP', 'COMPLETED', now() - INTERVAL '38 days'),
    (2000079, 5000003, 'TXN-DONALD-VOD-079', 'VODACOM'                 ,    499.00, 'ZAR', 'DEBICHECK', 'MOBILE_CONTRACT', 'COMPLETED', now() - INTERVAL '34 days'),
    (2000080, 5000003, 'TXN-DONALD-ESK-080', 'ESKOM'                   ,    760.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '16 days'),
    (2000081, 5000003, 'TXN-DONALD-UE-081', 'UBER EATS'               ,    132.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '18 days'),
    (2000082, 5000003, 'TXN-DONALD-SHO-082', 'SHOPRITE'                ,    421.30, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '20 days'),
    (2000083, 5000003, 'TXN-DONALD-BET-083', 'BETWAY'                  ,     90.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '22 days'),
    (2000084, 5000003, 'TXN-DONALD-NET-084', 'SPOTIFY'                 ,     69.99, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '24 days'),
    (2000085, 5000003, 'TXN-DONALD-HB-085', 'HOLLYWOODBETS'           ,    140.00, 'ZAR', 'STANDARD_EFT', 'GAMBLING', 'COMPLETED', now() - INTERVAL '28 days'),
    (2000086, 5000003, 'TXN-DONALD-TAK-086', 'TAKEALOT'                ,   1180.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '32 days'),
    (2000087, 5000003, 'TXN-DONALD-MUN-087', 'ETHEKWINI MUNICIPAL'     ,   1510.00, 'ZAR', 'STANDARD_EFT', 'MUNICIPAL', 'COMPLETED', now() - INTERVAL '43 days'),
    (2000088, 5000003, 'TXN-DONALD-LOAN-088', 'FNB PERSONAL LOAN'       ,   3100.00, 'ZAR', 'STANDARD_EFT', 'LOAN', 'COMPLETED', now() - INTERVAL '36 days'),
    (2000089, 5000003, 'TXN-DONALD-UE-089', 'UBER EATS'               ,     98.00, 'ZAR', 'STANDARD_EFT', 'FOOD_DELIVERY', 'COMPLETED', now() - INTERVAL '26 days'),
    (2000090, 5000003, 'TXN-DONALD-SHO-090', 'SHOPRITE'                ,    655.40, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '30 days'),
    (2000091, 5000003, 'TXN-DONALD-P2P-091', 'EFT TO D DUCK'           ,    450.00, 'ZAR', 'STANDARD_EFT', 'P2P_TRANSFER', 'COMPLETED', now() - INTERVAL '14 days'),
    (2000092, 5000003, 'TXN-DONALD-OLD-092', 'OLD CLOTHING STORE'      ,    410.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '100 days');

-- ---- Advance sequences past the seeded IDs ---------------------------------
-- So that newly registered customers / provisioned accounts / future
-- transactions / disputes do NOT collide with the explicit IDs above.
SELECT setval('customer_seq',    1000100, true);
SELECT setval('account_seq',     5000100, true);
SELECT setval('transaction_seq', 2000200, true);
SELECT setval('dispute_seq',     3000100, true);