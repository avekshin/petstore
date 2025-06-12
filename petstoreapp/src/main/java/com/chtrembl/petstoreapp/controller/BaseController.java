package com.chtrembl.petstoreapp.controller;

import com.chtrembl.petstoreapp.model.ContainerEnvironment;
import com.chtrembl.petstoreapp.model.User;
import com.microsoft.applicationinsights.telemetry.PageViewTelemetry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.RequestContextHolder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;

import static com.chtrembl.petstoreapp.config.Constants.AUTH_TYPE;
import static com.chtrembl.petstoreapp.config.Constants.CONTAINER_HOST;
import static com.chtrembl.petstoreapp.config.Constants.IS_AUTHENTICATED;
import static com.chtrembl.petstoreapp.config.Constants.SESSION_ID;
import static com.chtrembl.petstoreapp.config.Constants.USER_EMAIL;
import static com.chtrembl.petstoreapp.config.Constants.USER_NAME;

/**
 * Base controller providing common functionality for all domain controllers.
 * Handles session management, authentication setup, and shared model attributes.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public abstract class BaseController {

    private static final String CURRENT_USERS_HUB = "currentUsers";

    // Model attribute constants
    private static final String MODEL_APP_VERSION = "appVersion";
    private static final String MODEL_CART_SIZE = "cartSize";
    private static final String MODEL_CLAIMS = "claims";
    private static final String MODEL_CONTAINER_ENVIRONMENT = "containerEnvironment";
    private static final String MODEL_CURRENT_USERS_ON_SITE = "currentUsersOnSite";
    private static final String MODEL_EMAIL = "email";
    private static final String MODEL_GRANT_TYPE = "grant_type";
    private static final String MODEL_SESSION_ID = "sessionId";
    private static final String MODEL_USER = "user";
    private static final String MODEL_USER_LOGGED_IN = "userLoggedIn";
    private static final String MODEL_USER_NAME = "userName";

    protected final ContainerEnvironment containerEnvironment;
    protected final User sessionUser;
    protected final CacheManager currentUsersCacheManager;

    /**
     * Common model setup for all controllers.
     * Sets up session, authentication, and container information.
     */
    @ModelAttribute
    public void setModel(HttpServletRequest request, Model model, OAuth2AuthenticationToken token) {
        setupSessionUser(request, model, token); // Pass token to session setup
        setupAuthenticationDetails(model, token);
        setupContainerInfo(model);
        setupCacheInfo(model);
    }

    /**
     * Setup session user information.
     */
    private void setupSessionUser(HttpServletRequest request, Model model, OAuth2AuthenticationToken token) {
        CaffeineCache caffeineCache = (CaffeineCache) this.currentUsersCacheManager
                .getCache(CURRENT_USERS_HUB);

        if (sessionUser.getSessionId() == null) {
            String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();
            sessionUser.setSessionId(sessionId);
            caffeineCache.put(sessionUser.getSessionId(), sessionUser.getName());
        }

        if (token != null) {
            final OAuth2User user = token.getPrincipal();
            String authenticatedUserName = (String) user.getAttributes().get("name");
            if (authenticatedUserName != null && !authenticatedUserName.equals(sessionUser.getName())) {
                sessionUser.setName(authenticatedUserName);
                log.debug("Updated session user name to: {}", authenticatedUserName);
            }
        }

        MDC.put(SESSION_ID, sessionUser.getSessionId());
        MDC.put(USER_NAME, sessionUser.getName());
        MDC.put(CONTAINER_HOST, this.containerEnvironment.getContainerHostName());

        caffeineCache.put(sessionUser.getSessionId(), sessionUser.getName());

        model.addAttribute(MODEL_USER_NAME, sessionUser.getName());
        model.addAttribute(MODEL_SESSION_ID, sessionUser.getSessionId());
        model.addAttribute(MODEL_CART_SIZE, sessionUser.getCartCount());
    }

    /**
     * Setup authentication details and user information.
     */
    private void setupAuthenticationDetails(Model model, OAuth2AuthenticationToken token) {
        if (token != null) {
            final OAuth2User user = token.getPrincipal();

            String authenticatedUserName = (String) user.getAttributes().get("name");
            if (authenticatedUserName != null) {
                sessionUser.setName(authenticatedUserName);
                MDC.put(USER_NAME, authenticatedUserName);
                model.addAttribute(MODEL_USER_NAME, authenticatedUserName);
            }

            String userEmail = extractUserEmail(user);
            if (userEmail != null) {
                sessionUser.setEmail(userEmail);
                MDC.put(USER_EMAIL, userEmail);
                model.addAttribute(MODEL_EMAIL, userEmail);
                model.addAttribute(MODEL_USER_LOGGED_IN, true);
                log.debug("User email set to: {}", userEmail);
            } else {
                log.warn("Could not extract email for user: {}", sessionUser.getName());
            }

            MDC.put(AUTH_TYPE, "OAuth2");
            MDC.put(IS_AUTHENTICATED, "true");

            if (!sessionUser.isInitialTelemetryRecorded()) {
                sessionUser.getTelemetryClient().trackEvent(
                        String.format("PetStoreApp %s logged in, container host: %s",
                                sessionUser.getName(),
                                this.containerEnvironment.getContainerHostName()),
                        sessionUser.getCustomEventProperties(), null);

                sessionUser.setInitialTelemetryRecorded(true);
            }

            model.addAttribute(MODEL_CLAIMS, user.getAttributes());
            model.addAttribute(MODEL_USER, sessionUser.getName());
            model.addAttribute(MODEL_GRANT_TYPE, user.getAuthorities());
        } else {
            MDC.put(AUTH_TYPE, "Anonymous");
            MDC.put(IS_AUTHENTICATED, "false");
            model.addAttribute(MODEL_USER_LOGGED_IN, false);
        }
    }

    /**
     * Setup container environment information.
     */
    private void setupContainerInfo(Model model) {
        model.addAttribute(MODEL_CONTAINER_ENVIRONMENT, this.containerEnvironment);
        model.addAttribute(MODEL_APP_VERSION, this.containerEnvironment.getAppVersion());
    }

    /**
     * Setup cache information.
     */
    private void setupCacheInfo(Model model) {
        CaffeineCache caffeineCache = (CaffeineCache) this.currentUsersCacheManager
                .getCache(CURRENT_USERS_HUB);
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
                caffeineCache.getNativeCache();

        model.addAttribute(MODEL_CURRENT_USERS_ON_SITE, nativeCache.asMap().size());
    }

    /**
     * Track page view for telemetry.
     */
    protected void trackPageView(HttpServletRequest request, String pageName) {
        try {
            PageViewTelemetry pageViewTelemetry = new PageViewTelemetry();
            pageViewTelemetry.setUrl(new URI(request.getRequestURL().toString()));
            pageViewTelemetry.setName(pageName);
            sessionUser.getTelemetryClient().trackPageView(pageViewTelemetry);
        } catch (URISyntaxException e) {
            log.warn("Failed to track page view for {}: {}", pageName, e.getMessage());
        }
    }

    /**
     * Extract user email from OAuth2User attributes.
     */
    protected String extractUserEmail(OAuth2User user) {
        try {
            Object emailsAttribute = user.getAttribute("emails");
            if (emailsAttribute instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> emails = (Collection<String>) emailsAttribute;
                if (!emails.isEmpty()) {
                    String email = emails.iterator().next();
                    log.debug("Found email from 'emails' collection: {}", email);
                    return email.trim();
                }
            }
            log.warn("No email or subject ID found in OAuth2 attributes");
        } catch (Exception e) {
            log.warn("Error extracting email from OAuth2User: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Format stack trace for error display.
     */
    protected String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        appendStackTrace(sb, throwable, "");
        return sb.toString();
    }

    /**
     * Recursively append stack trace information.
     */
    private void appendStackTrace(StringBuilder sb, Throwable throwable, String prefix) {
        if (throwable == null) return;

        sb.append(prefix).append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element).append("\n");
        }

        for (Throwable suppressed : throwable.getSuppressed()) {
            appendStackTrace(sb, suppressed, "Suppressed: ");
        }

        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            appendStackTrace(sb, cause, "Caused by: ");
        }
    }
}