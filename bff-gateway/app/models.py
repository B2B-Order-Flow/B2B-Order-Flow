from datetime import date
from enum import StrEnum
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


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


class CancellationCostsCommand(Command):
    amount: str = Field(pattern=r"^[0-9]+\.[0-9]{2}$")
    breakdown: dict[str, object]


class ConfirmCancellationCostsCommand(Command):
    confirmed: bool


class ExportCommand(Command):
    format: str = Field(pattern=r"^(CSV|PDF)$")
    filters: dict[str, object] = Field(default_factory=dict)
