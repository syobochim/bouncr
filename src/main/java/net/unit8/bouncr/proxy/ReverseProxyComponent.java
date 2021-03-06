package net.unit8.bouncr.proxy;

import enkan.application.WebApplication;
import enkan.collection.Headers;
import enkan.collection.OptionMap;
import enkan.component.ApplicationComponent;
import enkan.component.ComponentLifecycle;
import enkan.component.WebServerComponent;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.exception.MisconfigurationException;
import enkan.exception.ServiceUnavailableException;
import enkan.exception.UnreachableException;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.io.IoCallback;
import io.undertow.io.Sender;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationMode;
import io.undertow.security.api.SecurityContext;
import io.undertow.security.handlers.AuthenticationCallHandler;
import io.undertow.security.handlers.AuthenticationConstraintHandler;
import io.undertow.security.handlers.AuthenticationMechanismsHandler;
import io.undertow.security.handlers.SecurityInitialHandler;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.Credential;
import io.undertow.security.idm.IdentityManager;
import io.undertow.security.idm.X509CertificateCredential;
import io.undertow.security.impl.ClientCertAuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ResponseCodeHandler;
import io.undertow.server.handlers.proxy.ProxyHandler;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import net.unit8.bouncr.cert.ReloadableTrustManager;
import net.unit8.bouncr.component.BouncrConfiguration;
import net.unit8.bouncr.component.RealmCache;
import net.unit8.bouncr.component.StoreProvider;
import net.unit8.bouncr.sign.JsonWebToken;
import org.xnio.Options;
import org.xnio.SslClientAuthMode;
import org.xnio.streams.ChannelInputStream;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static enkan.util.ThreadingUtils.some;
import static net.unit8.bouncr.component.StoreProvider.StoreType.BOUNCR_TOKEN;

/**
 * The component for an reverse proxy server.
 *
 * @author kawasima
 */
public class ReverseProxyComponent extends WebServerComponent {
    private static IoCallback callback = new IoCallback() {
        @Override
        public void onComplete(HttpServerExchange exchange, Sender sender) {

        }

        @Override
        public void onException(HttpServerExchange exchange, Sender sender, IOException exception) {

        }
    };

    private int ioThreads = 4;

    private int maxRequestTime = 30000;

    private boolean rewriteHostHeader = false;

    private boolean reuseXForwarded = true;

    private Undertow server;

    @Override
    protected ComponentLifecycle<ReverseProxyComponent> lifecycle() {
        return new ComponentLifecycle<ReverseProxyComponent>() {
            @Override
            public void start(ReverseProxyComponent component) {
                StoreProvider storeProvider = getDependency(StoreProvider.class);
                RealmCache realmCache = getDependency(RealmCache.class);
                ApplicationComponent app = getDependency(ApplicationComponent.class);
                BouncrConfiguration config = getDependency(BouncrConfiguration.class);
                JsonWebToken jwt = getDependency(JsonWebToken.class);

                if (server == null) {
                    OptionMap options = buildOptionMap();

                    HttpHandler appHandler = createAdminApp((WebApplication) app.getApplication());
                    MultiAppProxyClient proxyClient = new MultiAppProxyClient(config, storeProvider.getStore(BOUNCR_TOKEN), realmCache, jwt);
                    ProxyHandler proxyHandler = new ProxyHandler(proxyClient, maxRequestTime, ResponseCodeHandler.HANDLE_404, rewriteHostHeader, reuseXForwarded);

                    IdentityManager identityManager = new IdentityManager() {
                        @Override
                        public Account verify(Account account) {
                            return account;
                        }

                        @Override
                        public Account verify(String id, Credential credential) {
                            return null;
                        }

                        @Override
                        public Account verify(Credential credential) {
                            if (credential instanceof X509CertificateCredential) {
                                X509CertificateCredential x509cert = (X509CertificateCredential) credential;
                                return new Account(){
                                    @Override
                                    public Principal getPrincipal() {
                                        return x509cert.getCertificate().getSubjectX500Principal();
                                    }

                                    @Override
                                    public Set<String> getRoles() {
                                        return Collections.emptySet();
                                    }
                                };
                            }
                            return null;
                        }
                    };

                    Undertow.Builder builder = Undertow.builder()
                            .setHandler(addSecurity(
                                    Handlers.path()
                                            .addPrefixPath("/my", appHandler)
                                            .addPrefixPath("/admin", appHandler)
                                            .addPrefixPath("/", proxyHandler)
                                    , identityManager, options)
                            );

                    if (options.getBoolean("http?", true)) {
                        builder.addHttpListener(options.getInt("port"), options.getString("host"));
                    }
                    if (options.getBoolean("ssl?", false)) {
                        builder = builder.addHttpsListener(options.getInt("sslPort"), options.getString("host"),
                                createSSLContext(options));
                    }
                    if (options.get("truststore") != null) {
                        builder = builder.setSocketOption(Options.SSL_CLIENT_AUTH_MODE, SslClientAuthMode.REQUESTED);
                    }
                    server = builder.build();
                    server.start();
                }

            }

            @Override
            public void stop(ReverseProxyComponent component) {
                if (server != null) {
                    server.stop();
                    server = null;
                }
            }
        };
    }

    public void setMaxRequestTime(int maxRequestTime) {
        this.maxRequestTime = maxRequestTime;
    }

