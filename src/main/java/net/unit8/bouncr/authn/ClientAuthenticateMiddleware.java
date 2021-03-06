package net.unit8.bouncr.authn;

import enkan.MiddlewareChain;
import enkan.annotation.Middleware;
import enkan.data.HttpRequest;
import enkan.data.HttpResponse;
import enkan.data.PrincipalAvailable;
import enkan.middleware.AbstractWebMiddleware;
import enkan.util.MixinUtils;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;

/**
 * The middleware for client authentication.
 *
 * A HTTP header "X-Client-DN" was set by Undertow client authentication.
 *
 * @author kawasima
 */
@Middleware(name = "ClientAuthenticate", dependencies = {"authenticate"})
public class ClientAuthenticateMiddleware extends AbstractWebMiddleware {
    private boolean isAuthenticated(PrincipalAvailable request) {
        return request.getPrincipal() != null;
    }

    @Override
    public HttpResponse handle(HttpRequest request, MiddlewareChain chain) {
        request = MixinUtils.mixin(request, new Class[]{PrincipalAvailable.class});
        String clientDN = request.getHeaders().get("X-Client-DN");
        if (!isAuthenticated((PrincipalAvailable) request) && clientDN != null) {
            RDN cn = new X500Name(clientDN).getRDNs(BCStyle.CN)[0];
            String account = IETFUtils.valueToString(cn.getFirst().getValue());

        }
        return castToHttpResponse(chain.next(request));
    }
}
