"""
Deterministic synthetic enterprise corpus for the demo environment.

This is NOT the ML evaluation corpus (FiQA) and NOT the DBE regression fixtures
(the 10 seeded documents). It is a third corpus with a third job: giving the
deployed demo realistic documents to search, with owners and permissions so the
RBAC behaviour is visible.

It has to be separate. The regression suite pins exact counts and ids -- e.g.
`testUnifiedSearchVectorOnly` asserts totalHits == 10 with ids exactly 1..10 --
so any demo document sharing that database would break tests that must stay
frozen.

Everything is seeded, so the same command produces the same corpus, the same
ids and the same permissions on any machine.

Usage:
    python -m src.demo.corpus --out data/demo
"""

from __future__ import annotations

import argparse
import json
import random
from dataclasses import dataclass, field
from pathlib import Path

from src.demo.topics import TOPICS, Topic

DEFAULT_SEED = 20260912
DEFAULT_PER_TOPIC = 15

# Slot pools. Values are picked per document so that documents sharing a topic
# differ in specifics rather than being duplicates.
SLOTS: dict[str, tuple] = {
    "year": (2024, 2025, 2026),
    "quarter": (1, 2, 3, 4),
    "major": (1, 2, 3),
    "minor": (0, 1, 2, 3, 4),
    "system": ("Atlas", "Beacon", "Corvus", "Dynamo", "Everest", "Fathom"),
    "leave_days": (24, 25, 26, 28, 30),
    "carry": (3, 5, 7, 10),
    "carry_deadline": ("31 March", "30 April", "31 May"),
    "notice": (5, 10, 15, 20),
    "probation": (3, 6),
    "probation_ext": (1, 2, 3),
    "office_days": (2, 3, 4),
    "review": (6, 12),
    "stipend": ("150", "250", "400"),
    "abroad_days": (20, 30, 45, 60),
    "cycle": ("annual", "six-month", "quarterly"),
    "appeal": (5, 10, 14),
    "hotel_cap": ("120", "150", "180", "220"),
    "meal_cap": ("30", "40", "50"),
    "receipt_floor": ("10", "20", "25"),
    "claim_days": (30, 60, 90),
    "approve_l1": ("500", "1,000", "2,500"),
    "approve_l2": ("10,000", "25,000", "50,000"),
    "pay_days": (5, 10, 15),
    "revenue": ("4.2 million", "5.8 million", "7.1 million", "9.6 million"),
    "growth": ("6 per cent", "11 per cent", "18 per cent", "24 per cent"),
    "plan_var": ("2 per cent below", "1 per cent above", "4 per cent below"),
    "margin": ("61 per cent", "64 per cent", "68 per cent", "72 per cent"),
    "headcount_cost": ("2.1 million", "2.9 million", "3.4 million"),
    "cloud_cost": ("180 thousand", "240 thousand", "310 thousand"),
    "coverage": ("2.1 times", "2.8 times", "3.4 times"),
    "budget_month": ("September", "October", "November"),
    "budget_weeks": (3, 4, 6),
    "reforecast_threshold": ("5 per cent", "10 per cent"),
    "po_floor": ("500", "1,000", "2,000"),
    "quote_threshold": ("25,000", "50,000", "100,000"),
    "onboard_days": (10, 15, 20),
    "timeout": (500, 800, 1500, 2000),
    "rps": ("2,000", "5,000", "12,000"),
    "coverage_pct": ("80 per cent", "90 per cent"),
    "branch_days": (3, 5, 10),
    "ack_minutes": (5, 10, 15),
    "escalate_minutes": (20, 30, 45),
    "update_minutes": (15, 30, 60),
    "review_days": (3, 5, 10),
    "backfill_rows": ("100,000", "1 million", "10 million"),
    "queries": (120, 150, 200, 300),
    "kw_weight": ("0.3", "0.4", "0.5"),
    "vec_weight": ("0.5", "0.6", "0.7"),
    "chunk_words": (150, 180, 220),
    "overlap_words": (30, 40, 50),
    "nda_years": (3, 5, 7),
    "return_days": (14, 30, 60),
    "employment_retention": (6, 7, 10),
    "finance_retention": (6, 7),
    "recruit_retention": (6, 12),
    "sar_days": (28, 30),
    "breach_hours": (48, 72),
    "liability_cap": ("100 per cent", "125 per cent", "150 per cent"),
    "sla": ("99.5 per cent", "99.9 per cent"),
    "termination_notice": (30, 60, 90),
    "open_time": ("07:00", "07:30", "08:00"),
    "close_time": ("19:00", "20:00", "21:00"),
    "booking_days": (7, 14, 30),
    "release_minutes": (30, 60, 90),
    "urgent_hours": (2, 4, 8),
    "longhaul_hours": (6, 7, 8),
    "lock_minutes": (5, 10, 15),
    "report_hours": (12, 24),
}

