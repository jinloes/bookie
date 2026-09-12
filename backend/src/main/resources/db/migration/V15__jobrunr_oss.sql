ALTER TABLE background_jobs ADD execution_id UUID;
ALTER TABLE background_jobs ADD execution_attempt_base INTEGER NOT NULL DEFAULT 0;
ALTER TABLE background_jobs ADD execution_previous_max_attempts INTEGER;
ALTER TABLE background_jobs ADD execution_started BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_background_jobs_execution ON background_jobs(execution_id);

-- Attempts and LEASED now report engine state; they no longer elect retries or own a lease.
ALTER TABLE background_jobs DROP CONSTRAINT ck_background_jobs_attempts;
ALTER TABLE background_jobs ADD CONSTRAINT ck_background_jobs_attempts
    CHECK (attempts >= 0 AND max_attempts > 0 AND execution_attempt_base >= 0);
ALTER TABLE background_jobs DROP CONSTRAINT ck_background_jobs_lease;

-- Final PUBLIC schema for JobRunr OSS 8.8.1 (common v000-v016, H2 overrides).
-- Flyway owns DDL; runtime storage uses SKIP_CREATE.
CREATE TABLE jobrunr_migrations (
    id NCHAR(36) PRIMARY KEY,
    script VARCHAR(64) NOT NULL,
    installedOn VARCHAR(29) NOT NULL
);
CREATE TABLE jobrunr_jobs (
    id NCHAR(36) PRIMARY KEY,
    version INT NOT NULL,
    jobAsJson TEXT NOT NULL,
    jobSignature VARCHAR(512) NOT NULL,
    state VARCHAR(36) NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    updatedAt TIMESTAMP NOT NULL,
    scheduledAt TIMESTAMP,
    recurringJobId VARCHAR(128)
);
CREATE INDEX jobrunr_state_idx ON jobrunr_jobs(state);
CREATE INDEX jobrunr_job_signature_idx ON jobrunr_jobs(jobSignature);
CREATE INDEX jobrunr_job_created_at_idx ON jobrunr_jobs(createdAt);
CREATE INDEX jobrunr_job_scheduled_at_idx ON jobrunr_jobs(scheduledAt);
CREATE INDEX jobrunr_job_rci_idx ON jobrunr_jobs(recurringJobId);
CREATE INDEX jobrunr_jobs_state_updated_idx ON jobrunr_jobs(state ASC, updatedAt ASC);

CREATE TABLE jobrunr_recurring_jobs (
    id NCHAR(128) PRIMARY KEY,
    version INT NOT NULL,
    jobAsJson TEXT NOT NULL,
    createdAt BIGINT NOT NULL DEFAULT '0'
);
CREATE INDEX jobrunr_recurring_job_created_at_idx ON jobrunr_recurring_jobs(createdAt);

CREATE TABLE jobrunr_backgroundjobservers (
    id NCHAR(36) PRIMARY KEY,
    workerPoolSize INT NOT NULL,
    pollIntervalInSeconds INT NOT NULL,
    firstHeartbeat TIMESTAMP(6) NOT NULL,
    lastHeartbeat TIMESTAMP(6) NOT NULL,
    running INT NOT NULL,
    systemTotalMemory BIGINT NOT NULL,
    systemFreeMemory BIGINT NOT NULL,
    systemCpuLoad NUMERIC(3, 2) NOT NULL,
    processMaxMemory BIGINT NOT NULL,
    processFreeMemory BIGINT NOT NULL,
    processAllocatedMemory BIGINT NOT NULL,
    processCpuLoad NUMERIC(3, 2) NOT NULL,
    deleteSucceededJobsAfter VARCHAR(32),
    permanentlyDeleteJobsAfter VARCHAR(32),
    name VARCHAR(128)
);
CREATE INDEX jobrunr_bgjobsrvrs_fsthb_idx ON jobrunr_backgroundjobservers(firstHeartbeat);
CREATE INDEX jobrunr_bgjobsrvrs_lsthb_idx ON jobrunr_backgroundjobservers(lastHeartbeat);

CREATE TABLE jobrunr_metadata (
    id VARCHAR(156) PRIMARY KEY,
    name VARCHAR(92) NOT NULL,
    owner VARCHAR(64) NOT NULL,
    `value` TEXT NOT NULL,
    createdAt TIMESTAMP NOT NULL,
    updatedAt TIMESTAMP NOT NULL
);
INSERT INTO jobrunr_metadata (id, name, owner, `value`, createdAt, updatedAt)
VALUES ('succeeded-jobs-counter-cluster', 'succeeded-jobs-counter', 'cluster',
        CAST(0 AS CHAR(10)), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE VIEW jobrunr_jobs_stats AS
SELECT COALESCE(SUM(count), 0) AS total,
       COALESCE(SUM(CASE WHEN state = 'AWAITING' THEN count ELSE 0 END), 0) AS awaiting,
       COALESCE(SUM(CASE WHEN state = 'SCHEDULED' THEN count ELSE 0 END), 0) AS scheduled,
       COALESCE(SUM(CASE WHEN state = 'ENQUEUED' THEN count ELSE 0 END), 0) AS enqueued,
       COALESCE(SUM(CASE WHEN state = 'PROCESSING' THEN count ELSE 0 END), 0) AS processing,
       COALESCE(SUM(CASE WHEN state = 'PROCESSED' THEN count ELSE 0 END), 0) AS processed,
       COALESCE(SUM(CASE WHEN state = 'FAILED' THEN count ELSE 0 END), 0) AS failed,
       COALESCE(SUM(CASE WHEN state = 'SUCCEEDED' THEN count ELSE 0 END), 0) AS succeeded,
       COALESCE((SELECT CAST(CAST(`value` AS CHAR(10)) AS DECIMAL(10, 0))
                 FROM jobrunr_metadata WHERE id = 'succeeded-jobs-counter-cluster'), 0)
           AS allTimeSucceeded,
       COALESCE(SUM(CASE WHEN state = 'DELETED' THEN count ELSE 0 END), 0) AS deleted,
       (SELECT COUNT(*) FROM jobrunr_backgroundjobservers) AS nbrOfBackgroundJobServers,
       (SELECT COUNT(*) FROM jobrunr_recurring_jobs) AS nbrOfRecurringJobs
FROM (SELECT state, COUNT(*) AS count FROM jobrunr_jobs GROUP BY state);
