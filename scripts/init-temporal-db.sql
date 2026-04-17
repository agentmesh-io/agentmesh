-- Create databases for Temporal
-- Temporal requires these databases to store workflow data

-- Main Temporal database
CREATE DATABASE temporal;

-- Temporal visibility database (for search/list operations)
CREATE DATABASE temporal_visibility;

-- Grant all privileges to the agentmesh user
GRANT ALL PRIVILEGES ON DATABASE temporal TO agentmesh;
GRANT ALL PRIVILEGES ON DATABASE temporal_visibility TO agentmesh;