DEMO_USERS = (
    ("dana_hr",      "Dana Whitfield",   "HR",             "MANAGER"),
    ("omar_hr",      "Omar Siddiqui",    "HR",             "EMPLOYEE"),
    ("priya_fin",    "Priya Raman",      "Finance",        "MANAGER"),
    ("tom_fin",      "Tom Bergstrom",    "Finance",        "EMPLOYEE"),
    ("mei_eng",      "Mei Lin Zhao",     "Engineering",    "MANAGER"),
    ("jonas_eng",    "Jonas Aaltonen",   "Engineering",    "EMPLOYEE"),
    ("sara_eng",     "Sara Okonkwo",     "Engineering",    "EMPLOYEE"),
    ("ravi_res",     "Ravi Deshpande",   "Research",       "MANAGER"),
    ("elena_res",    "Elena Kovalenko",  "Research",       "EMPLOYEE"),
    ("marcus_legal", "Marcus Delacroix", "Legal",          "MANAGER"),
    ("aisha_legal",  "Aisha Rahman",     "Legal",          "EMPLOYEE"),
    ("nils_admin",   "Nils Andersen",    "Administration", "EMPLOYEE"),
    ("grace_it",     "Grace Mwangi",     "IT",             "EMPLOYEE"),
    ("demo_admin",   "Demo Administrator", "Administration", "ADMIN"),
)

STATUSES = ("INDEXED", "INDEXED", "INDEXED", "INDEXED", "PROCESSING", "ARCHIVED")

# Natural-language queries whose relevant documents are every document of the
# named topic. Used to verify retrieval end to end without hand-scoring.
DEMO_QUERIES: tuple[tuple[str, str], ...] = (
    ("how many days of paid holiday do I get each year", "hr-leave"),
    ("can I carry unused vacation into next year", "hr-leave"),
    ("what happens in my first week as a new starter", "hr-onboarding"),
    ("how long is the probation period", "hr-onboarding"),
    ("how many days must I come into the office", "hr-remote"),
    ("can I work from another country temporarily", "hr-remote"),
    ("how do I appeal my performance rating", "hr-performance"),
    ("what can I claim back for a business trip", "fin-expenses"),
    ("do I need a receipt for a small purchase", "fin-expenses"),
    ("how did revenue compare against plan", "fin-quarterly"),
    ("when are budget submissions due", "fin-budget"),
    ("difference between capital and operating spend", "fin-budget"),
    ("when do I need three competitive quotes", "fin-procurement"),
    ("why was my invoice returned without a purchase order", "fin-procurement"),
    ("how are the services split up and what owns which database", "tech-architecture"),
    ("what happens when the vector store goes down", "tech-architecture"),
    ("what is expected of me when reviewing a pull request", "tech-coding"),
    ("when should I escalate during an outage", "tech-incident"),
    ("how do we define severity one", "tech-incident"),
    ("how do we rename a column without breaking the old version", "tech-database"),
    ("which embedding model performed best in the evaluation", "res-embeddings"),
    ("how should lexical and semantic scores be combined", "res-ranking"),
    ("what window size and overlap should chunks use", "res-chunking"),
    ("how long do confidentiality obligations last", "legal-nda"),
    ("when is information not considered confidential", "legal-nda"),
    ("how long do we keep employee records after they leave", "legal-dataprotection"),
    ("how quickly must a data breach be reported", "legal-dataprotection"),
    ("what is the standard cap on supplier liability", "legal-vendor"),
    ("how do I book a desk in the office", "admin-facilities"),
    ("when can I book premium economy", "admin-travel"),
    ("what should I do with a suspicious email", "admin-security"),
    ("is multi-factor authentication required", "admin-security"),
)


@dataclass
class DemoCorpus:
    users: list[dict] = field(default_factory=list)
    documents: list[dict] = field(default_factory=list)
    permissions: list[dict] = field(default_factory=list)
    queries: list[dict] = field(default_factory=list)

    def relevant_by_query(self) -> dict[str, set[int]]:
        out: dict[str, set[int]] = {}
        for query in self.queries:
            out[query["query_id"]] = set(query["relevant_document_ids"])
        return out


