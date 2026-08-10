package sk.moja.runner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.CancellationToken;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import sk.moja.runners.CucumberLaunchSupport;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static sk.moja.db.importer.DbImport.dbImport;
import static sk.moja.enumerators.Constants.*;
import static sk.moja.utils.PrepareData.*;

public class ServerRunnerTest {

    @Test
    void runRegressionSuite() {
        String features = SERVER_DESTINATION + DESTINATION + "/temp/features";
        String glue = "sk.moja.base,sk.moja.steps";
        String reportsBase = SERVER_DESTINATION + DESTINATION + "/temp/reports/cucumber-report/";
        String plugin = "pretty,"
                + "pretty:" + reportsBase + "cucumber.txt,"
                + "sk.moja.events.GlobalEventHandler,"
                + "html:" + reportsBase + "cucumber.html,"
                + "json:" + reportsBase + "cucumber.json,"
                + "message:" + reportsBase + "cucumber-messages.ndjson,"
                + "timeline:" + reportsBase + "timeline.txt";

        Map<String, String> configParameters = CucumberLaunchSupport.buildConfigParameters(features, glue, null, plugin, false);
        configParameters.put("cucumber.ansi-colors.disabled", "true");

        TestExecutionSummary summary = CucumberLaunchSupport.execute(configParameters, CancellationToken.disabled());

        assertEquals(0, summary.getTotalFailureCount(), "Cucumber run reported failures");
    }

    @AfterAll
    static void afterAll() {
        dbImport();
        copyReportsAfterRun();
    }
}
