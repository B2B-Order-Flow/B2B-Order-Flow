from decimal import Decimal
from enum import StrEnum

from pydantic import BaseModel, ConfigDict, Field


class Command(BaseModel):
    model_config = ConfigDict(extra="allow")
    version: int = Field(ge=0)


class CommercialTerms(Command):
    tariff_type: str
    contract_type: str
    discount_percent: Decimal = Field(ge=0, le=100)


class TechResult(StrEnum):
    AVAILABLE = "AVAILABLE"
    WITH_CONSTRUCTION = "WITH_CONSTRUCTION"
    NONE = "NONE"


class TechResultCommand(Command):
    result: TechResult


class StageDecision(StrEnum):
    APPROVE = "APPROVE"
    RETURN = "RETURN"
    REJECT = "REJECT"


class DecisionCommand(Command):
    decision: StageDecision
    comment: str | None = Field(default=None, max_length=4000)


class PauseCommand(Command):
    reason: str
    comment: str = Field(max_length=4000)
