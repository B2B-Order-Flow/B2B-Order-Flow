package com.b2borderflow.core.request;

import com.b2borderflow.core.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Заявка клиента. Статус меняется только через сервис переходов;
 * прямых UPDATE статуса нет (docs/system/status-model.md, раздел 2).
 */
@Entity
@Table(name = "request")
public class Request extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "number", nullable = false, updatable = false, length = 20)
    private String number;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "parent_request_id")
    private UUID parentRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RequestStatus status = RequestStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private RequestStatus previousStatus;

    @Column(name = "status_changed_at", nullable = false)
    private OffsetDateTime statusChangedAt;

    @Column(name = "integration_error", nullable = false)
    private boolean integrationError;

    @Column(name = "commitment_level", nullable = false)
    private short commitmentLevel;

    @Column(name = "request_class")
    private String requestClass;

    @Column(name = "route_version", nullable = false)
    private int routeVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "manager_id")
    private UUID managerId;

    @Column(name = "address")
    private String address;

    @Column(name = "requested_speed_mbps")
    private Integer requestedSpeedMbps;

    @Column(name = "desired_date")
    private LocalDate desiredDate;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_preference", length = 32)
    private ContractType contractPreference;

    @Column(name = "client_comment")
    private String clientComment;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment", length = 16)
    private Segment segment;

    @Column(name = "is_gov_customer")
    private Boolean govCustomer;

    @Column(name = "debt_amount", precision = 14, scale = 2)
    private BigDecimal debtAmount;

    @Column(name = "crm_manager_id")
    private UUID crmManagerId;

    @Column(name = "tech_result")
    private String techResult;

    @Column(name = "tech_reject_reason")
    private String techRejectReason;

    @Column(name = "capex", precision = 14, scale = 2)
    private BigDecimal capex;

    @Column(name = "construction_days")
    private Integer constructionDays;

    @Column(name = "selected_offer_id")
    private UUID selectedOfferId;

    @Column(name = "contract_number")
    private String contractNumber;

    @Column(name = "contract_sent_at")
    private OffsetDateTime contractSentAt;

    @Column(name = "contract_concluded_at")
    private OffsetDateTime contractConcludedAt;

    @Column(name = "work_started_at")
    private OffsetDateTime workStartedAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;

    @Column(name = "act_sent_at")
    private OffsetDateTime actSentAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "accepted_by_default", nullable = false)
    private boolean acceptedByDefault;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public void setCompanyId(UUID companyId) {
        this.companyId = companyId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public UUID getParentRequestId() {
        return parentRequestId;
    }

    public void setParentRequestId(UUID parentRequestId) {
        this.parentRequestId = parentRequestId;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public RequestStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(RequestStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public OffsetDateTime getStatusChangedAt() {
        return statusChangedAt;
    }

    public void setStatusChangedAt(OffsetDateTime statusChangedAt) {
        this.statusChangedAt = statusChangedAt;
    }

    public boolean isIntegrationError() {
        return integrationError;
    }

    public void setIntegrationError(boolean integrationError) {
        this.integrationError = integrationError;
    }

    public short getCommitmentLevel() {
        return commitmentLevel;
    }

    public void setCommitmentLevel(short commitmentLevel) {
        this.commitmentLevel = commitmentLevel;
    }

    public String getRequestClass() {
        return requestClass;
    }

    public void setRequestClass(String requestClass) {
        this.requestClass = requestClass;
    }

    public int getRouteVersion() {
        return routeVersion;
    }

    public void setRouteVersion(int routeVersion) {
        this.routeVersion = routeVersion;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public UUID getManagerId() {
        return managerId;
    }

    public void setManagerId(UUID managerId) {
        this.managerId = managerId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getRequestedSpeedMbps() {
        return requestedSpeedMbps;
    }

    public void setRequestedSpeedMbps(Integer requestedSpeedMbps) {
        this.requestedSpeedMbps = requestedSpeedMbps;
    }

    public LocalDate getDesiredDate() {
        return desiredDate;
    }

    public void setDesiredDate(LocalDate desiredDate) {
        this.desiredDate = desiredDate;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public ContractType getContractPreference() {
        return contractPreference;
    }

    public void setContractPreference(ContractType contractPreference) {
        this.contractPreference = contractPreference;
    }

    public String getClientComment() {
        return clientComment;
    }

    public void setClientComment(String clientComment) {
        this.clientComment = clientComment;
    }

    public Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment;
    }

    public Boolean getGovCustomer() {
        return govCustomer;
    }

    public void setGovCustomer(Boolean govCustomer) {
        this.govCustomer = govCustomer;
    }

    public BigDecimal getDebtAmount() {
        return debtAmount;
    }

    public void setDebtAmount(BigDecimal debtAmount) {
        this.debtAmount = debtAmount;
    }

    public UUID getCrmManagerId() {
        return crmManagerId;
    }

    public void setCrmManagerId(UUID crmManagerId) {
        this.crmManagerId = crmManagerId;
    }

    public String getTechResult() {
        return techResult;
    }

    public void setTechResult(String techResult) {
        this.techResult = techResult;
    }

    public String getTechRejectReason() {
        return techRejectReason;
    }

    public void setTechRejectReason(String techRejectReason) {
        this.techRejectReason = techRejectReason;
    }

    public BigDecimal getCapex() {
        return capex;
    }

    public void setCapex(BigDecimal capex) {
        this.capex = capex;
    }

    public Integer getConstructionDays() {
        return constructionDays;
    }

    public void setConstructionDays(Integer constructionDays) {
        this.constructionDays = constructionDays;
    }

    public UUID getSelectedOfferId() {
        return selectedOfferId;
    }

    public void setSelectedOfferId(UUID selectedOfferId) {
        this.selectedOfferId = selectedOfferId;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public OffsetDateTime getContractSentAt() {
        return contractSentAt;
    }

    public void setContractSentAt(OffsetDateTime contractSentAt) {
        this.contractSentAt = contractSentAt;
    }

    public OffsetDateTime getContractConcludedAt() {
        return contractConcludedAt;
    }

    public void setContractConcludedAt(OffsetDateTime contractConcludedAt) {
        this.contractConcludedAt = contractConcludedAt;
    }

    public OffsetDateTime getWorkStartedAt() {
        return workStartedAt;
    }

    public void setWorkStartedAt(OffsetDateTime workStartedAt) {
        this.workStartedAt = workStartedAt;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public OffsetDateTime getActSentAt() {
        return actSentAt;
    }

    public void setActSentAt(OffsetDateTime actSentAt) {
        this.actSentAt = actSentAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(OffsetDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public boolean isAcceptedByDefault() {
        return acceptedByDefault;
    }

    public void setAcceptedByDefault(boolean acceptedByDefault) {
        this.acceptedByDefault = acceptedByDefault;
    }

    public OffsetDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(OffsetDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
