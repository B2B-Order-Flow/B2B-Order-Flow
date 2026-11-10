package com.b2borderflow.core.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.b2borderflow.core.company.CompanyStatus;
import com.b2borderflow.core.domain.Role;
import com.b2borderflow.core.support.AbstractIntegrationTest;
import com.b2borderflow.core.user.AppUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Ограничения request_stage после миграции V3 (замечания к V2). */
class RequestStageConstraintsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void cancelledStageKeepsAssigneeButQueuedStageCannotHaveOne() {
        UUID requestId = createDraft();
        AppUser technician = createUser("tech", "Павел Орлов", Role.TECH_SPECIALIST);

        insertStage(requestId, 1, "TECH_CHECK", "CANCELLED", "TECH_SPECIALIST", technician.getId());

        assertThatThrownBy(() -> insertStage(
                requestId, 1, "INSTALLATION", "QUEUED", "ENGINEER", technician.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void returnedStageDoesNotBlockNewActiveStageOfSameType() {
        UUID requestId = createDraft();
        AppUser technician = createUser("tech", "Павел Орлов", Role.TECH_SPECIALIST);

        insertStage(requestId, 1, "TECH_CHECK", "RETURNED", "TECH_SPECIALIST", technician.getId());
        insertStage(requestId, 1, "TECH_CHECK", "QUEUED", "TECH_SPECIALIST", null);
        assertThat(jdbc.queryForObject(
                "select count(*) from request_stage where request_id = ? and type = 'TECH_CHECK'",
                Integer.class, requestId)).isEqualTo(2);

        assertThatThrownBy(() -> insertStage(
                requestId, 1, "TECH_CHECK", "QUEUED", "TECH_SPECIALIST", null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID createDraft() {
        AppUser client = createUser("client.owner", "Клиент Владелец");
        createCompany("ООО «Логистик Плюс»", "7725012341", CompanyStatus.ACTIVE, client);
        String token = login("client.owner");
        ApiResponse created = post("/internal/v1/requests", token, Role.CLIENT_OWNER, requestPayload());
        assertThat(created.status()).isEqualTo(201);
        return UUID.fromString((String) created.body().get("id"));
    }

    private void insertStage(UUID requestId, int routeVersion, String type, String status,
                             String queueRole, UUID assigneeId) {
        jdbc.update("""
                        insert into request_stage
                            (request_id, route_version, type, status, queue_role, assignee_id)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                requestId, routeVersion, type, status, queueRole, assigneeId);
    }
}
