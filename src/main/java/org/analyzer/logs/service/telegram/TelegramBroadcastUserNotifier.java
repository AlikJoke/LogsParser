package org.analyzer.logs.service.telegram;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.analyzer.logs.service.BroadcastUserNotifier;
import org.analyzer.logs.service.UserNotifier;
import org.analyzer.logs.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TelegramBroadcastUserNotifier implements BroadcastUserNotifier {

    @Autowired
    private UserNotifier userNotifier;
    @Autowired
    private UserService userService;

    @Override
    public void broadcast(@NonNull String message) {
        this.userService.findAllWithTelegramId()
                            .forEach(user -> this.userNotifier.notify(message, user));
    }
}