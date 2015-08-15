import java.util.List;

public class Result {
    public final List<String> command;
    public final String stdOut;
    public final String stdErr;
    public final int exitCode;

    public Result(List<String> command, String stdOut, String stdErr, int exitCode) {
        this.command = command;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
        this.exitCode = exitCode;
    }

    public Result failOnError() {
        if (exitCode == 0) {
            return this;
        } else {
            System.err.println("Exit code was " + exitCode);
            System.err.println("Command was " + command);
            throw new RuntimeException("Error");
        }
    }
}
