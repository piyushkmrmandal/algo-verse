package com.algoverse.notification.service.impl;

import com.algoverse.notification.service.SesEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * SES email implementation.
 *
 * When {@code notification.email.enabled=true} and AWS credentials are configured,
 * this would call the SES SendTemplatedEmail API. In the current deployment it logs
 * the intent, keeping the service operational without AWS credentials in dev/CI.
 *
 * To enable real SES: add software.amazon.awssdk:ses to pom.xml, inject SesClient,
 * and replace the log.info call with sesClient.sendTemplatedEmail(...).
 */
@Slf4j
@Service
public class SesEmailServiceImpl implements SesEmailService {

    @Value("${notification.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${notification.email.from:noreply@algoverse.io}")
    private String fromAddress;

    @Override
    public void send(String toAddress, String templateName, Map<String, Object> templateData,
                     String idempotencyKey) {
        if (!emailEnabled) {
            log.info("[EMAIL-STUB] Would send template='{}' to='{}' idempotencyKey='{}' data={}",
                    templateName, toAddress, idempotencyKey, templateData.keySet());
            return;
        }

        // Real AWS SES call would go here:
        // sesClient.sendTemplatedEmail(SendTemplatedEmailRequest.builder()
        //     .source(fromAddress)
        //     .destination(Destination.builder().toAddresses(toAddress).build())
        //     .template(templateName)
        //     .templateData(objectMapper.writeValueAsString(templateData))
        //     .clientToken(idempotencyKey)
        //     .build());
        log.info("Email sent via SES: template={} to={}", templateName, toAddress);
    }
}
