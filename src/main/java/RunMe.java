import ch.ethz.ssh2.Session;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

public class RunMe {

    public static String vmName() {
        return "ubuntu-cloudimg-trusty-vagrant-amd64";
    }

    public static void main(String[] args) throws Exception {
        if (!listVms().contains(vmName())) {
            throw new RuntimeException(vmName() + " VM not found");
        }

        if (!runningVms().contains(vmName())) {
            System.out.println(">>> VM " + vmName() + " not running, starting ...");
            executeAndReport("startvm", vmName(), "--type", "headless");
        }
        System.out.println(">>> VM is running");
        System.out.println(">>> Checking for SSH");
        waitSsh();
        System.out.println(">>> SSH OK");

        if (snapShots().isEmpty()) {
            System.out.println(">>> Taking initial snapshot ...");
            takeSnapshot(Arrays.asList());
        }

        System.out.println(">>> Snapshots = " + snapShots());

        List<String> cmds = new ArrayList<>();
        cmds = run(cmds, "apt-get -y update");
        cmds = run(cmds, "apt-get -y upgrade");
        cmds = run(cmds, "echo hello > " + "random-" + UUID.randomUUID().toString() + ".txt");

        debug("ls -l /home/vagrant");
    }

    private static void pump(InputStream is) throws Exception {
        while (is.available() > 0) {
            int c = is.read();
            if (c == -1) break;
            System.out.print((char) c);
            System.out.flush();
        }
    }

    private static void debug(String command) throws Exception {
        try (SshConn conn = new SshConn("localhost", 2222)) {
            conn.auth();
            Session sess = conn.openSession();
            sess.execCommand(command);
            System.out.println(">>> Exec " + command);
            InputStream is = sess.getStdout();
            InputStream err = sess.getStderr();
            while (sess.getExitStatus() == null) {
                pump(is);
                pump(err);
                Thread.sleep(100);
            }
            pump(is);
            pump(err);

            System.out.println(">>> Command exited with code " + sess.getExitStatus().intValue());
        }
    }

    private static List<String> run(List<String> cmds, String command) throws Exception {
        String requiredSnapshot = snapshotName(cmds);
        if (!snapShots().contains(requiredSnapshot)) {
            String message = "Required snapshot " + requiredSnapshot + " not found, don't know what to do";
            System.err.println(message);
            throw new RuntimeException(message);
        }

        String newSnapshot = snapshotName(add(cmds, command));
        if (!snapShots().contains(newSnapshot)) {
            List<String> snapsToDelete = new ArrayList<>();
            for (String snap : snapShots()) {
                int idx = Integer.valueOf(snap.split("-")[1]);
                int selfIdx = Integer.valueOf(newSnapshot.split("-")[1]);
                if (idx >= selfIdx) {
                    snapsToDelete.add(snap);
                }
            }
            if (!snapsToDelete.isEmpty()) {
                System.out.println(">>> Deleting snapshots " + snapsToDelete);
                executeAndReport("controlvm", vmName(), "poweroff");
                executeAndReport("snapshot", vmName(), "restore", requiredSnapshot);
                Collections.reverse(snapsToDelete);
                for (String snap : snapsToDelete) {
                    executeAndReport("snapshot", vmName(), "delete", snap);
                }
                executeAndReport("startvm", vmName(), "--type", "headless");
                waitSsh();
            }
        }

        if (snapShots().contains(newSnapshot)) {
            System.out.println(">>> Command " + command + " already executed, doing nothing ...");
            return add(cmds, command);
        } else {
            System.out.println(">>> Executing '" + command + "' and saving as '" + newSnapshot + "' ...");
            String executeable = "/home/vagrant/cmd-" + newSnapshot + ".sh";

            try (SshConn conn = new SshConn("localhost", 2222)) {
                conn.auth();
                Session sess = conn.openSession();
                sess.execCommand("cat > " + executeable);
                try (OutputStream os = sess.getStdin()) {
                    IOUtils.write(command.getBytes(StandardCharsets.UTF_8), os);
                }
                try (InputStream is = sess.getStdout()) {
                    try (InputStream err = sess.getStderr()) {
                    }
                }
                while (sess.getExitStatus() == null) {
                    Thread.sleep(100);
                }
                int exitCode = sess.getExitStatus().intValue();
                if (exitCode == 0) {
                    System.out.println(">>> Transfer OK");
                } else {
                    String message = ">>> Transfer failed, exit code " + exitCode;
                    System.err.println(message);
                    throw new RuntimeException(message);
                }
            }

            int exitCode = 1;
            try (SshConn conn = new SshConn("localhost", 2222)) {
                conn.auth();
                Session sess = conn.openSession();
                sess.execCommand("sudo /bin/bash " + executeable);
                InputStream out = sess.getStdout();
                InputStream err = sess.getStderr();
                try (OutputStream os = sess.getStdin()) {

                }

                while (sess.getExitStatus() == null) {
                    pump(out);
                    pump(err);
                    Thread.sleep(100);
                }

                pump(out);
                pump(err);

                System.out.println(">>> Exit code was " + sess.getExitStatus().intValue());
                exitCode = sess.getExitStatus().intValue();
            }

            if (exitCode == 0) {
                System.out.println(">>> Saving snapshot");
                takeSnapshot(add(cmds, command));
            } else {
                System.err.println(">>> Reverting to snapshot " + snapshotName(cmds));
                String message = "Command '" + command + "' exited with error status " + exitCode;
                System.err.println(message);
                revertToSnapshot(cmds);
                throw new RuntimeException(message);
            }
        }
        return add(cmds, command);
    }

