import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.sqlapp.data.db.command.GenerateDiffSqlCommand;
import com.sqlapp.data.db.command.html.GenerateHtmlDocsCommand;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.SchemaUtils;

/** Produces review artifacts; no connection is opened and no SQL is executed. */
public class GenerateSchemaChangeDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected before XML, after XML, and output directory");
        }
        Catalog before = SchemaUtils.readXml(new File(args[0]));
        Catalog after = SchemaUtils.readXml(new File(args[1]));
        Path output = Path.of(args[2]);
        Files.createDirectories(output);

        var diff = new GenerateDiffSqlCommand();
        diff.setOriginal(before);
        diff.setTarget(after);
        diff.run();
        var sql = new StringBuilder("-- Generated HSQL change SQL for review; not executed.\n");
        for (var operation : diff.getSqlOperations()) {
            String statement = operation.getSqlText().stripTrailing();
            sql.append(statement);
            if (!statement.endsWith(";")) {
                sql.append(';');
            }
            sql.append('\n');
        }
        Files.writeString(output.resolve("change.sql"), sql, StandardCharsets.UTF_8);
        generateHtml(new File(args[0]), output.resolve("before"));
        generateHtml(new File(args[1]), output.resolve("after"));
        System.out.println("Review " + output.resolve("change.sql").toAbsolutePath());
        System.out.println("Compare " + output.resolve("before/index.html").toAbsolutePath());
        System.out.println("   with " + output.resolve("after/index.html").toAbsolutePath());
    }

    private static void generateHtml(File snapshot, Path output) {
        var command = new GenerateHtmlDocsCommand();
        command.setTargetFile(snapshot);
        command.setOutputDirectory(output.toFile());
        command.setMultiThread(false);
        command.run();
    }
}
