-- Read-only checks for the A01 policy and an immutable card type.
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY;

-- Q1: both the aggregate and its materialized counter must be safe.
SELECT s.id, s.booked_persons, s.max_persons,
       COALESCE(SUM(CASE WHEN r.status IN (1,3) THEN r.persons ELSE 0 END),0) AS occupied
FROM t_schedule s LEFT JOIN t_reservation r ON r.schedule_id=s.id
GROUP BY s.id,s.booked_persons,s.max_persons
HAVING booked_persons<>occupied OR booked_persons<0 OR max_persons<=0
    OR booked_persons>max_persons OR occupied>max_persons;

-- Q2: annual cards do not carry a times balance.
SELECT id FROM t_member_card
WHERE card_type=1 AND (remaining_times IS NULL OR remaining_times<0);
SELECT id FROM t_reservation WHERE persons<=0 OR status NOT IN (1,2,3);
SELECT id FROM t_member_card_transaction
WHERE after_times<>before_times+change_times OR before_times<0 OR after_times<0
   OR change_type NOT IN (1,2)
   OR (change_type=1 AND change_times>=0) OR (change_type=2 AND change_times<=0);

-- Q3: one original deduction and at most one matching refund, with original ownership.
WITH amounts AS (
 SELECT r.id,r.persons,r.status,r.member_id,r.member_card_id,c.card_type,
        SUM(CASE WHEN t.change_type=1 THEN 1 ELSE 0 END) AS nd,
        SUM(CASE WHEN t.change_type=2 THEN 1 ELSE 0 END) AS nr,
        COALESCE(SUM(CASE WHEN t.change_type=1 THEN -t.change_times ELSE 0 END),0) AS d,
        COALESCE(SUM(CASE WHEN t.change_type=2 THEN t.change_times ELSE 0 END),0) AS refund,
        SUM(CASE WHEN t.id IS NOT NULL AND
             (t.member_card_id<>r.member_card_id OR t.member_id<>r.member_id OR t.persons<>r.persons)
             THEN 1 ELSE 0 END) AS mismatch
 FROM t_reservation r JOIN t_member_card c ON c.id=r.member_card_id
 LEFT JOIN t_member_card_transaction t ON t.reservation_id=r.id
 GROUP BY r.id,r.persons,r.status,r.member_id,r.member_card_id,c.card_type
)
SELECT * FROM amounts
WHERE mismatch>0 OR nd>1 OR nr>1
   OR (card_type=1 AND (nd<>1 OR d<>persons OR refund<>IF(status=2,d,0)))
   OR (card_type=2 AND (nd<>0 OR nr<>0 OR d<>0 OR refund<>0));

-- Q4: closed schedules with live reservations are incomplete under synchronous A01.
SELECT r.id,r.schedule_id,r.status FROM t_reservation r
JOIN t_schedule s ON s.id=r.schedule_id WHERE s.status=3 AND r.status IN (1,3);

-- Q5: uniqueness queries remain useful even when the expected indexes are absent.
SELECT member_id,request_id,COUNT(*) AS n FROM t_reservation
GROUP BY member_id,request_id HAVING n>1;
SELECT member_id,schedule_id,COUNT(*) AS n FROM t_reservation WHERE status IN (1,3)
GROUP BY member_id,schedule_id HAVING n>1;
SELECT reservation_id,change_type,COUNT(*) AS n FROM t_member_card_transaction
GROUP BY reservation_id,change_type HAVING n>1;

-- Q6: only for a complete ledger with no funding/adjustments after opening.
SELECT c.id,c.remaining_times,c.purchase_times,c.gift_times,COALESCE(SUM(t.change_times),0) AS delta
FROM t_member_card c LEFT JOIN t_member_card_transaction t ON t.member_card_id=c.id
WHERE c.card_type=1
GROUP BY c.id,c.remaining_times,c.purchase_times,c.gift_times
HAVING purchase_times IS NULL OR gift_times IS NULL
    OR remaining_times<>purchase_times+gift_times+delta;

-- Q7: references must remain traceable.
SELECT r.id FROM t_reservation r
LEFT JOIN t_schedule s ON s.id=r.schedule_id
LEFT JOIN t_member_card c ON c.id=r.member_card_id
LEFT JOIN t_member m ON m.id=r.member_id
WHERE s.id IS NULL OR c.id IS NULL OR m.id IS NULL OR c.member_id<>r.member_id;
SELECT t.id FROM t_member_card_transaction t
LEFT JOIN t_reservation r ON r.id=t.reservation_id
WHERE r.id IS NULL;

COMMIT;
