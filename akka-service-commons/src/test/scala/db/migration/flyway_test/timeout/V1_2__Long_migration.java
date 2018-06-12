/*
 * Copyright© 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package db.migration.flyway_test.timeout;

/**
 * Example of a Java-based migration.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class V1_2__Long_migration implements org.flywaydb.core.api.migration.jdbc.JdbcMigration {
    public void migrate(java.sql.Connection connection) {
        final int MAX_SLEEP_SECONDS = 10;

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