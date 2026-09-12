"""
Topic definitions for the synthetic enterprise demo corpus.

Each topic carries its own vocabulary and its own prose. This matters: a corpus
generated from one template with swapped nouns would make every document a near
neighbour of every other, and retrieval quality would be an artefact of the
generator rather than a property of the embedding model. Topics here are written
to be lexically distinct from one another while staying plausible within their
department.

Slots in {braces} are filled per-document with deterministic values so that
documents of the same topic differ in specifics (year, system, threshold) rather
than being duplicates.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Topic:
    key: str
    category: str
    department: str
    title: str
    summary: str
    document_type: str
    keywords: tuple[str, ...]
    sections: tuple[tuple[str, str], ...]  # (heading, body)


TOPICS: tuple[Topic, ...] = (
    # ---------------------------------------------------------------- HR
    Topic(
        key="hr-leave",
        category="HR",
        department="HR",
        title="Annual Leave and Time Off Policy {year}",
        summary="Entitlement, accrual, carry-over and approval routing for paid time off.",
        document_type="PDF",
        keywords=("leave", "holiday", "accrual", "carry-over", "absence"),
        sections=(
            ("Entitlement",
             "Permanent employees accrue {leave_days} days of paid annual leave per calendar year, "
             "accruing monthly from the start date. Part-time staff accrue pro rata against contracted "
             "hours. Statutory public holidays are additional and are not deducted from the annual "
             "entitlement."),
            ("Carry-over",
             "Up to {carry} days may be carried into the following year and must be taken before "
             "{carry_deadline}. Carry-over beyond that limit requires written approval from the "
             "department head and is granted only where business demand prevented the leave being "
             "taken."),
            ("Requesting leave",
             "Requests are submitted through the workforce portal at least {notice} working days in "
             "advance. The line manager approves or declines within five working days. Declined "
             "requests must record a business reason. Periods of more than ten consecutive days "
             "require second-level approval."),
            ("Sickness during leave",
             "Where an employee falls ill during booked annual leave and provides medical evidence, "
             "the affected days are reclassified as sick leave and returned to the annual entitlement."),
        ),
    ),
    Topic(
        key="hr-onboarding",
        category="HR",
        department="HR",
        title="New Joiner Onboarding Handbook {year}",
        summary="First-week checklist, equipment provisioning and probation milestones.",
        document_type="DOCX",
        keywords=("onboarding", "induction", "probation", "new joiner"),
        sections=(
            ("Before the start date",
             "The hiring manager raises an onboarding ticket at least {notice} working days before the "
             "start date. Facilities issues a building pass, IT provisions a laptop from the {system} "
             "standard image, and payroll confirms bank and tax details."),
            ("First week",
             "New joiners complete mandatory induction covering information security, health and "
             "safety, and the code of conduct. A buddy is assigned from within the team to answer "
             "day-to-day questions that do not warrant the line manager."),
            ("Probation",
             "The probation period runs for {probation} months. Formal reviews take place at the "
             "midpoint and one week before the end. A probation extension of up to {probation_ext} "
             "months may be agreed once, in writing, where performance is improving but incomplete."),
        ),
    ),
    Topic(
        key="hr-remote",
        category="HR",
        department="HR",
        title="Hybrid and Remote Working Standard {year}",
        summary="Office attendance expectations, home workstation requirements and cross-border limits.",
        document_type="PDF",
        keywords=("remote", "hybrid", "workstation", "attendance"),
        sections=(
            ("Attendance pattern",
             "Teams operate a hybrid pattern with a minimum of {office_days} days per week on site. "
             "Anchor days are set per department so that collaborative work is co-located. Fully "
             "remote arrangements are exceptions and are reviewed every {review} months."),
            ("Home workstation",
             "Employees working from home must have a workstation meeting the display, seating and "
             "lighting requirements in the health and safety annex. A self-assessment is completed "
             "annually and a stipend of {stipend} is available towards compliant equipment."),
            ("Working from another country",
             "Working outside the country of employment is limited to {abroad_days} days per year and "
             "requires prior approval, because extended presence can create tax residency and "
             "permanent establishment exposure for the company."),
        ),
    ),
    Topic(
        key="hr-performance",
        category="HR",
        department="HR",
        title="Performance Review and Calibration Guide {year}",
        summary="Review cycle, rating scale, calibration process and appeals.",
        document_type="DOCX",
        keywords=("performance", "review", "calibration", "rating", "appraisal"),
        sections=(
            ("Cycle",
             "Reviews run on a {cycle} cadence. Employees submit a self-assessment, the line manager "
             "writes an assessment, and peer input is gathered for anyone with cross-team "
             "responsibilities."),
            ("Calibration",
             "Managers meet in calibration sessions to compare ratings across teams before anything is "
             "communicated. Calibration exists to remove manager-to-manager variance, not to force a "
             "distribution; there is no quota for any rating band."),
            ("Appeals",
             "An employee who disagrees with a final rating may appeal within {appeal} working days. "
             "Appeals are heard by a manager outside the reporting line together with an HR partner."),
        ),
    ),
    # ------------------------------------------------------------ Finance
    Topic(
        key="fin-expenses",
        category="Finance",
        department="Finance",
        title="Employee Expense and Reimbursement Policy {year}",
        summary="Claimable categories, receipt thresholds, approval limits and payment timing.",
        document_type="PDF",
        keywords=("expenses", "reimbursement", "receipts", "claim", "travel"),
        sections=(
            ("What can be claimed",
             "Reasonable costs incurred wholly for business purposes are reimbursable: standard-class "
             "rail, economy air travel, accommodation up to {hotel_cap} per night, and subsistence up "
             "to {meal_cap} per day. Alcohol, fines and personal entertainment are never reimbursable."),
            ("Receipts and thresholds",
             "Itemised receipts are required for any single item above {receipt_floor}. Below that "
             "threshold a card statement line is sufficient. Claims are submitted within {claim_days} "
             "days of the spend date; later claims require a written explanation."),
            ("Approval limits",
             "Line managers approve claims up to {approve_l1}. Claims above that route to the "
             "department head, and anything above {approve_l2} requires finance director sign-off. "
             "Nobody may approve their own claim or a claim from which they benefit."),
            ("Payment",
             "Approved claims are paid in the next scheduled payment run, normally within "
             "{pay_days} working days of approval."),
        ),
    ),
    Topic(
        key="fin-quarterly",
        category="Finance",
        department="Finance",
        title="Quarterly Financial Report Q{quarter} {year}",
        summary="Revenue, margin, headcount cost and variance against plan for the quarter.",
        document_type="XLSX",
        keywords=("revenue", "margin", "variance", "quarterly", "forecast"),
        sections=(
            ("Summary",
             "Revenue for the quarter was {revenue}, representing {growth} growth against the same "
             "quarter last year and {plan_var} against plan. Gross margin held at {margin}, broadly "
             "flat sequentially."),
            ("Cost base",
             "Headcount cost was {headcount_cost}, the largest single line. Cloud infrastructure spend "
             "rose to {cloud_cost} following the {system} migration, offset partially by the "
             "decommissioning of legacy hosting."),
            ("Variance commentary",
             "The shortfall against plan is concentrated in the enterprise segment, where two deals "
             "slipped into the following quarter. Pipeline coverage for the next quarter stands at "
             "{coverage} of target."),
        ),
    ),
    Topic(
        key="fin-budget",
        category="Finance",
        department="Finance",
        title="Departmental Budget Planning Guidelines {year}",
        summary="How departments build, submit and revise their annual budget.",
        document_type="XLSX",
        keywords=("budget", "planning", "forecast", "allocation", "capex"),
        sections=(
            ("Timetable",
             "Budget templates are issued in {budget_month}. Departments return draft submissions "
             "within {budget_weeks} weeks, followed by review meetings and a consolidated plan for "
             "board approval before year end."),
            ("Capital versus operating",
             "Expenditure creating an asset with a useful life beyond twelve months is capital and is "
             "depreciated over its life. Software subscriptions and contractor time are operating "
             "expenditure regardless of the size of the commitment."),
            ("Reforecasting",
             "Budgets are reforecast at the half year. A department projecting an overspend above "
             "{reforecast_threshold} must raise it at the point it becomes visible rather than waiting "
             "for the reforecast cycle."),
        ),
    ),
    Topic(
        key="fin-procurement",
        category="Finance",
        department="Finance",
        title="Procurement and Purchase Order Standard {year}",
        summary="Purchase thresholds, competitive quotes, supplier onboarding and PO discipline.",
        document_type="PDF",
        keywords=("procurement", "purchase order", "supplier", "quotes", "vendor"),
        sections=(
            ("Thresholds",
             "Purchases below {po_floor} may proceed on a company card with manager approval. Above "
             "that a purchase order is mandatory before any commitment is made to a supplier. "
             "Commitments above {quote_threshold} require at least three competitive quotes."),
            ("Supplier onboarding",
             "New suppliers complete due diligence covering financial standing, data protection "
             "posture and sanctions screening before a first order is placed. Onboarding typically "
             "takes {onboard_days} working days."),
            ("No purchase order, no payment",
             "Invoices received without a matching purchase order are returned to the supplier. This "
             "is deliberate: retrospective orders remove the control that the commitment was approved "
             "before it was made."),
        ),
    ),
    # ----------------------------------------------------------- Technical
    Topic(
        key="tech-architecture",
        category="Technical",
        department="Engineering",
        title="{system} Platform Architecture Overview v{major}.{minor}",
        summary="Service decomposition, data stores, synchronous and asynchronous paths.",
        document_type="MD",
        keywords=("architecture", "microservices", "platform", "design", "scalability"),
        sections=(
            ("Service decomposition",
             "The {system} platform is decomposed into services owning distinct data. The API gateway "
             "terminates TLS and handles authentication; downstream services trust the propagated "
             "identity rather than re-authenticating on every hop."),
            ("Data stores",
             "Relational data lives in PostgreSQL, document content in MongoDB, and vector embeddings "
             "in a dedicated vector store. Each store is owned by exactly one service; cross-store "
             "consistency is reconciled asynchronously rather than through distributed transactions."),
            ("Failure behaviour",
             "Every synchronous dependency has a timeout of {timeout} milliseconds and a defined "
             "degraded response. Search, for example, falls back to keyword results when the vector "
             "store is unavailable rather than failing the whole request."),
            ("Scaling",
             "Services are stateless and scale horizontally behind the gateway. Throughput above "
             "{rps} requests per second requires read replicas for the relational store."),
        ),
    ),
    Topic(
        key="tech-coding",
        category="Technical",
        department="Engineering",
        title="Engineering Code Standards and Review Guide v{major}.{minor}",
        summary="Style, review expectations, test requirements and merge criteria.",
        document_type="MD",
        keywords=("code review", "standards", "testing", "style", "pull request"),
        sections=(
            ("Review expectations",
             "Every change is reviewed by at least one engineer who did not write it. Reviews look for "
             "correctness, readability and test coverage. A review that only comments on formatting has "
             "not done its job; formatting is enforced by the linter, not by people."),
            ("Testing",
             "New logic ships with tests. Bug fixes ship with a test that fails before the fix. "
             "Coverage is a signal rather than a target: a module at {coverage} coverage with "
             "meaningless assertions is worse than one at half that with sharp ones."),
            ("Merge criteria",
             "A change merges when the build is green, the review is approved, and no unresolved "
             "comment remains. Long-lived branches are discouraged; anything open beyond {branch_days} "
             "days should be split."),
        ),
    ),
    Topic(
        key="tech-incident",
        category="Technical",
        department="Engineering",
        title="Incident Response and On-Call Runbook v{major}.{minor}",
        summary="Severity definitions, escalation paths, communication and post-incident review.",
        document_type="MD",
        keywords=("incident", "on-call", "severity", "escalation", "outage", "postmortem"),
        sections=(
            ("Severity",
             "A severity one incident is a full outage or data loss affecting customers. Severity two "
             "is significant degradation with a workaround. Severity three is a contained fault with no "
             "customer impact. Severity is set by the responder and may be revised upward at any time."),
            ("Escalation",
             "The on-call engineer acknowledges within {ack_minutes} minutes. If the cause is not "
             "understood within {escalate_minutes} minutes, escalate rather than continuing alone. "
             "Escalating early is never criticised; escalating late routinely is."),
            ("Communication",
             "For severity one and two, a written update goes out every {update_minutes} minutes even "
             "when there is nothing new, because silence is read as absence of progress."),
            ("Post-incident review",
             "A review is held within {review_days} working days. It examines the conditions that "
             "allowed the fault, not the individual who triggered it. Actions are assigned owners and "
             "dates or they are not actions."),
        ),
    ),
    Topic(
        key="tech-database",
        category="Technical",
        department="Engineering",
        title="Database Migration and Schema Change Policy v{major}.{minor}",
        summary="Backwards-compatible migrations, rollout sequencing and rollback.",
        document_type="MD",
        keywords=("database", "migration", "schema", "rollback", "postgresql"),
        sections=(
            ("Backwards compatibility",
             "Schema changes are applied so that the previous application version continues to work. "
             "Adding a nullable column is safe; dropping or renaming one is not, and is performed as a "
             "sequence of compatible steps across releases."),
            ("Sequencing",
             "Expand, migrate, contract. Add the new structure, backfill and dual-write, switch reads, "
             "then remove the old structure only once no deployed version references it."),
            ("Rollback",
             "Every migration has a tested rollback path or an explicit statement that it is "
             "irreversible. Backfills touching more than {backfill_rows} rows run in batches with a "
             "pause between them to protect replication lag."),
        ),
    ),
    # ------------------------------------------------------------ Research
    Topic(
        key="res-embeddings",
        category="Research",
        department="Research",
        title="Evaluation of Sentence Embedding Models for Retrieval {year}",
        summary="Comparison of small embedding models on an internal retrieval benchmark.",
        document_type="PDF",
        keywords=("embeddings", "retrieval", "evaluation", "semantic search", "benchmark"),
        sections=(
            ("Motivation",
             "Keyword retrieval fails when the query and the document use different words for the same "
             "concept. Dense embeddings map text into a vector space where semantic proximity is "
             "geometric proximity, which addresses vocabulary mismatch directly."),
            ("Method",
             "Models were compared on an internal benchmark of {queries} judged queries. Each document "
             "was chunked into overlapping windows and scored by its strongest chunk. Metrics were "
             "Recall at k, mean reciprocal rank and normalised discounted cumulative gain."),
            ("Results",
             "Dense retrieval outperformed the lexical baseline by a wide margin. Differences between "
             "similarly sized dense models were within the confidence interval of the benchmark, so no "
             "model was declared superior on evidence this thin."),
            ("Threats to validity",
             "The benchmark is small and single-domain. Absolute scores on a reduced corpus are "
             "inflated relative to a full corpus because there are fewer distractors to rank against."),
        ),
    ),
    Topic(
        key="res-ranking",
        category="Research",
        department="Research",
        title="Hybrid Ranking Strategies for Enterprise Search {year}",
        summary="Fusing lexical and semantic signals, weighting and score normalisation.",
        document_type="PDF",
        keywords=("ranking", "hybrid", "fusion", "relevance", "scoring"),
        sections=(
            ("Why fuse",
             "Lexical and semantic retrieval fail in different ways. Lexical matching is precise on "
             "exact terms such as product codes and names but blind to paraphrase. Semantic matching "
             "handles paraphrase but can drift on rare literals."),
            ("Normalisation",
             "Fusing raw scores is unsound when the two systems produce values on different scales. "
             "Cosine similarity runs from minus one to one while lexical scores are unbounded above, "
             "so both are mapped to a common range before any weighting is applied."),
            ("Weighting",
             "A fixed weighting of {kw_weight} lexical to {vec_weight} semantic performed well across "
             "query types. Weights are normalised over whichever retrievers actually ran, so a "
             "keyword-only query still spans the full score range."),
        ),
    ),
    Topic(
        key="res-chunking",
        category="Research",
        department="Research",
        title="Document Chunking Strategies and Retrieval Quality {year}",
        summary="Window size, overlap and their effect on passage-level retrieval.",
        document_type="PDF",
        keywords=("chunking", "passage retrieval", "window", "overlap", "context"),
        sections=(
            ("The problem",
             "Embedding an entire long document into one vector averages away the specific passage that "
             "answers a query. Splitting into passages restores that specificity but risks cutting the "
             "answer in half at a boundary."),
            ("Overlap",
             "Overlapping windows of {chunk_words} words with {overlap_words} words of overlap ensure "
             "any sentence appears whole in at least one chunk. The cost is a larger index, roughly "
             "proportional to the overlap fraction."),
            ("Aggregation",
             "A document is scored by its strongest chunk rather than the mean of its chunks. Averaging "
             "penalises long documents that answer the query in one place and say nothing relevant "
             "elsewhere."),
        ),
    ),
    # --------------------------------------------------------------- Legal
    Topic(
        key="legal-nda",
        category="Legal",
        department="Legal",
        title="Mutual Non-Disclosure Agreement Template v{major}.{minor}",
        summary="Standard mutual NDA, permitted disclosures, term and return of materials.",
        document_type="DOCX",
        keywords=("NDA", "confidentiality", "disclosure", "agreement", "template"),
        sections=(
            ("Confidential information",
             "Confidential information means non-public information disclosed by either party, whether "
             "marked or not, that a reasonable person would understand to be confidential given its "
             "nature and the circumstances of disclosure."),
            ("Exclusions",
             "Information is not confidential where it was already public, was already lawfully held by "
             "the receiving party, was independently developed without reference to the disclosure, or "
             "is required to be disclosed by law or court order."),
            ("Term",
             "Obligations continue for {nda_years} years from the date of disclosure. Trade secrets "
             "remain protected for as long as they retain that status, without expiry."),
            ("Return of materials",
             "On written request each party returns or destroys the other's confidential material "
             "within {return_days} days, save for one archival copy retained for compliance."),
        ),
    ),
    Topic(
        key="legal-dataprotection",
        category="Legal",
        department="Legal",
        title="Data Protection and Records Retention Policy {year}",
        summary="Lawful basis, retention periods, subject rights and breach notification.",
        document_type="PDF",
        keywords=("data protection", "privacy", "retention", "GDPR", "breach", "personal data"),
        sections=(
            ("Lawful basis",
             "Personal data is processed only where a lawful basis exists and is recorded before "
             "processing begins. Consent is used sparingly because it can be withdrawn, which makes it "
             "a poor basis for processing the business must continue to perform."),
            ("Retention",
             "Personal data is retained only as long as it serves the purpose it was collected for. "
             "Employment records are kept for {employment_retention} years after leaving, financial "
             "records for {finance_retention} years, and recruitment records for {recruit_retention} "
             "months for unsuccessful candidates."),
            ("Subject rights",
             "Requests for access, correction, erasure or portability are answered within "
             "{sar_days} days. The clock starts when the request is received, not when it reaches the "
             "right team, so requests are routed the day they arrive."),
            ("Breach notification",
             "A personal data breach likely to result in risk to individuals is notified to the "
             "supervisory authority within {breach_hours} hours of becoming aware of it."),
        ),
    ),
    Topic(
        key="legal-vendor",
        category="Legal",
        department="Legal",
        title="Vendor Contract and Supplier Terms Review {year}",
        summary="Standard contract positions, liability caps, termination and data processing terms.",
        document_type="DOCX",
        keywords=("contract", "vendor", "supplier", "liability", "termination", "SLA"),
        sections=(
            ("Liability",
             "The standard position caps aggregate liability at {liability_cap} of fees paid in the "
             "preceding twelve months. Caps are never applied to breach of confidentiality, data "
             "protection breaches, or wilful misconduct."),
            ("Service levels",
             "Availability commitments below {sla} for a service on a customer-facing path require "
             "explicit sign-off, because the vendor's availability becomes a ceiling on ours."),
            ("Termination",
             "Contracts include termination for convenience on {termination_notice} days notice. "
             "Auto-renewal terms longer than twelve months are rejected by default; they remove the "
             "opportunity to renegotiate."),
            ("Data processing",
             "Any vendor processing personal data on our behalf signs a data processing agreement "
             "covering sub-processors, international transfers and deletion on termination."),
        ),
    ),
    # -------------------------------------------------------- Administration
    Topic(
        key="admin-facilities",
        category="Administration",
        department="Administration",
        title="Office Facilities and Workspace Guide {year}",
        summary="Building access, desk booking, meeting rooms and maintenance requests.",
        document_type="PDF",
        keywords=("facilities", "office", "desk booking", "access", "meeting rooms"),
        sections=(
            ("Building access",
             "Access is by personal pass between {open_time} and {close_time} on weekdays. Out-of-hours "
             "access is enabled on request with manager approval. Passes are personal; holding a door "
             "open for someone without a pass defeats the access log entirely."),
            ("Desk booking",
             "Desks are booked through the workspace portal up to {booking_days} days ahead. "
             "Unclaimed bookings are released {release_minutes} minutes after the start of the day to "
             "avoid phantom occupancy."),
            ("Maintenance",
             "Faults are reported through the facilities queue. Anything affecting safety is treated "
             "as urgent and attended to within {urgent_hours} hours."),
        ),
    ),
    Topic(
        key="admin-travel",
        category="Administration",
        department="Administration",
        title="Business Travel and Booking Standard {year}",
        summary="Booking channels, class of travel, insurance and duty of care.",
        document_type="PDF",
        keywords=("travel", "booking", "flights", "insurance", "itinerary"),
        sections=(
            ("Booking",
             "Travel is booked through the corporate travel desk so that itineraries are visible for "
             "duty-of-care purposes. Self-booking is reimbursable only where the travel desk could not "
             "meet the requirement and this is documented."),
            ("Class of travel",
             "Economy is standard. Flights with a scheduled duration above {longhaul_hours} hours may "
             "be booked in premium economy with department head approval. Rail travel is standard class "
             "except where a cheaper flexible ticket is unavailable."),
            ("Insurance and duty of care",
             "All business travel is covered by the corporate policy. Travel to locations under a "
             "government advisory against non-essential travel requires prior risk assessment."),
        ),
    ),
    Topic(
        key="admin-security",
        category="Administration",
        department="IT",
        title="Information Security Awareness Standard {year}",
        summary="Password policy, device handling, phishing reporting and clear desk.",
        document_type="PDF",
        keywords=("security", "password", "phishing", "devices", "awareness", "MFA"),
        sections=(
            ("Authentication",
             "Multi-factor authentication is mandatory on every system holding company or personal "
             "data. Passwords are generated and stored in the approved password manager; reuse across "
             "systems is prohibited because one breach then becomes many."),
            ("Devices",
             "Company laptops are encrypted at rest and lock after {lock_minutes} minutes idle. Lost or "
             "stolen devices are reported within {report_hours} hours so the device can be wiped "
             "remotely."),
            ("Phishing",
             "Suspected phishing is reported through the report button rather than deleted. Reporting a "
             "genuine message by mistake costs nothing; deleting a real attack without reporting leaves "
             "colleagues exposed to the same campaign."),
            ("Clear desk",
             "Printed material containing personal or commercially sensitive data is not left "
             "unattended and is disposed of in confidential waste."),
        ),
    ),
)


CATEGORIES = ("HR", "Finance", "Technical", "Research", "Legal", "Administration")

assert {t.category for t in TOPICS} == set(CATEGORIES), "every category needs topics"
