-- AlgoVerse PostgreSQL initialization script
-- This script runs once when the postgres container is first created.

CREATE DATABASE auth_db;
CREATE DATABASE problem_db;
CREATE DATABASE submission_db;
CREATE DATABASE gamification_db;
CREATE DATABASE ai_db;
CREATE DATABASE analytics_db;
CREATE DATABASE sysdesign_db;

-- Grant all privileges to the algoverse user (set via POSTGRES_USER)
GRANT ALL PRIVILEGES ON DATABASE auth_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE problem_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE submission_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE gamification_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE ai_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE analytics_db TO algoverse;
GRANT ALL PRIVILEGES ON DATABASE sysdesign_db TO algoverse;
