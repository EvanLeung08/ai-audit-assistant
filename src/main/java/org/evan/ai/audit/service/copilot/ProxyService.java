package org.evan.ai.audit.service.copilot;

import org.evan.ai.audit.config.CopilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing proxy configuration.
 * Allows users to enable/disable proxy and provide authentication credentials.
 */
@Service
public class ProxyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyService.class);

    private final CopilotProperties copilotProperties;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean proxyEnabled = false;
    private volatile String proxyUsername;
    private volatile String proxyPassword;

    public ProxyService(CopilotProperties copilotProperties) {
        this.copilotProperties = copilotProperties;
    }

    /**
     * Enable proxy with optional authentication.
     */
    public void enableProxy(String username, String password) {
        lock.lock();
        try {
            this.proxyEnabled = true;
            this.proxyUsername = username;
            this.proxyPassword = password;

            // Set up system-wide proxy authentication if credentials provided
            if (username != null && !username.isEmpty()) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        if (getRequestorType() == RequestorType.PROXY) {
                            return new PasswordAuthentication(username,
                                password != null ? password.toCharArray() : new char[0]);
                        }
                        return null;
                    }
                });
                LOGGER.info("Proxy enabled with authentication for user: {}", username);
            } else {
                // Clear authenticator if no credentials
                Authenticator.setDefault(null);
                LOGGER.info("Proxy enabled without authentication");
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Disable proxy.
     */
    public void disableProxy() {
        lock.lock();
        try {
            this.proxyEnabled = false;
            this.proxyUsername = null;
            this.proxyPassword = null;
            Authenticator.setDefault(null);
            LOGGER.info("Proxy disabled");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check if proxy is enabled by user.
     */
    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    /**
     * Check if proxy server is configured in application.yml.
     */
    public boolean isProxyConfigured() {
        CopilotProperties.Proxy proxyConfig = copilotProperties.getProxy();
        return proxyConfig != null &&
               proxyConfig.getHost() != null &&
               !proxyConfig.getHost().isEmpty();
    }

    /**
     * Get proxy configuration info.
     */
    public ProxyInfo getProxyInfo() {
        CopilotProperties.Proxy proxyConfig = copilotProperties.getProxy();
        if (proxyConfig == null || proxyConfig.getHost() == null || proxyConfig.getHost().isEmpty()) {
            return new ProxyInfo(false, null, 0, false, null);
        }
        return new ProxyInfo(
                proxyEnabled,
                proxyConfig.getHost(),
                proxyConfig.getPort(),
                proxyUsername != null && !proxyUsername.isEmpty(),
                proxyUsername
        );
    }

    /**
     * Create a Proxy object for HTTP client configuration.
     */
    public Proxy createProxy() {
        if (!proxyEnabled || !isProxyConfigured()) {
            return Proxy.NO_PROXY;
        }

        CopilotProperties.Proxy proxyConfig = copilotProperties.getProxy();
        Proxy.Type proxyType = "SOCKS".equalsIgnoreCase(proxyConfig.getType())
                ? Proxy.Type.SOCKS
                : Proxy.Type.HTTP;

        return new Proxy(proxyType, new InetSocketAddress(proxyConfig.getHost(), proxyConfig.getPort()));
    }

    /**
     * Create a RestClient.Builder with proxy configuration.
     */
    public RestClient.Builder createRestClientBuilder() {
        RestClient.Builder builder = RestClient.builder();

        if (proxyEnabled && isProxyConfigured()) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setProxy(createProxy());
            builder.requestFactory(requestFactory);
            LOGGER.debug("RestClient configured with proxy: {}:{}",
                    copilotProperties.getProxy().getHost(),
                    copilotProperties.getProxy().getPort());
        }

        return builder;
    }

    /**
     * Get current proxy username.
     */
    public String getProxyUsername() {
        return proxyUsername;
    }

    /**
     * Proxy information record.
     */
    public record ProxyInfo(
            boolean enabled,
            String host,
            int port,
            boolean hasCredentials,
            String username
    ) {}
}
