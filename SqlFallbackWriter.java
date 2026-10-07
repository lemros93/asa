package sk.moja.db.importer.db;

import sk.moja.db.importer.config.ImportConfig;
import sk.moja.db.importer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Keď zápis do DB zlyhá, vygeneruje .sql súbor s celým importom.
 * Súbor je možné spustiť manuálne: mysql -u user -p automation < fallback_2026-10-05_09-12.sql
 */
public class SqlFallbackWriter {

    private static final Logger log = LoggerFactory.getLogger(SqlFallbackWriter.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static Path write(TestRun run, ImportConfig config, SQLException cause) {
        String timestamp = LocalDateTime.now().format(TS);
        String filename  = "fallback_" + timestamp + ".sql";

        // Uložíme vedľa ndjson súboru alebo do working dir
        Path ndjsonDir = Path.of(config.ndjsonPath).getParent();
        Path outPath   = (ndjsonDir != null ? ndjsonDir : Path.of(".")).resolve(filename);

        try (PrintWriter w = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(
                        new FileOutputStream(outPath.toFile()), StandardCharsets.UTF_8)))) {

            w.println("-- ============================================================");
            w.println("-- Cucumber Import Fallback SQL");
            w.println("-- Vygenerované: " + LocalDateTime.now());
            w.println("-- Príčina zlyhania: " + cause.getMessage());
            w.println("-- Spustenie: mysql -u USER -p DATABASE < " + filename);
            w.println("-- ============================================================");
            w.println();
            w.println("SET NAMES utf8mb4;");
            w.println("START TRANSACTION;");
            w.println();

            // --- runs ---
            w.println("-- Run");
            w.printf("""
                INSERT INTO runs (
                    project, project_type, run_at,
                    tests_total_ms, build_duration_ms,
                    triggered_by, trigger_type,
                    branch, commit_hash,
                    environment, app_url, build_number,
                    parallel_mode, thread_count,
                    jira_reporting, jira_new_execution, jira_cycle_ids,
                    cucumber_tags, tested_feature, test_properties,
                    total_features, passed_features, failed_features,
                    total_scenarios, passed_scenarios, failed_scenarios,
                    total_steps, passed_steps, failed_steps, skipped_steps
                ) VALUES (%s, 'web', %s, %d, %d, %s, %s, %s, %s, %s, %s, %s, %s, %d, %d, %d, %s, %s, %s, %s,
                          %d, %d, %d, %d, %d, %d, %d, %d, %d, %d);%n""",
                q(config.project), q(run.runAt.toString()),
                run.testsTotalMs, run.buildDurationMs,
                q(config.triggeredBy), q(config.triggerType),
                q(config.branch), q(config.commitHash),
                q(config.environment), q(config.appUrl), q(config.buildNumber),
                q(config.parallelMode), config.threadCount,
                config.jiraReporting ? 1 : 0, config.jiraNewExecution ? 1 : 0,
                q(config.jiraCycleIds), q(config.cucumberTags),
                q(config.testedFeature), q(config.testProperties),
                run.totalFeatures, run.passedFeatures, run.failedFeatures,
                run.totalScenarios, run.passedScenarios, run.failedScenarios,
                run.totalSteps, run.passedSteps, run.failedSteps, run.skippedSteps
            );
            w.println("SET @run_id = LAST_INSERT_ID();");
            w.println();

            // --- run_metadata ---
            w.println("-- Run metadata");
            w.printf("INSERT INTO run_metadata (run_id, meta_key, meta_value) VALUES%n");
            w.printf("    (@run_id, 'browser', %s),%n", q(config.browser));
            w.printf("    (@run_id, 'browser_version', %s);%n", q(config.browserVersion));
            w.println();

            // --- features, scenarios, steps ---
            int featIdx = 0;
            for (Feature feature : run.features) {
                featIdx++;
                w.println("-- Feature " + featIdx + ": " + escape(feature.name));
                w.printf("""
                    INSERT INTO features (run_id, name, file_path, status, duration_ms, tags,
                        total_scenarios, passed_scenarios, failed_scenarios)
                    VALUES (@run_id, %s, %s, %s, %d, %s, %d, %d, %d);%n""",
                    q(feature.name), q(feature.filePath), q(feature.status),
                    feature.durationMs, q(feature.tags),
                    feature.totalScenarios, feature.passedScenarios, feature.failedScenarios
                );
                w.println("SET @feat_id = LAST_INSERT_ID();");

                for (Scenario scenario : feature.scenarios) {
                    w.printf("""
                        INSERT INTO scenarios (feature_id, name, status, line, started_at, duration_ms,
                            thread_id, tags, hook_error_type, hook_error_message,
                            total_steps, passed_steps, failed_steps, skipped_steps)
                        VALUES (@feat_id, %s, %s, %d, %d, %d, %s, %s, %s, %s, %d, %d, %d, %d);%n""",
                        q(scenario.name), q(scenario.status), scenario.line,
                        scenario.startedAt, scenario.durationMs,
                        q(scenario.threadId), q(scenario.tags),
                        q(scenario.hookErrorType), q(scenario.hookErrorMessage),
                        scenario.totalSteps, scenario.passedSteps,
                        scenario.failedSteps, scenario.skippedSteps
                    );
                    w.println("SET @scen_id = LAST_INSERT_ID();");

                    if (!scenario.steps.isEmpty()) {
                        w.println("INSERT INTO steps (scenario_id, keyword, name, translated_text,");
                        w.println("    line, status, duration_ms, error_message, stack_trace,");
                        w.println("    screenshot_url, failed_screenshot_url) VALUES");
                        for (int i = 0; i < scenario.steps.size(); i++) {
                            Step s = scenario.steps.get(i);
                            boolean last = (i == scenario.steps.size() - 1);
                            w.printf("    (@scen_id, %s, %s, %s, %d, %s, %d, %s, %s, %s, %s)%s%n",
                                q(s.keyword), q(s.name), q(s.translatedText),
                                s.line, q(s.status), s.durationMs,
                                q(s.errorMessage), q(s.stackTrace),
                                q(s.screenshotUrl), q(s.failedScreenshotUrl),
                                last ? ";" : ","
                            );
                        }
                    }
                }
                w.println();
            }

            w.println("COMMIT;");
            w.println("-- Import dokončený");

            log.warn("SQL fallback uložený do: {}", outPath.toAbsolutePath());
            return outPath;

        } catch (IOException e) {
            log.error("Nepodarilo sa zapísať SQL fallback súbor: {}", outPath, e);
            return null;
        }
    }

    /** Obalí hodnotu do SQL single-quoted stringu, escapuje apostrofy a backslashe */
    private static String q(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Len pre komentáre — skráti dlhý string */
    private static String escape(String s) {
        if (s == null) return "";
        String clean = s.replace("--", "- -");
        return clean.length() > 80 ? clean.substring(0, 80) + "..." : clean;
    }
}
