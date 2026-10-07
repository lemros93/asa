package sk.moja.db.importer.db;

import sk.moja.db.importer.config.ImportConfig;
import sk.moja.db.importer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseWriter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseWriter.class);
    private final Connection conn;

    public DatabaseWriter(ImportConfig config) throws SQLException {
        log.info("Connecting to database: {}", config.dbUrl);
        this.conn = DriverManager.getConnection(config.dbUrl, config.dbUser, config.dbPassword);
        this.conn.setAutoCommit(false);
        log.info("Database connection established");
    }

    public void save(TestRun run, ImportConfig config) throws SQLException {
        try {
            long runId = insertRun(run, config);
            insertRunMetadata(runId, config);

            for (Feature feature : run.features) {
                long featureId = insertFeature(runId, feature);
                for (Scenario scenario : feature.scenarios) {
                    long scenarioId = insertScenario(featureId, scenario);
                    insertSteps(scenarioId, scenario);
                }
            }

            conn.commit();
            log.info("Successfully saved run ID={} to database", runId);

        } catch (SQLException e) {
            conn.rollback();
            log.error("Database error, transaction rolled back", e);
            throw e;
        }
    }

    private long insertRun(TestRun run, ImportConfig config) throws SQLException {
        String sql = """
            INSERT INTO runs (
                project, project_type, run_at,
                tests_total_ms, build_duration_ms,
                triggered_by, trigger_type,
                branch, commit_hash,
                environment, app_url, build_number,
                parallel_mode, thread_count,
                jira_reporting, jira_new_execution, jira_cycle_ids,
                cucumber_tags, tested_feature, test_properties,
                total_features,  passed_features,  failed_features,
                total_scenarios, passed_scenarios, failed_scenarios,
                total_steps,     passed_steps,     failed_steps,     skipped_steps
            ) VALUES (?, 'web', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        //       1          2    3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1,    config.project);
            ps.setTimestamp(2, Timestamp.valueOf(run.runAt));
            ps.setLong(3,      run.testsTotalMs);
            ps.setLong(4,      run.buildDurationMs);
            ps.setString(5,    config.triggeredBy);
            ps.setString(6,    config.triggerType);
            ps.setString(7,    trunc(config.branch,         500));
            ps.setString(8,    trunc(config.commitHash,      100));
            ps.setString(9,    trunc(config.environment,     200));
            ps.setString(10,   trunc(config.appUrl,        500));
            ps.setString(11,   trunc(config.buildNumber,     200));
            ps.setString(12,   trunc(config.parallelMode,     20));
            ps.setInt(13,      config.threadCount);
            ps.setBoolean(14,  config.jiraReporting);
            ps.setBoolean(15,  config.jiraNewExecution);
            ps.setString(16,   trunc(config.jiraCycleIds,   1000));
            ps.setString(17,   trunc(config.cucumberTags,   1000));
            ps.setString(18,   trunc(config.testedFeature,  1000));
            ps.setString(19,   trunc(config.testProperties, 1000));
            ps.setInt(20,      run.totalFeatures);
            ps.setInt(21,      run.passedFeatures);
            ps.setInt(22,      run.failedFeatures);
            ps.setInt(23,      run.totalScenarios);
            ps.setInt(24,      run.passedScenarios);
            ps.setInt(25,      run.failedScenarios);
            ps.setInt(26,      run.totalSteps);
            ps.setInt(27,      run.passedSteps);
            ps.setInt(28,      run.failedSteps);
            ps.setInt(29,      run.skippedSteps);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private void insertRunMetadata(long runId, ImportConfig config) throws SQLException {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("browser",         config.browser);
        metadata.put("browser_version", config.browserVersion);

        String sql = "INSERT INTO run_metadata (run_id, meta_key, meta_value) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                ps.setLong(1,   runId);
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private long insertFeature(long runId, Feature feature) throws SQLException {
        String sql = """
            INSERT INTO features (
                run_id, name, file_path, status, duration_ms, tags,
                total_scenarios, passed_scenarios, failed_scenarios
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1,   runId);
            ps.setString(2, trunc(feature.name,          1000));
            ps.setString(3, trunc(feature.filePath,   500));
            ps.setString(4, feature.status);
            ps.setLong(5,   feature.durationMs);
            ps.setString(6, trunc(feature.tags,          1000));
            ps.setInt(7,    feature.totalScenarios);
            ps.setInt(8,    feature.passedScenarios);
            ps.setInt(9,    feature.failedScenarios);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private long insertScenario(long featureId, Scenario scenario) throws SQLException {
        String sql = """
            INSERT INTO scenarios (
                feature_id, name, status, line, started_at, duration_ms, thread_id, tags,
                hook_error_type, hook_error_message,
                total_steps, passed_steps, failed_steps, skipped_steps
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1,   featureId);
            ps.setString(2, trunc(scenario.name,         1000));
            ps.setString(3, scenario.status);
            ps.setInt(4,    scenario.line);
            ps.setLong(5,   scenario.startedAt);
            ps.setLong(6,   scenario.durationMs);
            ps.setString(7, trunc(scenario.threadId,      200));
            ps.setString(8, trunc(scenario.tags,         1000));
            ps.setString(9, scenario.hookErrorType);
            ps.setString(10, scenario.hookErrorMessage);
            ps.setInt(11,   scenario.totalSteps);
            ps.setInt(12,   scenario.passedSteps);
            ps.setInt(13,   scenario.failedSteps);
            ps.setInt(14,   scenario.skippedSteps);
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getLong(1);
        }
    }

    private void insertSteps(long scenarioId, Scenario scenario) throws SQLException {
        String sql = """
            INSERT INTO steps (
                scenario_id, keyword, name, translated_text,
                line, status, duration_ms,
                error_message, stack_trace,
                screenshot_url, failed_screenshot_url
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Step step : scenario.steps) {
                ps.setLong(1,    scenarioId);
                ps.setString(2,  trunc(step.keyword,               50));
                ps.setString(3,  trunc(step.name,                2000));
                ps.setString(4,  trunc(step.translatedText,       2000));
                ps.setInt(5,     step.line);
                ps.setString(6,  trunc(step.status,                 20));
                ps.setLong(7,    step.durationMs);
                ps.setString(8,  step.errorMessage);   // TEXT — bez limitu
                ps.setString(9,  step.stackTrace);     // TEXT — bez limitu
                ps.setString(10, trunc(step.screenshotUrl,         2000));
                ps.setString(11, trunc(step.failedScreenshotUrl,   2000));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Skráti string na max znakov — ochrana pred Data too long */
    private static String trunc(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public void close() {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            log.warn("Error closing connection", e);
        }
    }
}
