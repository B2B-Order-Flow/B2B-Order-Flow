package com.b2borderflow.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoreOpenApiContractTest {

    private static final Pattern PATH = Pattern.compile("^  (/[^:]+):$");
    private static final Pattern METHOD = Pattern.compile("^    (get|post|put|patch|delete|head|options|trace):$");
    private static final Pattern OPERATION_ID = Pattern.compile("^      operationId: ([^\\s]+)$");

    private static final Set<String> WEEK_TWO_OPERATIONS = Set.of(
            "setCommercialTerms", "markContractSent", "concludeContract", "acceptOffer",
            "markActSent", "signAct", "closeByDefault", "listStages", "listMyStages",
            "claimStage", "assignStage", "setTechResult", "completeStage", "activateStage",
            "getRequestSla", "pauseStageSla", "resumeStageSla", "extendStageSlaPause",
            "listNotifications", "readNotification", "readAllNotifications", "listOperatorUsers",
            "issueWsTicket", "redeemWsTicket", "streamEvents");

    @Test
    void everyOperationHasUniqueOperationIdAndWeekTwoIsPresent() throws IOException {
        Path spec = Path.of("src/main/resources/openapi/core-api.yaml");
        String currentPath = null;
        String currentMethod = null;
        Set<String> operationIds = new HashSet<>();

        for (String line : Files.readAllLines(spec)) {
            Matcher path = PATH.matcher(line);
            if (path.matches()) {
                currentPath = path.group(1);
                currentMethod = null;
                continue;
            }
            Matcher method = METHOD.matcher(line);
            if (method.matches()) {
                currentMethod = method.group(1).toUpperCase();
                continue;
            }
            Matcher operationId = OPERATION_ID.matcher(line);
            if (operationId.matches() && currentPath != null && currentMethod != null) {
                String id = operationId.group(1);
                assertThat(operationIds.add(id)).as("duplicate operationId %s", id).isTrue();
            }
        }

        assertThat(operationIds).isNotEmpty();
        assertThat(operationIds).containsAll(WEEK_TWO_OPERATIONS);
    }
}
