"""Check that every public BFF operation exists in the core OpenAPI contract."""

import argparse
from pathlib import Path

import yaml


def operations(document: dict, public: bool = False) -> set[tuple[str, str]]:
    return {
        (path.replace("/api/v1/", "/internal/v1/", 1) if public else path, method)
        for path, item in document.get("paths", {}).items()
        for method in item
        if method.lower() in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bff", type=Path, default=Path("openapi/external-api.yaml"))
    parser.add_argument("--core", type=Path, required=True)
    args = parser.parse_args()

    if not args.core.exists():
        raise SystemExit(f"Core OpenAPI contract not found: {args.core}")

    with args.bff.open(encoding="utf-8") as file:
        bff = yaml.safe_load(file)
    with args.core.open(encoding="utf-8") as file:
        core = yaml.safe_load(file)

    bff_operations = operations(bff, public=True)
    core_operations = operations(core)
    missing_in_core = bff_operations - core_operations
    missing_in_bff = core_operations - bff_operations
    if missing_in_core or missing_in_bff:
        if missing_in_core:
            print("BFF operations missing from core contract:")
        for path, method in sorted(missing_in_core):
            print(f"  {method.upper():6} {path}")
        if missing_in_bff:
            print("Core operations missing from BFF contract:")
        for path, method in sorted(missing_in_bff):
            print(f"  {method.upper():6} {path}")
        return 1

    print("OpenAPI operation compatibility check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
