import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.HTTPProxyData;
import ch.ethz.ssh2.Session;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SshConn extends Connection implements AutoCloseable {
    public SshConn(String hostname) {
        super(hostname);
    }

    public SshConn(String hostname, int port) {
        super(hostname, port);
    }

    public SshConn(String hostname, int port, String softwareversion) {
        super(hostname, port, softwareversion);
    }

    public SshConn(String hostname, int port, HTTPProxyData proxy) {
        super(hostname, port, proxy);
    }

    public SshConn(String hostname, int port, String softwareversion, HTTPProxyData proxy) {
        super(hostname, port, softwareversion, proxy);
    }

    private Session session = null;

    public void auth() throws IOException {
        try (InputStream is = RunMe.class.getResourceAsStream("/vagrant")) {
            connect();
            char[] key = IOUtils.toCharArray(is, StandardCharsets.UTF_8);
            boolean authOk = authenticateWithPublicKey("vagrant", key, null);
            if (!authOk) {
                throw new RuntimeException("Auth not OK");
            }
        }
    }

    @Override
    public synchronized Session openSession() throws IOException {
        if (session!=null) {
            throw new RuntimeException("Session already open");
        }
        session = super.openSession();
        return session;
    }

    @Override
    public synchronized void close() {
        if (session!=null) {
            try {
                IOUtils.closeQuietly(session.getStderr());
                IOUtils.closeQuietly(session.getStdin());
                IOUtils.closeQuietly(session.getStdout());
                session.close();
                session = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        super.close();
    }
}
