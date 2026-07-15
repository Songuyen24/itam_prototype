# ITAM Prototype

IT Asset Management System Prototype

## Technology Stack

- **Backend**: Java 21, Spring Boot 3.3, Maven
- **Frontend**: React 18, TypeScript, Vite
- **Database**: PostgreSQL
- **Migration**: Flyway

## Architecture

Monorepo structure with feature-based organization:

```
itam-prototype/
├── backend/          # Spring Boot application
├── frontend/         # ReactJS application
├── storage/          # Local file storage
├── .env.example      # Environment variables template
└── README.md
```

## Prerequisites

- Java 21 or higher
- Maven 3.9+
- Node.js 18+ and npm
- PostgreSQL 14+

## Database Setup

1. Create PostgreSQL database:

```sql
CREATE DATABASE itam_db;
CREATE USER itam_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE itam_db TO itam_user;
```

2. Update `.env` file with your database credentials (copy from `.env.example`)

## Backend

### Running the Backend

```bash
cd backend

# Build
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

Or run the JAR directly:

```bash
java -jar target/itam-backend-0.0.1-SNAPSHOT.jar
```

The backend runs at `http://localhost:8080/api`

### Backend Structure

```
backend/src/main/java/com/company/itam/
├── ItamApplication.java
├── config/                 # Configuration classes
├── common/                 # Shared utilities
│   ├── exception/
│   ├── response/
│   ├── pagination/
│   └── util/
├── auth/                   # Authentication
├── user/                   # User management
├── catalog/                # Reference data (departments, locations, etc.)
├── asset/                  # Asset management
├── relationship/           # Asset relationships
├── transaction/            # Transaction tracking
├── workflow/               # Business workflows
│   ├── receiving/         # Asset receiving
│   ├── handover/          # Asset handover
│   ├── recovery/          # Asset recovery
│   └── disposal/          # Asset disposal
├── document/              # Document management
├── notification/          # Email notifications
├── audit/                 # Audit logging
└── report/                # Reporting
```

## Frontend

### Running the Frontend

```bash
cd frontend

# Install dependencies
npm install

# Run development server
npm run dev
```

The frontend runs at `http://localhost:5173`

### Frontend Structure

```
frontend/src/
├── app/                    # App configuration
│   ├── router.tsx         # Route definitions
│   ├── providers.tsx      # Context providers
│   └── constants.ts       # App constants
├── layouts/               # Layout components
├── features/              # Feature modules
│   ├── auth/             # Authentication
│   ├── assets/           # Asset management
│   ├── users/            # User management
│   ├── catalogs/         # Catalog management
│   ├── receiving/        # Receiving workflow
│   ├── handover/         # Handover workflow
│   ├── recovery/         # Recovery workflow
│   ├── disposal/         # Disposal workflow
│   ├── documents/        # Document management
│   ├── history/          # Transaction history
│   └── dashboard/        # Dashboard
├── shared/                # Shared utilities
│   ├── api/             # HTTP client
│   ├── components/      # Reusable components
│   ├── hooks/           # Custom hooks
│   ├── types/           # TypeScript types
│   └── utils/           # Utility functions
├── routes/               # Route guards
├── assets/              # Static assets
└── tests/               # Test utilities
```

## Features Not Yet Implemented

The following features are not yet implemented in this scaffold:

- User authentication and authorization
- Asset CRUD operations
- Workflow processes (receiving, handover, recovery, disposal)
- Document upload and storage
- Email notifications
- Audit logging
- Reports and dashboards
- Catalog management (departments, locations, suppliers, etc.)
- Database migrations (Flyway scripts)
- Integration tests
- UI components and pages

## Configuration

### Environment Variables

Copy `.env.example` to `.env` and configure:

```bash
cp .env.example .env
```

Key variables:
- `DB_URL`: PostgreSQL connection URL
- `DB_USERNAME`: Database username
- `DB_PASSWORD`: Database password
- `BACKEND_PORT`: Backend server port (default: 8080)
- `FRONTEND_PORT`: Frontend development server port (default: 5173)
- `VITE_API_BASE_URL`: Backend API URL for frontend

## License

Proprietary - Internal Use Only
