from datetime import date
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator


class Command(BaseModel):
    model_config = ConfigDict(extra="forbid")
    version: int = Field(ge=0)


class TariffType(StrEnum):
    STANDARD = "STANDARD"
    INDIVIDUAL = "INDIVIDUAL"


class ContractType(StrEnum):
    OFFER = "OFFER"
    STANDARD = "STANDARD"
    CLIENT_FORM = "CLIENT_FORM"


class CommercialTerms(Command):
    tariffType: TariffType
    contractType: ContractType
    discountPercent: str | None = Field(default=None, pattern=r"^[0-9]{1,3}\.[0-9]{2}$")
    speedMbps: int | None = Field(default=None, ge=1, le=100000)


class TechResult(StrEnum):
    AVAILABLE = "AVAILABLE"
    WITH_CONSTRUCTION = "WITH_CONSTRUCTION"
    NONE = "NONE"


class TechResultCommand(Command):
    result: TechResult
    capex: str | None = Field(default=None, pattern=r"^[0-9]+\.[0-9]{2}$")
    constructionDays: int | None = Field(default=None, ge=1)
    reasonCode: str | None = None
    comment: str | None = Field(default=None, max_length=4000)


class StageDecision(StrEnum):
    APPROVE = "APPROVE"
    RETURN = "RETURN"
    REJECT = "REJECT"


class DecisionCommand(Command):
    decision: StageDecision
    comment: str | None = Field(default=None, max_length=4000)
    reasonCode: str | None = None


class AssignStageCommand(Command):
    assigneeId: UUID


class ContractConcludedCommand(Command):
    contractNumber: str = Field(min_length=1, max_length=100)
    concludedAt: date


class PauseCommand(Command):
    reasonCode: str
    comment: str = Field(max_length=4000)


class ExtendPauseCommand(Command):
    comment: str = Field(max_length=4000)


class FlexibleCommand(Command):
    """Week-three command envelope; field-level business rules stay in core."""

    model_config = ConfigDict(extra="allow")


class StageReturnCommand(Command):
    comment: str = Field(min_length=1, max_length=4000)


class StageProblemCommand(Command):
    problem: str = Field(pattern=r"^(NO_ACCESS|CONNECTION_IMPOSSIBLE)$")
    comment: str = Field(min_length=1, max_length=4000)


class CancelCommand(Command):
    reasonCode: str = Field(min_length=1, max_length=100)
    comment: str | None = Field(default=None, max_length=4000)


class CommentCommand(Command):
    comment: str = Field(min_length=1, max_length=4000)


class DefectClaimCommand(Command):
    reason: str = Field(min_length=1, max_length=4000)


class CancellationRequestCommand(Command):
    reasonCode: str = Field(min_length=1, max_length=100)
    comment: str | None = Field(default=None, max_length=4000)


class CancellationCostItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    description: str = Field(min_length=1, max_length=500)
    amount: str = Field(pattern=r"^[0-9]+\.[0-9]{2}$")


class CancellationCostsCommand(Command):
    amount: str = Field(pattern=r"^[0-9]+\.[0-9]{2}$")
    breakdown: list[CancellationCostItem] = Field(min_length=1)
    comment: str | None = Field(default=None, max_length=4000)


class ConfirmCancellationCostsCommand(Command):
    confirmed: bool
    comment: str | None = Field(default=None, max_length=4000)


class ExportCommand(BaseModel):
    model_config = ConfigDict(extra="forbid")

    format: str = Field(pattern=r"^(CSV|PDF)$")
    filters: dict[str, object] | None = None
    requestId: UUID | None = None

    @model_validator(mode="after")
    def validate_export_target(self):
        if self.format == "PDF" and not self.requestId:
            raise ValueError("requestId is required for PDF export")
        if self.format == "CSV" and self.requestId is not None:
            raise ValueError("requestId is only valid for PDF export")
        return self
