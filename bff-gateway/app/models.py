from datetime import date
from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field


class Command(BaseModel):
    model_config = ConfigDict(extra="allow")
    version: int = Field(ge=0)


class CommercialTerms(Command):
    tariffType: str
    contractType: str
    discountPercent: Decimal | None = Field(default=None, ge=0, le=100)
    speedMbps: int | None = Field(default=None, ge=1, le=100000)


class TechResult(StrEnum):
    AVAILABLE = "AVAILABLE"
    WITH_CONSTRUCTION = "WITH_CONSTRUCTION"
    NONE = "NONE"


class TechResultCommand(Command):
    result: TechResult
    capex: Decimal | None = Field(default=None, ge=0)
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
    assigneeId: str


class ContractConcludedCommand(Command):
    contractNumber: str = Field(min_length=1, max_length=100)
    concludedAt: date


class PauseCommand(Command):
    reasonCode: str
    comment: str = Field(max_length=4000)