    private static void revertToSnapshot(List<String> cmds) {
        executeAndReport("controlvm", vmName(), "poweroff");
        executeAndReport("snapshot", vmName(), "restore", snapshotName(cmds));
        executeAndReport("startvm", vmName(), "--type", "headless");
        waitSsh();
    }

    public static List<String> add(List<String> existing, String newElem) {
        return new ArrayList<String>(existing) {{
            add(newElem);
        }};
    }

    public static String snapshotName(List<String> cmds) {
        String cmdJoined = String.join("\n", cmds);
        String name = String.format("snap-%03d-%s", cmds.size(), cmdJoined.hashCode() + "");
        return name;
    }

    public static void takeSnapshot(List<String> cmds) {
        String name = snapshotName(cmds);
        if (snapShots().contains(name)) {
            String message = "Tried to overwrite existing snapshot! Name = " + name;
            System.err.println(message);
            throw new RuntimeException(message);
        }
        executeAndReport("snapshot", vmName(), "take", name);
    }

    public static List<String> snapShots() {
        List<String> snaps = new ArrayList<>();
        Result result = execute("snapshot", vmName(), "list", "--machinereadable");
        if (result.exitCode == 1 && result.stdOut.trim().equals("This machine does not have any snapshots")) {
            return Arrays.asList();
        } else if (result.exitCode == 0) {
            for (String line : result.stdOut.split("\n")) {
                if (line.trim().equals("")) continue;
                if (line.startsWith("SnapshotName")) {
                    snaps.add(unquote(line.split("=")[1]));
                }
            }
        } else {
            result.failOnError();
            throw new RuntimeException("Unhandled state");
        }

        return snaps;
    }

    private static String unquote(String s) {
        if (s.startsWith("\"")) {
            s = s.substring(1);
        }
        if (s.endsWith("\"")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static void waitSsh() {
        LocalDateTime timeOut = LocalDateTime.now().plusMinutes(5);
        while (LocalDateTime.now().isBefore(timeOut)) {
            try (SshConn conn = new SshConn("localhost", 2222)) {
                try (InputStream is = RunMe.class.getResourceAsStream("/vagrant")) {
                    conn.connect();
                    char[] key = IOUtils.toCharArray(is, StandardCharsets.UTF_8);
                    boolean authOk = conn.authenticateWithPublicKey("vagrant", key, null);
                    if (authOk) {
                        return;
                    } else {
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Warning: " + e.getMessage());
                }
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static List<String> listVms() {
        Result result = execute("list", "vms").failOnError();
        return getQuoteFirstPartOfLine(result);
    }

    private static List<String> getQuoteFirstPartOfLine(Result result) {
        String[] lines = result.stdOut.split("\n");
        List<String> vms = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().equals("")) continue;
            String substring = line.substring(1, line.indexOf('"', 1));
            vms.add(substring);
        }
        return vms;
    }

    public static List<String> runningVms() {
        return getQuoteFirstPartOfLine(execute("list", "runningvms").failOnError());
    }

    public static Result executeAndReport(String... args) {
        Result res = execute(args);
        if (res.exitCode == 0) {
            System.out.println(">>> [OK] " + Arrays.asList(args));
        } else {
            System.out.println(">>> [Fail] " + Arrays.asList(args));
            throw new RuntimeException("Got exit code " + res.exitCode);
        }
        return res;
    }

    public static Result execute(String... args) {
        try {
            List<String> arg = new ArrayList<>();
            arg.add(vboxmanage());
            for (String s : args) {
                arg.add(s);
            }
            String[] arguments = arg.toArray(new String[arg.size()]);
            Process process = Runtime.getRuntime().exec(arguments);
            String out = "";
            String err = "";
            try (OutputStream os = process.getOutputStream()) {
                try (InputStream stdOut = process.getInputStream()) {
                    try (InputStream stdErr = process.getErrorStream()) {
                        err = IOUtils.toString(stdErr, StandardCharsets.UTF_8);
                        out = IOUtils.toString(stdOut, StandardCharsets.UTF_8);
                    }
                }
            }
            while (process.isAlive()) {
                Thread.sleep(100);
            }
            return new Result(arg, out, err, process.exitValue());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static String vboxmanage() {
        if (System.getProperty("vboxmanage") != null) {
            return System.getProperty("vboxmanage");
        }
        for (String file : Arrays.asList("/usr/local/bin/vboxmanage", "/usr/bin/vboxmanage")) {
            if (new File(file).exists()) {
                return file;
            }
        }
        throw new RuntimeException("Could not find vboxmanage");
    }

}
