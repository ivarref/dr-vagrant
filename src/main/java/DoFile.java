import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DoFile {
    public static void main(String[] args) throws Exception {
        String file = FileUtils.readFileToString(new File("setup.sh"), StandardCharsets.UTF_8);
        List<String> commands = Arrays.asList(file.split("(?m)^###(#)*$"));

        RunMe.doInit();
        RunMe.doCmds(new ArrayList<String>() {
            {
                for (String command : commands) {
                    add(command.trim());
                }
            }
        });
    }
}
