#!/usr/bin/env python3
"""Convert LiTiaoTiao custom rules into a conservative, package-bound ad subset."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import re
from pathlib import Path


REPOSITORY = "https://github.com/743859910/LiTiaoTiao_Custom_Rules"
SOURCE_FILE_URL = (
    "https://raw.githubusercontent.com/743859910/"
    "LiTiaoTiao_Custom_Rules/master/LiTiaotiao_Custom_Rules.txt"
)

AD_PATTERN = re.compile(
    r"(?i)(广告|廣告|推广|推廣|(?<![a-z])ad(?:vert(?:isement|ising)?)?(?![a-z])|"
    r"splash|banner|native_ad|ksad|csj|gdt|topon|reaper|promotion|promo)"
)
CLOSE_PATTERN = re.compile(
    r"(?i)(关闭|關閉|close|skip|跳过|跳過|不感兴趣|不感興趣|dismiss|"
    r"cancel|ignore|(?:^|_)del(?:_|$)|delete|remove|hide)"
)
DENIED_CONTEXT_PATTERN = re.compile(
    r"(?i)(删除所有广告|刪除所有廣告|支付|付款|安装|安裝|下载|下載|授权|授權|"
    r"允许|允許|登录|登錄|注册|註冊|确认购买|確認購買)"
)


def repair_inner_json(raw: str) -> tuple[dict, bool]:
    try:
        return json.loads(raw), False
    except json.JSONDecodeError:
        repaired = re.sub(r",\s*([}\]])", r"\1", raw)
        return json.loads(repaired), True


def is_ad_close_rule(rule: dict) -> bool:
    trigger = str(rule.get("id", "")).strip()
    action = str(rule.get("action", "")).strip()
    combined = f"{trigger} {action}"
    if not trigger or not action or action == "GLOBAL_ACTION_BACK":
        return False
    if DENIED_CONTEXT_PATTERN.search(combined):
        return False
    return bool(AD_PATTERN.search(combined) and CLOSE_PATTERN.search(action))


def convert(source: Path, commit: str, commit_date: str) -> dict:
    source_bytes = source.read_bytes()
    outer = json.loads(source_bytes.decode("utf-8"))
    package_rules: dict[str, list[dict]] = {}
    source_rule_count = 0
    repaired_entries = 0

    for package_entry in outer:
        if not isinstance(package_entry, dict):
            continue
        for package_hash, raw_rules in package_entry.items():
            parsed, repaired = repair_inner_json(raw_rules)
            repaired_entries += int(repaired)
            rules = parsed.get("popup_rules", [])
            if not isinstance(rules, list):
                continue
            source_rule_count += len(rules)
            selected: list[dict] = []
            for source_index, rule in enumerate(rules):
                if not isinstance(rule, dict) or not is_ad_close_rule(rule):
                    continue
                trigger = str(rule["id"]).strip()
                action = str(rule["action"]).strip()
                converted = {
                    "key": f"{package_hash}:{source_index}",
                    "trigger": trigger,
                    "action": action,
                }
                delay = rule.get("delay_popup", 0)
                if isinstance(delay, int) and 0 < delay <= 15_000:
                    converted["delayMs"] = delay
                selected.append(converted)
            if selected:
                package_rules.setdefault(str(package_hash), []).extend(selected)

    included_rule_count = sum(len(rules) for rules in package_rules.values())
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    return {
        "schema": 1,
        "source": {
            "repository": REPOSITORY,
            "file": SOURCE_FILE_URL,
            "commit": commit,
            "commitDate": commit_date,
            "license": "MIT",
            "sha256": hashlib.sha256(source_bytes).hexdigest(),
            "generatedAt": now,
        },
        "stats": {
            "sourcePackageEntries": len(outer),
            "sourcePopupRules": source_rule_count,
            "repairedInnerEntries": repaired_entries,
            "includedPackageHashes": len(package_rules),
            "includedAdCloseRules": included_rule_count,
            "excludedRules": source_rule_count - included_rule_count,
        },
        "packageRules": package_rules,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--commit-date", required=True)
    args = parser.parse_args()

    result = convert(args.source, args.commit, args.commit_date)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(result["stats"], ensure_ascii=False, sort_keys=True))


if __name__ == "__main__":
    main()
