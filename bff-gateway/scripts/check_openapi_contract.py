"""Check that every public BFF operation exists in the core OpenAPI contract."""

import argparse
from pathlib import Path

import yaml


def operations(document: dict) -> set[tuple[str, str]]:
    return {
        (path, method)
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

    missing = operations(bff) - operations(core)
    if missing:
        print("BFF operations missing from core contract:")
        for path, method in sorted(missing):
            print(f"  {method.upper():6} {path}")
        return 1

    print("OpenAPI operation compatibility check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
