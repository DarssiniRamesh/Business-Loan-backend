package com.example.BusinessLoanAPISpringBoot.notifications.provider.stub;

import com.example.BusinessLoanAPISpringBoot.notifications.config.NotificationProperties;
import com.example.BusinessLoanAPISpringBoot.notifications.model.NotificationRequest;
import com.example.BusinessLoanAPISpringBoot.notifications.provider.SmsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub implementation that does not send real SMS.
 * Useful for dev/CI environments.
 */
public class StubSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(StubSmsProvider.class);

    private final NotificationProperties props;

    public StubSmsProvider(NotificationProperties props) {
        this.props = props;
    }

    @Override
    public void send(NotificationRequest request, String toPhoneE164, String message) {
        String from = props.getSms().getFrom();
        log.info(
                "[STUB SMS] from={} to={} eventType={} draftId={} applicantUserId={} message={}",
                from,
                toPhoneE164,
                request.eventType(),
                request.draftId(),
                request.applicantUserId(),
                message
        );
    }
}
