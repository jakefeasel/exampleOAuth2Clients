import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.templ.HandlebarsTemplateEngine
import io.vertx.ext.web.handler.TemplateHandler

import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.auth.oauth2.OAuth2ClientOptions
import io.vertx.ext.auth.oauth2.impl.OAuth2TokenImpl
import io.vertx.ext.auth.oauth2.OAuth2FlowType

import io.vertx.core.shareddata.SharedData

def router  = Router.router(vertx)

// Basic UI rendered with input from IG, passed down as request headers
// IG reads user information from the id_token claims. Any claim needed by this app can
// be sent as a separate header.
router.route().pathRegex("^/admin/")
.handler({ routingContext ->
    routingContext.put("sub", routingContext.request().getHeader("x-claim-sub"))
    routingContext.next()
})
.handler(TemplateHandler.create(HandlebarsTemplateEngine.create()))

// Any route besides the root will be treated as static content
router.route().pathRegex("^/admin/.+").handler(StaticHandler.create())

// All of the below logic will have been validated by the AM policy engine (vi IG)
// before it is executed

// In this case, the actual back-end logic we want to protect for this RP happens to be
// proxied calls out to IDM, authenticated via a privileged OAuth2 token that we
// obtain using the client credential flow.
OAuth2ClientOptions opts = new OAuth2ClientOptions([
        site:"https://login.sample.forgeops.com",
        tokenPath:"/oauth2/access_token",
        clientID: "daClient",
        clientSecret: "daClientSecret",
        useBasicAuthorizationHeader: false
])
// necessary to work with self-signed certificate in development; should not be used in production
opts.setTrustAll(true)
def authProvider = OAuth2Auth.create(vertx, OAuth2FlowType.CLIENT, opts)
authProvider.authenticate([
    // This is a scope that ordinary users will not be able to request.
    scope: "user_admin"
], { res ->
  if (res.failed()) {
    System.err.println("Access Token Error: ${res.cause().getMessage()}")
  } else {
    OAuth2TokenImpl token = (OAuth2TokenImpl) res.result()
    // keep the token in a shared memory location so that future requests can read it
    SharedData sd = vertx.sharedData()
    sd.getAsyncMap('accessToken', {
        it.result().put('data', token.principal(), {})
    })
  }
})

router.route().pathRegex("^/openidm/.*")
.handler({ routingContext ->
    SharedData sd = vertx.sharedData()
    sd.getAsyncMap('accessToken', {
        it.result().get('data', { resp ->
            // re-initialize the access token from the shared memory location, to be used in the proxied request
            OAuth2TokenImpl token = new OAuth2TokenImpl(authProvider, new JsonObject(resp.result()))

            // read the current request's body payload in order to insert it into the proxied request
            routingContext.request.bodyHandler({ payload ->

                // copy all of the headers from the current request to send along with the proxied request...
                JsonObject headers = routingContext.request.headers().entries().inject(new JsonObject()) {obj, i ->
                    obj.put(i.getKey(), i.getValue())
                }
                // ... except for this one, since it will cause conflicts within the http client:
                headers.remove("host")
                headers.remove("accept-encoding")

                token.fetch(
                    routingContext.request.method(),
                    "https://rs-service.sample.forgeops.com${routingContext.normalisedPath()}?${routingContext.request.query()}",
                    headers,
                    payload,
                    { rsResponse ->
                        if (rsResponse.failed()) {
                            // TODO: check for failures due to token expiration; if detected, refresh the token and try again
                            routingContext.response().end(rsResponse.cause().getMessage())
                        } else {
                            routingContext.response().end(rsResponse.result().body())
                        }
                    }
                )
            })
        })
    })
})

def server  = vertx.createHttpServer()
server.requestHandler( router .&'accept').listen(9999)
