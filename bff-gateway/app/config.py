from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    core_url: str = Field(default="http://core-service:8080", validation_alias="CORE_URL")
    internal_token: str = Field(default="change_me", validation_alias="INTERNAL_TOKEN")
    http_timeout_seconds: float = Field(default=12.0, validation_alias="CORE_HTTP_TIMEOUT_SECONDS")
    http_connect_timeout_seconds: float = Field(default=2.0, validation_alias="CORE_HTTP_CONNECT_TIMEOUT_SECONDS")


@lru_cache
def get_settings() -> Settings:
    return Settings()
