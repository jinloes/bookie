package com.bookie.service;

import com.bookie.config.OutlookProperties;
import com.bookie.model.*;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.OutlookTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class OutlookService {

    private static final long TOKEN_ID = 1L;
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final String AUTH_BASE = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private static final String SCOPES = "Mail.Read offline_access";

    private final OutlookTokenRepository tokenRepository;
    private final ExpenseRepository expenseRepository;
    private final OutlookProperties properties;
    private final RestClient restClient;
    private String pendingCodeVerifier;

    public OutlookService(OutlookTokenRepository tokenRepository, ExpenseRepository expenseRepository, OutlookProperties properties) {
        this.tokenRepository = tokenRepository;
        this.expenseRepository = expenseRepository;
        this.properties = properties;
        this.restClient = RestClient.create();
    }

    public String getAuthorizationUrl() {
        pendingCodeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(pendingCodeVerifier);
        return (AUTH_BASE + "/authorize?client_id=%s&response_type=code&redirect_uri=%s&scope=%s&response_mode=query&code_challenge=%s&code_challenge_method=S256")
                .formatted(properties.clientId(), encode(properties.redirectUri()), encode(SCOPES), codeChallenge);
    }

    public void handleCallback(String code) {
        String body = "client_id=%s&code=%s&grant_type=authorization_code&redirect_uri=%s&scope=%s&code_verifier=%s"
                .formatted(encode(properties.clientId()),
                        encode(code), encode(properties.redirectUri()), encode(SCOPES), encode(pendingCodeVerifier));
        pendingCodeVerifier = null;
        saveTokens(postToTokenEndpoint(body));
    }

    public boolean isConnected() {
        return tokenRepository.existsById(TOKEN_ID);
    }

    public OutlookEmailsPage getRentalEmails(int page) {
        OutlookToken token = tokenRepository.findById(TOKEN_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook not connected"));

        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            token = refreshAccessToken(token);
        }

        String accessToken = token.getAccessToken();
        List<String> folderIds = findFolderIdsByName(accessToken, "inbox", "Rent Expenses");

        List<OutlookEmail> all = folderIds.stream()
                .flatMap(id -> fetchMessagesFromFolder(id, accessToken).stream())
                .sorted((a, b) -> b.receivedAt().compareTo(a.receivedAt()))
                .map(email -> {
                    Long expenseId = expenseRepository.findBySourceId(email.id())
                            .map(Expense::getId)
                            .orElse(null);
                    return new OutlookEmail(email.id(), email.subject(), email.sender(), email.receivedAt(), email.preview(), expenseId);
                })
                .toList();

        int pageSize = 10;
        int from = page * pageSize;
        if (from >= all.size()) {
            return new OutlookEmailsPage(List.of(), page, false);
        }
        int to = Math.min(from + pageSize, all.size());
        return new OutlookEmailsPage(all.subList(from, to), page, to < all.size());
    }

    private List<OutlookEmail>fetchMessagesFromFolder(String folderIdOrName, String accessToken) {
        String url = (GRAPH_BASE + "/me/mailFolders/%s/messages?$filter=categories/any(c:c eq 'Rental')&$select=subject,from,receivedDateTime,bodyPreview&$orderby=receivedDateTime desc&$top=50")
                .formatted(folderIdOrName);

        GraphEmailsResponse response = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(GraphEmailsResponse.class);

        if (response == null || response.value() == null) {
            return List.of();
        }

        return response.value().stream()
                .map(msg -> new OutlookEmail(
                        msg.id(),
                        msg.subject(),
                        msg.from() != null && msg.from().emailAddress() != null
                                ? msg.from().emailAddress().name() : "",
                        msg.receivedDateTime(),
                        msg.bodyPreview(),
                        null))
                .toList();
    }

    public record MessageContent(String subject, String body) {}

    public MessageContent fetchMessageBody(String messageId) {
        String accessToken = getValidAccessToken();
        String url = (GRAPH_BASE + "/me/messages/%s?$select=subject,body").formatted(messageId);

        GraphMessageBody message = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(GraphMessageBody.class);

        if (message == null || message.body() == null) {
            return new MessageContent("", "");
        }
        String plainText = message.body().content().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return new MessageContent(message.subject(), plainText);
    }

    private String getValidAccessToken() {
        OutlookToken token = tokenRepository.findById(TOKEN_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook not connected"));
        if (LocalDateTime.now().isAfter(token.getExpiresAt())) {
            token = refreshAccessToken(token);
        }
        return token.getAccessToken();
    }

    private List<String> findFolderIdsByName(String accessToken, String... names) {
        String filter = java.util.Arrays.stream(names)
                .map(name -> "displayName eq '%s'".formatted(name))
                .collect(java.util.stream.Collectors.joining(" or "));

        String url = (GRAPH_BASE + "/me/mailFolders?$filter=%s").formatted(filter);

        GraphFoldersResponse response = restClient.get()
                .uri(url)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(GraphFoldersResponse.class);

        if (response == null || response.value() == null) {
            return List.of();
        }
        return response.value().stream().map(GraphFolder::id).toList();
    }

    private OutlookToken refreshAccessToken(OutlookToken existing) {
        String body = "client_id=%s&refresh_token=%s&grant_type=refresh_token&scope=%s"
                .formatted(encode(properties.clientId()), encode(existing.getRefreshToken()), encode(SCOPES));
        return saveTokens(postToTokenEndpoint(body));
    }

    private MicrosoftTokenResponse postToTokenEndpoint(String body) {
        return restClient.post()
                .uri(AUTH_BASE + "/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(MicrosoftTokenResponse.class);
    }

    private OutlookToken saveTokens(MicrosoftTokenResponse tokenResponse) {
        OutlookToken token = new OutlookToken(
                TOKEN_ID,
                tokenResponse.accessToken(),
                tokenResponse.refreshToken(),
                LocalDateTime.now().plusSeconds(tokenResponse.expiresIn() - 60));
        return tokenRepository.save(token);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generateCodeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}