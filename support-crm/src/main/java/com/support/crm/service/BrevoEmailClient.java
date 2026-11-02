package com.support.crm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

// Sends email through Brevo's HTTP API. Render's free plan blocks SMTP ports, but HTTPS always works.
@Component
@Slf4j
public class BrevoEmailClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailClient(@Value("${app.mail.brevo-url:https://api.brevo.com/v3/smtp/email}") String brevoUrl,
                            @Value("${app.mail.api-key:}") String apiKey,
                            @Value("${app.mail.from-email:}") String fromEmail,
                            @Value("${app.mail.from-name:SupportDesk}") String fromName) {
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(5000);
        timeouts.setReadTimeout(5000);
        this.restClient = RestClient.builder().baseUrl(brevoUrl).requestFactory(timeouts).build();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    // A failed email must never break the ticket, so problems are only logged
    public void send(EmailMessage message) {
        if (apiKey.isBlank() || fromEmail.isBlank()) {
            log.warn("BREVO_API_KEY or MAIL_FROM_EMAIL is not set, skipping email: {}", message.subject());
            return;
        }
        try {
            restClient.post()
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "sender", Map.of("name", fromName, "email", fromEmail),
                            "to", List.of(Map.of("email", message.to())),
                            "subject", message.subject(),
                            "textContent", message.text()))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email sent: {}", message.subject());
        } catch (Exception e) {
            log.error("Failed to send email '{}': {}", message.subject(), e.getMessage());
        }
    }
}