def _fill(text: str, rng: random.Random, chosen: dict) -> str:
    """Fill {slots}, reusing a value once chosen so it stays consistent per document."""
    result = text
    for name, pool in SLOTS.items():
        token = "{" + name + "}"
        if token in result:
            if name not in chosen:
                chosen[name] = rng.choice(pool)
            result = result.replace(token, str(chosen[name]))
    return result


def _owner_for(topic: Topic, rng: random.Random) -> str:
    candidates = [u[0] for u in DEMO_USERS if u[2] == topic.department and u[3] != "ADMIN"]
    if not candidates:
        candidates = [u[0] for u in DEMO_USERS if u[3] != "ADMIN"]
    return rng.choice(candidates)


def generate(seed: int = DEFAULT_SEED, per_topic: int = DEFAULT_PER_TOPIC) -> DemoCorpus:
    corpus = DemoCorpus()

    corpus.users = [
        {"username": u, "full_name": n, "department": d, "role": r,
         "email": f"{u}@demo.example.com"}
        for u, n, d, r in DEMO_USERS
    ]

    document_id = 1
    topic_documents: dict[str, list[int]] = {}

    for topic in TOPICS:
        topic_documents[topic.key] = []
        for variant in range(per_topic):
            # Seeded per (topic, variant) so adding a topic does not shift the
            # content of documents belonging to other topics.
            rng = random.Random(f"{seed}:{topic.key}:{variant}")
            chosen: dict = {}

            title = _fill(topic.title, rng, chosen)
            if per_topic > 1:
                title = f"{title} ({chr(ord('A') + variant % 26)}{variant // 26 or ''})".strip()

            body_parts = []
            for heading, text in topic.sections:
                body_parts.append(f"{heading}. {_fill(text, rng, chosen)}")
            body = "\n\n".join(body_parts)

            owner = _owner_for(topic, rng)
            corpus.documents.append({
                "document_id": document_id,
                "topic": topic.key,
                "title": title,
                "description": _fill(topic.summary, rng, chosen),
                "category": topic.category,
                "department": topic.department,
                "owner": owner,
                "document_type": topic.document_type,
                "status": rng.choice(STATUSES),
                "language": "en",
                "keywords": list(topic.keywords),
                "body": body,
                "word_count": len(body.split()),
                "character_count": len(body),
                "storage_reference": f"s3://demo-bucket/{topic.category.lower()}/{topic.key}-{variant:02d}",
            })
            topic_documents[topic.key].append(document_id)
            document_id += 1

    # Explicit READ grants so permission filtering is visible in the demo:
    # every manager gets read access to a deterministic slice of another
    # department's documents.
    grant_rng = random.Random(f"{seed}:grants")
    managers = [u[0] for u in DEMO_USERS if u[3] == "MANAGER"]
    for manager in managers:
        foreign = [d for d in corpus.documents if d["owner"] != manager]
        for document in grant_rng.sample(foreign, min(12, len(foreign))):
            corpus.permissions.append({
                "document_id": document["document_id"],
                "username": manager,
                "permission_type": "READ",
            })

    for index, (text, topic_key) in enumerate(DEMO_QUERIES, start=1):
        corpus.queries.append({
            "query_id": f"dq{index:03d}",
            "text": text,
            "topic": topic_key,
            "relevant_document_ids": topic_documents[topic_key],
        })

    return corpus


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate the synthetic demo corpus")
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED)
    parser.add_argument("--per-topic", type=int, default=DEFAULT_PER_TOPIC)
    parser.add_argument("--out", default="data/demo")
    args = parser.parse_args()

    corpus = generate(args.seed, args.per_topic)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    for name, records in (
        ("users.json", corpus.users),
        ("documents.json", corpus.documents),
        ("permissions.json", corpus.permissions),
        ("queries.json", corpus.queries),
    ):
        (out / name).write_text(json.dumps(records, indent=2, ensure_ascii=False), encoding="utf-8")

    words = sum(d["word_count"] for d in corpus.documents)
    print(f"users        {len(corpus.users)}")
    print(f"documents    {len(corpus.documents)}  ({len(TOPICS)} topics x {args.per_topic})")
    print(f"permissions  {len(corpus.permissions)}")
    print(f"queries      {len(corpus.queries)}")
    print(f"total words  {words:,}  (mean {words // len(corpus.documents)} per document)")
    print(f"written to   {out.resolve()}")


if __name__ == "__main__":
    main()
