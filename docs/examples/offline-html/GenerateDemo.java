import java.io.File;

import com.sqlapp.data.db.command.html.GenerateHtmlDocsCommand;

/** Generates documentation from the fictional model without opening a database. */
public class GenerateDemo {
    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected Schema XML and output directory");
        }
        var command = new GenerateHtmlDocsCommand();
        command.setTargetFile(new File(args[0]));
        command.setOutputDirectory(new File(args[1]));
        command.setMultiThread(false);
        command.run();
        System.out.println("Open " + new File(args[1], "index.html").getAbsolutePath());
    }
}
