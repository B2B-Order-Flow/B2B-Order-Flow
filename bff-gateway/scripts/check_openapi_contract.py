"""Check that every public BFF operation exists in the core OpenAPI contract."""

import argparse
from pathlib import Path

import yaml

EXCLUDED_CORE_OPERATIONS = {
    ("/internal/v1/events/stream", "get"),
    ("/internal/v1/ws/ticket/redeem", "post"),
}


def operations(document: dict, public: bool = False) -> set[tuple[str, str]]:
    return {
        (path.replace("/api/v1/", "/internal/v1/", 1) if public else path, method)
        for path, item in document.get("paths", {}).items()
        for method in item
        if method.lower() in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}
    }


def validate_public_contract(document: dict) -> list[str]:
    parameters = document.get("components", {}).get("parameters", {})

    def resolve_parameter(parameter: dict) -> dict:
        reference = parameter.get("$ref", "")
        if reference.startswith("#/components/parameters/"):
            return parameters.get(reference.rsplit("/", 1)[-1], {})
        return parameter

    errors = []
    for path, item in document.get("paths", {}).items():
        path_parameters = {
            resolve_parameter(parameter).get("name")
            for parameter in item.get("parameters", [])
            if resolve_parameter(parameter).get("in") == "path"
        }
        placeholders = {
            segment[1:-1] for segment in path.split("/") if segment.startswith("{") and segment.endswith("}")
        }
        for method, operation in item.items():
            if method.lower() not in {"get", "post", "put", "patch", "delete", "head", "options", "trace"}:
                continue
            operation_parameters = {
                resolve_parameter(parameter).get("name")
                for parameter in operation.get("parameters", [])
                if resolve_parameter(parameter).get("in") == "path"
            }
            if placeholders - path_parameters - operation_parameters:
                missing = ", ".join(sorted(placeholders - path_parameters - operation_parameters))
                errors.append(f"{method.upper()} {path}: missing path parameter(s): {missing}")
            if not operation.get("security") and not path.startswith("/api/v1/auth/"):
                errors.append(f"{method.upper()} {path}: missing security declaration")
    return errors


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
    contract_errors = validate_public_contract(bff)
    if contract_errors:
        print("Invalid BFF contract:")
        for error in contract_errors:
            print(f"  {error}")
        return 1
    core_operations = operations(core) - EXCLUDED_CORE_OPERATIONS
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
