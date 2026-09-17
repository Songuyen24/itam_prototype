# V14 Flyway Checksum Recovery

Use this runbook only when Flyway reports a checksum mismatch for version 14 after a database ran commit `32099c3`. The repository's current V14 is byte-for-byte identical to the migration originally merged in `0dad6ff`; do not edit it or disable Flyway validation.

## Preconditions

1. Back up the database.
2. Confirm `flyway_schema_history` contains one successful row for version `14`.
3. Confirm the validation error names only V14. Stop if any other migration differs.
4. Confirm the T11B schema already contains `asset_license_details.seat_count`, `license_allocations`, `asset_relationships.allocation_id`, and the capacity triggers. Stop and restore from backup if the schema is incomplete.

## Repair

From `backend`, provide the target database through Flyway environment variables and run the pinned Flyway Maven plugin:

```powershell
$env:FLYWAY_URL = 'jdbc:postgresql://host:5432/database'
$env:FLYWAY_USER = 'database-user'
$env:FLYWAY_PASSWORD = 'database-password'

.\mvnw.cmd org.flywaydb:flyway-maven-plugin:10.10.0:info
.\mvnw.cmd org.flywaydb:flyway-maven-plugin:10.10.0:repair
.\mvnw.cmd org.flywaydb:flyway-maven-plugin:10.10.0:validate
```

`repair` updates the recorded checksum to the canonical V14 file; it does not execute V14 again. Review the `info` output before repair and retain the database backup until the application starts and the license allocation checks pass.

## Verification

Start the backend against the repaired database and verify:

- Flyway validation completes without ignored or failed migrations.
- Existing license allocations retain their package, device, user, seat count, and status.
- License totals still equal allocated plus available seats.
- Creating two concurrent reservations for the final seat allows only one reservation.
