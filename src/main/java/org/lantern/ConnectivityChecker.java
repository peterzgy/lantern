package org.lantern;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.TimerTask;

import org.lantern.event.Events;
import org.lantern.state.Connectivity;
import org.lantern.state.Model;
import org.lantern.state.SyncPath;
import org.littleshoot.proxy.impl.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class ConnectivityChecker extends TimerTask {
    private static Logger LOG = LoggerFactory
            .getLogger(ConnectivityChecker.class);
    private static final String[] TEST_SITES = new String[] {
            "mail.yahoo.com",
            "www.microsoft.com",
            "blogfa.com",
            "www.baidu.com"
    };
    private static final int TEST_SOCKET_TIMEOUT_MILLIS = 30000;

    private final Model model;

    @Inject
    ConnectivityChecker(final Model model) {
        this.model = model;
    }
    
    public void connect() throws ConnectException {
        if (!checkConnectivity()) {
            throw new ConnectException("Could not connect");
        }
    }

    @Override
    public void run() {
        checkConnectivity();
    }
    
    public boolean checkConnectivity() {
        final boolean wasConnected = 
                Boolean.TRUE.equals(model.getConnectivity().isInternet());
        final InetAddress ip = localIpAddressIfConnected();
        final boolean connected = ip != null;
        this.model.getConnectivity().setInternet(connected);
        boolean becameConnected = connected && !wasConnected;
        boolean becameDisconnected = !connected && wasConnected;
        if (becameConnected) {
            LOG.info("Became connected");
            notifyConnected();
        } else if (becameDisconnected) {
            LOG.info("Became disconnected");
            notifyDisconnected();
        }
        Events.sync(SyncPath.CONNECTIVITY, model.getConnectivity());
        return connected;
    }

    private InetAddress localIpAddressIfConnected() {
        // Check if the Internet is reachable
        boolean internetIsReachable = areAnyTestSitesReachable();

        if (internetIsReachable) {
            LOG.debug("Internet is reachable...");
            try {
                return NetworkUtils.getLocalHost();
            } catch (UnknownHostException e) {
                LOG.error("Could not get local host?", e);
            }
        } 
        
        LOG.info("None of the test sites were reachable -- no internet connection");
        return null;
    }

    private void notifyConnected() {
        LOG.info("Became connected with same IP address");
        ConnectivityChangedEvent event = new ConnectivityChangedEvent(true);
        Events.asyncEventBus().post(event);
    }

    private void notifyDisconnected() {
        ConnectivityChangedEvent event = new ConnectivityChangedEvent(false);
        Events.asyncEventBus().post(event);
    }

    private static boolean areAnyTestSitesReachable() {
        for (String site : TEST_SITES) {
            if (isReachable(site)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReachable(String site) {
        Socket socket = null;
        try {
            socket = new Socket();
            LOG.debug("Testing site: {}", site);
            socket.connect(new InetSocketAddress(site, 80),
                    TEST_SOCKET_TIMEOUT_MILLIS);
            return true;
        } catch (Exception e) {
            LOG.debug("Could not connect", e);
            // Ignore
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    LOG.debug("Unable to close connectivity test socket: {}",
                            e.getMessage(), e);
                }
            }
        }
    }
}
