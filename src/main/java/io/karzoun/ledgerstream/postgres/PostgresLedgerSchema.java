package io.karzoun.ledgerstream.postgres;

import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import java.util.Objects;

public final class PostgresLedgerSchema {
    private PostgresLedgerSchema() { }

    public static void migrate(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .load()
                .migrate();
    }
}
