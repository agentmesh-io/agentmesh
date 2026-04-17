-- Fix content column from TEXT (which PostgreSQL stores as OID) to VARCHAR
-- This prevents Hibernate from using Large Object storage

-- First, convert existing LOB OIDs to empty JSON (can't retrieve LOB data after column type change)
UPDATE blackboard_entry 
SET content = '{}'
WHERE entry_type = 'CODE' 
  AND length(content) < 10 
  AND content ~ '^\d+$';

-- Alter column to VARCHAR (forces Hibernate to use inline storage)
ALTER TABLE blackboard_entry 
ALTER COLUMN content TYPE VARCHAR(100000);

-- Verify no LOB OIDs remain
DO $$
DECLARE
    lob_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO lob_count
    FROM blackboard_entry
    WHERE content ~ '^\d+$' AND length(content) < 10;
    
    IF lob_count > 0 THEN
        RAISE EXCEPTION 'Found % LOB OID references after migration', lob_count;
    END IF;
END $$;
