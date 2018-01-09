package db.migration.flyway_test.timeout;

/**
 * Example of a Java-based migration.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class V1_2__Long_migration implements org.flywaydb.core.api.migration.jdbc.JdbcMigration {
    public void migrate(java.sql.Connection connection) {
        final int MAX_SLEEP_SECONDS =   3;

        try {
            for (int i = 0; i < MAX_SLEEP_SECONDS*10; i++) {
                if (i % 10 == 0) {
                    System.out.println("sleeping for another 1s period... (" + i + "/" + MAX_SLEEP_SECONDS + ")");
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException ex) {
            System.out.println("damn got exception:" + ex.getMessage());
            ex.printStackTrace();
        }

    }
}