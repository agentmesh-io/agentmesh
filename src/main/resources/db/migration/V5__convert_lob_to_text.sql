-- Migration: Convert Large Object OIDs in blackboard_entry.content to actual TEXT content
-- This fixes the issue where @Lob annotation caused Hibernate to store content as PostgreSQL Large Objects

-- Step 1: Create a temporary function to convert LOB OIDs to TEXT
CREATE OR REPLACE FUNCTION convert_lob_to_text() RETURNS void AS $$
DECLARE
    r RECORD;
    lob_oid OID;
    lob_content TEXT;
BEGIN
    -- Process each blackboard_entry that has a numeric content (LOB OID)
    FOR r IN 
        SELECT id, content 
        FROM blackboard_entry 
        WHERE content ~ '^\d+$'  -- Match only numeric strings (LOB OIDs)
    LOOP
        BEGIN
            -- Try to convert the content to an OID and fetch the large object
            lob_oid := r.content::INTEGER::OID;
            
            -- Check if this OID exists in pg_largeobject_metadata
            IF EXISTS (SELECT 1 FROM pg_largeobject_metadata WHERE oid = lob_oid) THEN
                -- Fetch the actual content from the large object
                lob_content := convert_from(lo_get(lob_oid), 'UTF8');
                
                -- Update the blackboard_entry with the actual content
                UPDATE blackboard_entry 
                SET content = lob_content 
                WHERE id = r.id;
                
                RAISE NOTICE 'Converted LOB OID % to TEXT for entry %', lob_oid, r.id;
                
                -- Delete the large object to free up space
                PERFORM lo_unlink(lob_oid);
            ELSE
                RAISE NOTICE 'LOB OID % not found for entry %, skipping', lob_oid, r.id;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Error processing entry %: %', r.id, SQLERRM;
        END;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- Step 2: Execute the conversion
SELECT convert_lob_to_text();

-- Step 3: Drop the temporary function
DROP FUNCTION convert_lob_to_text();

-- Step 4: Add a comment to document the change
COMMENT ON COLUMN blackboard_entry.content IS 'Stores blackboard entry content as plain TEXT (not LOB)';
