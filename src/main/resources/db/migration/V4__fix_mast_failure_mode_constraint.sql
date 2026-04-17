-- V4: Fix MAST failure mode constraint to allow full enum names
-- The MASTFailureMode enum uses full names like FM_3_1_OUTPUT_QUALITY
-- but the original constraint only allowed short codes like FM_3_1

-- Drop the old constraint
ALTER TABLE mast_violations DROP CONSTRAINT IF EXISTS mast_violations_failure_mode_check;

-- Add new constraint with full enum names
ALTER TABLE mast_violations ADD CONSTRAINT mast_violations_failure_mode_check
    CHECK (failure_mode IN (
        -- Category 1: Specification Issues
        'FM_1_1_SPECIFICATION_VIOLATION',
        'FM_1_2_ROLE_VIOLATION',
        'FM_1_3_STEP_REPETITION',
        'FM_1_4_CONTEXT_LOSS',
        -- Category 2: Inter-Agent Misalignment
        'FM_2_1_COORDINATION_FAILURE',
        'FM_2_2_COMMUNICATION_BREAKDOWN',
        'FM_2_3_DEPENDENCY_VIOLATION',
        'FM_2_4_STATE_INCONSISTENCY',
        -- Category 3: Task Verification
        'FM_3_1_OUTPUT_QUALITY',
        'FM_3_2_INCOMPLETE_OUTPUT',
        'FM_3_3_FORMAT_VIOLATION',
        'FM_3_4_HALLUCINATION',
        'FM_3_5_TIMEOUT',
        'FM_3_6_TOOL_INVOCATION_FAILURE'
    ));
