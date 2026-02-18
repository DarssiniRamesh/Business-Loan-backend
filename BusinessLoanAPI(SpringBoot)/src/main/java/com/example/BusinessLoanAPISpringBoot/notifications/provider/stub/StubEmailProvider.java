package com.example.BusinessLoanAPISpringBoot.notifications.provider.stub;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.EmailProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation that does not send real email.
 * Useful for dev/CI environments.
 */
public class StubEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(StubEmailProvider.class);

    private final NotificationProperties props;

    public StubEmailProvider(NotificationProperties props) {
        this.props = props;
    }

    @Override
    public void send(NotificationRequest request, String toEmail, String subject, String bodyText) {
        String from = props.getEmail().getFrom();
        log.info(
                "[STUB EMAIL] from={} to={} subject={} eventType={} draftId={} applicantUserId={} body={}",
                from,
                toEmail,
                subject,
                request == null ? null : request.eventType(),
                request == null ? null : request.draftId(),
                request == null ? null : request.applicantUserId(),
                bodyText
        );
    }
}
