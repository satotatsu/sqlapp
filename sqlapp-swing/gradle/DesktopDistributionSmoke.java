/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;

/** Run as a source file using the packaged runtime and only the packaged JARs. */
class DesktopDistributionSmoke {
    public static void main(String[] args) throws Exception {
        var drivers = DriverManager.drivers().map(driver -> driver.getClass().getName())
                .collect(Collectors.toSet());
        var required = Set.of("org.hsqldb.jdbc.JDBCDriver", "org.sqlite.JDBC",
                "org.postgresql.Driver", "com.mysql.cj.jdbc.Driver",
                "com.microsoft.sqlserver.jdbc.SQLServerDriver", "net.ucanaccess.jdbc.UcanaccessDriver");
        if (!drivers.containsAll(required)) {
            throw new IllegalStateException("Packaged JDBC services missing: "
                    + required.stream().filter(name -> !drivers.contains(name)).toList());
        }
        if (!com.formdev.flatlaf.FlatLightLaf.setup()) {
            throw new IllegalStateException("Packaged FlatLaf initialization failed.");
        }
        Class.forName("org.fife.ui.rsyntaxtextarea.RSyntaxTextArea");
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT 73")) {
            if (!rows.next() || rows.getInt(1) != 73) {
                throw new IllegalStateException("Packaged SQLite native driver failed.");
            }
        }
        try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:desktopSmoke", "SA", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("VALUES(73)")) {
            if (!rows.next() || rows.getInt(1) != 73) {
                throw new IllegalStateException("Packaged HSQLDB driver failed.");
            }
        }
        System.out.println("Packaged Java, JDBC services, SQLite native driver, HSQLDB and UI libraries verified.");
    }
}
