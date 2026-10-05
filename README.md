# Enterprise Knowledge Intelligence Platform

> **Current documentation:** [docs/PROJECT_OVERVIEW.md](docs/PROJECT_OVERVIEW.md) describes the whole
> project as it stands. Each subject has its own document: [DBE-DSD](docs/database/DBE-DSD.md),
> [DSA-3](docs/algorithms/DSA-3.md), [ML](docs/ml/ML.md) and [OSSP](docs/ossp/OSSP.md). Parts of this
> README predate the implementation; for example, Qdrant now holds real MiniLM embeddings.
> Review decks and the DB final report are in [docs/presentations/](docs/presentations/)
> ([index](docs/PROJECT_OVERVIEW.md#review-presentations-and-reports)).
> Live: https://ekipsearch.vercel.app

## Project Vision
The Enterprise Knowledge Intelligence Platform is a comprehensive system designed to allow users to upload, organize, search, classify, analyze, and retrieve organizational knowledge. It is being developed as a single integrated project across four university courses, combining database management, algorithms, operating systems, and machine learning into a unified architecture.

## Four Subject Roles
This project is divided across four key subjects:
- **DataBase (DBE-DSD)**: Handles the hybrid database architecture (PostgreSQL, MongoDB, Qdrant), Spring Boot REST APIs, authentication/authorization, and React frontend.
- **DSA-3 (Data Structures & Algorithms)**: Provides the TextHack advanced algorithm engine for high-performance string matching, dynamic programming, network flow, and randomized algorithms.
- **OSSP (Operating Systems & System Programming)**: Contributes the ShellForge Linux administration shell for process management, IPC, memory management, and synchronization.
- **Machine Learning (ML)**: Implements document classification, clustering, search ranking, feature engineering, and model serving.

## 3. Database Layer (Hybrid Architecture)
The project utilizes a hybrid database architecture optimizing for different data access patterns:

- **PostgreSQL**: Stores highly structured relational metadata (Users, Roles, Permissions, Document Identity).
- **MongoDB**: Stores unstructured/semi-structured document text, processed chunks, and flexible metadata.
- **Qdrant**: Stores vector embeddings of document chunks for semantic search.

*Note: Qdrant currently uses deterministic synthetic vectors for infrastructure validation. Real embeddings will be introduced during the ML/integration phase.*

## Architecture
- **subjects/**: Contains isolated subject-specific implementations.
- **integration/**: Handles cross-subject integration logic.
- **shared/**: Contains shared interfaces, schemas, DTO contracts, and configuration specifications.

## Technology Stack
- **Backend & APIs**: Spring Boot, Java
- **Frontend**: React
- **Databases**: PostgreSQL (Relational), MongoDB (Document), Qdrant (Vector)
- **Algorithms Engine**: Java (Core Data Structures & Algorithms)
- **System Programming**: C, Linux/POSIX System Calls
- **Machine Learning**: Python, Jupyter Notebooks

## Development Roadmap
- **Phase 0**: Repository Initialization (In Progress)
- **Phase 1**: Subject-specific Core Implementations
- **Phase 2**: Integration between Subjects
- **Phase 3**: Final Testing and Deployment
