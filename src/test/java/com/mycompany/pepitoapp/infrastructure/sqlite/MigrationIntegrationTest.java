package com.mycompany.pepitoapp.infrastructure.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationIntegrationTest {
    @TempDir java.nio.file.Path temp;

    @Test void versionedMigrationIsIdempotent() throws Exception {
        SQLiteTestDatabase db=new SQLiteTestDatabase(temp);
        db.addProduct("A","Producto","3.50","2.20");
        db.migrate();
        db.migrate();
        assertEquals(1,db.scalarLong("SELECT COUNT(*) FROM schema_migrations WHERE version=1"));
        assertEquals(1,db.scalarLong("SELECT COUNT(*) FROM sale_price_history WHERE product_id='A'"));
    }
}