    public void setIoThreads(int ioThreads) {
        this.ioThreads = ioThreads;
    }

    private KeyManager[] getKeyManagers(OptionMap options) throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException, UnrecoverableKeyException {
        KeyStore keystore = (KeyStore) options.get("keystore");
        if (keystore != null) {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, options.getString("keystorePassword").toCharArray());
            return keyManagerFactory.getKeyManagers();
        } else {
            return null;
        }
    }


    private SSLContext createSSLContext(OptionMap options)  {
        try {
            SSLContext context = SSLContext.getInstance("TLSv1.2");
            ReloadableTrustManager trustManager = getDependency(ReloadableTrustManager.class);
            context.init(getKeyManagers(options),
                    trustManager.initialized() ? new TrustManager[]{ trustManager } : null,
                    null);
            return context;
        } catch (NoSuchAlgorithmException | KeyManagementException | UnrecoverableKeyException | CertificateException | KeyStoreException | IOException e) {
            // TODO
            throw new MisconfigurationException("bouncr.", e);
        }
    }

    private HttpHandler createAdminApp(WebApplication application) {
        return new HttpHandler() {
            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                if (exchange.isInIoThread()) {
                    exchange.dispatch(this);
                    return;
                }
                HttpRequest request = new DefaultHttpRequest();
                request.setRequestMethod(exchange.getRequestMethod().toString());
                request.setUri(exchange.getRequestURI());
                request.setProtocol(exchange.getProtocol().toString());
                request.setQueryString(exchange.getQueryString());
                request.setCharacterEncoding(exchange.getRequestCharset());
                request.setBody(new ChannelInputStream(exchange.getRequestChannel()));
                request.setContentLength(exchange.getRequestContentLength());
                request.setRemoteAddr(exchange.getSourceAddress()
                        .getAddress()
                        .getHostAddress());
                request.setScheme(exchange.getRequestScheme());
                request.setServerName(exchange.getHostName());
                request.setServerPort(exchange.getHostPort());

                Headers headers = Headers.empty();
                exchange.getRequestHeaders().forEach(e -> {
                    String headerName = e.getHeaderName().toString();
                    e.forEach(v -> headers.put(headerName, v));
                });
                some(exchange.getSecurityContext(),
                        SecurityContext::getAuthenticatedAccount,
                        Account::getPrincipal,
                        principal -> headers.put("X-Client-DN", principal.getName()));
                request.setHeaders(headers);

                try {
                    HttpResponse response = application.handle(request);
                    exchange.setStatusCode(response.getStatus());
                    setResponseHeaders(response.getHeaders(), exchange);
                    exchange.startBlocking();
                    setBody(exchange.getResponseSender(), response.getBody());
                } catch (ServiceUnavailableException ex) {
                    exchange.setStatusCode(503);
                } finally {
                    exchange.endExchange();
                }
            }
        };
    }

    private static void setBody(Sender sender, Object body) throws IOException {
        if (body == null) {
            return; // Do nothing
        }

        if (body instanceof String) {
            sender.send((String) body);
        } else if (body instanceof InputStream) {
            ReadableByteChannel chan = Channels.newChannel((InputStream) body);

            ByteBuffer buf = ByteBuffer.allocate(4096);
            for (;;) {
                int size = chan.read(buf);
                if (size <= 0) break;
                buf.flip();
                sender.send(buf, callback);
                buf.clear();
            }
            sender.close(IoCallback.END_EXCHANGE);
        } else if (body instanceof File) {
            try(FileInputStream fis = new FileInputStream((File) body);
                FileChannel chan = fis.getChannel()) {
                ByteBuffer buf = ByteBuffer.allocate(4096);
                for (;;) {
                    int size = chan.read(buf);
                    if (size <= 0) break;
                    buf.flip();
                    sender.send(buf, callback);
                    buf.clear();
                }
                sender.close(IoCallback.END_EXCHANGE);
            }
        } else {
            throw new UnreachableException();
        }
    }

    private void setResponseHeaders(Headers headers, HttpServerExchange exchange) {
        HeaderMap map = exchange.getResponseHeaders();
        headers.keySet().forEach(headerName -> headers.getList(headerName)
                .forEach(v -> {
                    if (v instanceof String) {
                        map.add(HttpString.tryFromString(headerName), (String) v);
                    } else if (v instanceof Number) {
                        map.add(HttpString.tryFromString(headerName), ((Number) v).longValue());
                    }
                }));
    }

    private HttpHandler addSecurity(final HttpHandler toWrap, final IdentityManager identityManager, OptionMap options) {
        HttpHandler handler = toWrap;
        KeyStore truststore = (KeyStore) options.get("truststore");
        if (truststore != null) {
            handler = new AuthenticationCallHandler(handler);
            handler = new AuthenticationConstraintHandler(handler) {
                @Override
                protected boolean isAuthenticationRequired(final HttpServerExchange exchange) {
                    return false;
                }
            };
            final List<AuthenticationMechanism> mechanisms = Collections.singletonList(new ClientCertAuthenticationMechanism("Bouncr"));
            handler = new AuthenticationMechanismsHandler(handler, mechanisms);
            handler = new SecurityInitialHandler(AuthenticationMode.PRO_ACTIVE, identityManager, handler);
        }
        return handler;
    }
}
