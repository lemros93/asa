-- Cucumber Test Results Database

CREATE TABLE IF NOT EXISTS runs (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    project             VARCHAR(200)    NOT NULL,
    project_type        VARCHAR(20)     NOT NULL DEFAULT 'web',
    run_at              DATETIME        NOT NULL,
    tests_total_ms      BIGINT,
    build_duration_ms   BIGINT,
    triggered_by        VARCHAR(200),
    trigger_type        VARCHAR(100),
    branch              VARCHAR(500),
    commit_hash         VARCHAR(100),
    environment         VARCHAR(200),
    app_url             TEXT,
    build_number        VARCHAR(200),
    parallel_mode       VARCHAR(20)     DEFAULT 'serial',
    thread_count        INT             DEFAULT 1,
    jira_reporting      TINYINT(1)      DEFAULT 0,
    jira_new_execution  TINYINT(1)      DEFAULT 0,
    jira_cycle_ids      VARCHAR(1000),
    cucumber_tags       VARCHAR(1000),
    tested_feature      TEXT,
    test_properties     TEXT,

    total_features      INT             DEFAULT 0,
    passed_features     INT             DEFAULT 0,
    failed_features     INT             DEFAULT 0,
    total_scenarios     INT             DEFAULT 0,
    passed_scenarios    INT             DEFAULT 0,
    failed_scenarios    INT             DEFAULT 0,
    total_steps         INT             DEFAULT 0,
    passed_steps        INT             DEFAULT 0,
    failed_steps        INT             DEFAULT 0,
    skipped_steps       INT             DEFAULT 0,

    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS run_metadata (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    run_id      INT             NOT NULL,
    meta_key    VARCHAR(200)    NOT NULL,
    meta_value  TEXT,
    FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS features (
    id                  INT AUTO_INCREMENT PRIMARY KEY,
    run_id              INT             NOT NULL,
    name                VARCHAR(1000)   NOT NULL,
    file_path           TEXT,
    status              VARCHAR(20)     NOT NULL,
    duration_ms         BIGINT,
    tags                VARCHAR(1000),

    total_scenarios     INT             DEFAULT 0,
    passed_scenarios    INT             DEFAULT 0,
    failed_scenarios    INT             DEFAULT 0,

    FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS scenarios (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    feature_id      INT             NOT NULL,
    name            VARCHAR(1000)   NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    line            INT,
    started_at      BIGINT,
    duration_ms     BIGINT,
    thread_id       VARCHAR(200),
    tags            VARCHAR(1000),
    hook_error_type     VARCHAR(20),
    hook_error_message  TEXT,

    total_steps     INT             DEFAULT 0,
    passed_steps    INT             DEFAULT 0,
    failed_steps    INT             DEFAULT 0,
    skipped_steps   INT             DEFAULT 0,

    FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS steps (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    scenario_id           INT             NOT NULL,
    keyword               VARCHAR(50),
    name                  VARCHAR(2000)   NOT NULL,
    translated_text       VARCHAR(2000),
    line                  INT             NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    duration_ms           BIGINT,
    error_message         TEXT,
    stack_trace           MEDIUMTEXT,
    screenshot_url        TEXT,
    failed_screenshot_url TEXT,

    FOREIGN KEY (scenario_id) REFERENCES scenarios(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_runs_project         ON runs(project);
CREATE INDEX IF NOT EXISTS idx_runs_run_at          ON runs(run_at);
CREATE INDEX IF NOT EXISTS idx_features_run_id      ON features(run_id);
CREATE INDEX IF NOT EXISTS idx_features_status      ON features(status);
CREATE INDEX IF NOT EXISTS idx_scenarios_feature_id ON scenarios(feature_id);
CREATE INDEX IF NOT EXISTS idx_scenarios_status     ON scenarios(status);
CREATE INDEX IF NOT EXISTS idx_steps_scenario_id    ON steps(scenario_id);
