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
-- Per customer: a DEBICHECK gym mandate, a STANDARD_EFT loan, and a STANDARD_EFT
-- retail (Pick n Pay) card purchase. Gym + loan are disputable; the in-store Pick n
-- Pay is NOT (IN_STORE_RETAIL).

-- Trevor Sesoko (account 5000001)
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000001, 5000001, 'TXN-TREVOR-DC-001',  'VIRGIN ACTIVE GYM',   450.00,  'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '10 days'),
    (2000002, 5000001, 'TXN-TREVOR-LOAN-002','ABSA HOME LOAN',      8200.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '7 days'),
    (2000003, 5000001, 'TXN-TREVOR-PNP-003', 'PICK N PAY ASPARAGUS',860.75,  'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '2 days');

-- Extra Trevor transactions to exercise the regulatory rules:
--   2000010 ONLINE_RETAIL (disputable), STANDARD_EFT, recent -> dispute leans MANUAL (score 50+)
--   2000011 LOAN (disputable), STANDARD_EFT, dated 90 days ago -> dispute = 400 window closed (past 60 days)
-- For the AUTO_APPROVE + refund demo use a GYM DebiCheck txn (e.g. 2000001) with reason
-- INCORRECT_DATE: DEBICHECK 30 + 5 = 35 (<=50) -> AUTO_APPROVE and credits the balance.
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000010, 5000001, 'TXN-TREVOR-TAK-010', 'TAKEALOT',          1299.00, 'ZAR', 'STANDARD_EFT', 'ONLINE_RETAIL', 'COMPLETED', now() - INTERVAL '4 days'),
    (2000011, 5000001, 'TXN-TREVOR-OLD-011', 'OLD FINANCE LOAN',  2500.00, 'ZAR', 'STANDARD_EFT', 'LOAN',          'COMPLETED', now() - INTERVAL '90 days');
-- Mesut Ozil (account 5000002)
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000004, 5000002, 'TXN-MESUT-DC-004',  'PLANET FITNESS',       399.00,  'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '9 days'),
    (2000005, 5000002, 'TXN-MESUT-LOAN-005','CAPITEC VEHICLE LOAN', 5600.00, 'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '6 days'),
    (2000006, 5000002, 'TXN-MESUT-PNP-006', 'PICK N PAY HYPER',     1240.30, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '1 days');

-- Donald Duck (account 5000003) - has transactions but NO disputes yet
INSERT INTO transactions (transaction_id, account_id, reference, merchant, amount, currency, mandate_type, category, status, transaction_date) VALUES
    (2000007, 5000003, 'TXN-DONALD-DC-007',  'SILVER SNEAKERS GYM', 299.00, 'ZAR', 'DEBICHECK',    'GYM_MEMBERSHIP',  'COMPLETED', now() - INTERVAL '8 days'),
    (2000008, 5000003, 'TXN-DONALD-LOAN-008','FNB PERSONAL LOAN',   3100.00,'ZAR', 'STANDARD_EFT', 'LOAN',            'COMPLETED', now() - INTERVAL '5 days'),
    (2000009, 5000003, 'TXN-DONALD-PNP-009', 'PICK N PAY EXPRESS',  540.00, 'ZAR', 'STANDARD_EFT', 'IN_STORE_RETAIL', 'COMPLETED', now() - INTERVAL '3 days');

-- ---- Advance sequences past the seeded IDs ---------------------------------
-- So that newly registered customers / provisioned accounts / future
-- transactions / disputes do NOT collide with the explicit IDs above.
SELECT setval('customer_seq',    1000100, true);
SELECT setval('account_seq',     5000100, true);
SELECT setval('transaction_seq', 2000100, true);
SELECT setval('dispute_seq',     3000100, true);