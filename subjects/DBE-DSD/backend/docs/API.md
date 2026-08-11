# API Endpoints

## 1. Application Health
- **URL**: `/api/health`
- **Method**: `GET`
- **Purpose**: Verify application is running.

**Example Response**:
```json
{
  "status": "UP",
  "service": "enterprise-knowledge-intelligence-backend"
}
```

## 2. Actuator Health
- **URL**: `/actuator/health`
- **Method**: `GET`
- **Purpose**: Internal Spring Boot metrics.

## 3. Retrieve Documents
- **URL**: `/api/documents`
- **Method**: `GET`
- **Purpose**: Fetch all document metadata.

**Example Response**:
```json
[
  {
    "id": 1,
    "title": "Q1 Financial Report",
    "description": "Financial summary for Q1 2026",
    "category": "Finance",
    "owner": "bob_finance",
    "status": "INDEXED",
    "documentType": "PDF"
  }
]
```
