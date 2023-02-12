package org.analyzer.logs.service.std;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.analyzer.logs.service.UserNotifier;
import org.analyzer.logs.service.std.config.TelegramNotificationBotConfiguration;
import org.asynchttpclient.AsyncCompletionHandlerBase;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class TelegramUserNotifier implements UserNotifier {

    @Autowired
    private TelegramNotificationBotConfiguration telegramConfiguration;
    @Autowired
    private AsyncHttpClient httpClient;

    @Override
    public void notify(@NonNull String message, @NonNull String userId) {
        Arrays.stream(message.split("(?<=\\G.{4096})"))
                .forEach(part ->
                                this.httpClient
                                        .preparePost(buildOperationUrl(userId, part))
                                        .execute(new AsyncCompletionHandlerBase() {
                                            @Override
                                            public void onThrowable(Throwable t) {
                                                log.error("", t);
                                            }

                                            @Override
                                            public Response onCompleted(Response response) throws Exception {
                                                log.debug("Message notification sent with response status {}", response.getStatusCode());
                                                return super.onCompleted(response);
                                            }
                                        })
                );
    }

    private String buildOperationUrl(final String userToken, final String text) {
        return UriComponentsBuilder
                    .fromHttpUrl(this.telegramConfiguration.getOperationTemplate())
                    .queryParams(composeRequestParams(userToken, text))
                    .build()
                    .toString();
    }

    private MultiValueMap<String, String> composeRequestParams(
            final String userToken,
            final String message) {
        final MultiValueMap<String, String> params = new LinkedMultiValueMap<>(2);
        params.put(this.telegramConfiguration.getUserTokenKey(), List.of(userToken));
        params.put(this.telegramConfiguration.getMessageKey(), List.of(message));

        return params;
    }
}
