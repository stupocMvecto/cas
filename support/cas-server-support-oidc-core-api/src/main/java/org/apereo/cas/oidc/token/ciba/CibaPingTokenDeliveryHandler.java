package org.apereo.cas.oidc.token.ciba;

import org.apereo.cas.oidc.OidcConfigurationContext;
import org.apereo.cas.oidc.OidcConstants;
import org.apereo.cas.oidc.ticket.OidcCibaRequest;
import org.apereo.cas.services.OidcBackchannelTokenDeliveryModes;
import org.apereo.cas.services.OidcRegisteredService;
import org.apereo.cas.util.function.FunctionUtils;
import org.apereo.cas.util.http.HttpExecutionRequest;
import org.apereo.cas.util.http.HttpUtils;
import org.apereo.cas.util.serialization.JacksonObjectMapperFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import java.util.Map;

/**
 * This is {@link CibaPingTokenDeliveryHandler}.
 *
 * @author Misagh Moayyed
 * @since 7.1.0
 */
@RequiredArgsConstructor
@Getter
public class CibaPingTokenDeliveryHandler implements CibaTokenDeliveryHandler {
    private static final ObjectMapper MAPPER = JacksonObjectMapperFactory.builder()
        .singleValueAsArray(true).defaultTypingEnabled(false).build().toObjectMapper();

    private final OidcBackchannelTokenDeliveryModes deliveryMode = OidcBackchannelTokenDeliveryModes.PING;

    private final OidcConfigurationContext configurationContext;

    @Override
    public Map deliver(final OidcRegisteredService registeredService, final OidcCibaRequest cibaRequest) throws Throwable {
        HttpResponse response = null;
        try {
            val clientNotificationValue = (String) cibaRequest.getAuthentication().getSingleValuedAttribute(OidcConstants.CLIENT_NOTIFICATION_TOKEN);
            val payload = Map.of(OidcConstants.AUTH_REQ_ID, cibaRequest.getEncodedId());
            val exec = HttpExecutionRequest.builder()
                .bearerToken(clientNotificationValue)
                .method(HttpMethod.POST)
                .url(registeredService.getBackchannelClientNotificationEndpoint())
                .entity(MAPPER.writeValueAsString(payload))
                .httpClient(configurationContext.getHttpClient())
                .build();
            response = HttpUtils.execute(exec);
            FunctionUtils.throwIf(!HttpStatus.valueOf(response.getCode()).is2xxSuccessful(),
                () -> new HttpException("Unable to deliver tokens to client application %s".formatted(registeredService.getName())));

            cibaRequest.markTicketReady();
            configurationContext.getTicketRegistry().updateTicket(cibaRequest);
            
            return payload;
        } finally {
            HttpUtils.close(response);
        }
    }
}