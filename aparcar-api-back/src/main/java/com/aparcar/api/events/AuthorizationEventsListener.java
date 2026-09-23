package com.aparcar.api.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthorizationEventsListener {

    @EventListener
    public void onFailure(AuthorizationDeniedEvent deniedEvent) {
        try {
            log.error("Authorization failed for the user {} due to {}", deniedEvent.getAuthentication().get().getName(),
                    deniedEvent.getAuthorizationResult().toString());

        } catch (AuthenticationCredentialsNotFoundException e) {
            log.error("Authorization failed due to missing authentication credentials: {}", e.getMessage());
        } catch (Exception e) {
            log.error("An unexpected error occurred during authorization: {}", e.getMessage(), e);
        }
    }
}
