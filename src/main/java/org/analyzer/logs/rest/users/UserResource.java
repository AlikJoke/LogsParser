package org.analyzer.logs.rest.users;

import lombok.NonNull;
import org.analyzer.logs.model.UserEntity;
import org.analyzer.logs.model.UserSettings;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.annotation.Nullable;
import java.util.UUID;

public record UserResource(
        @NonNull String username,
        @NonNull String password,
        @Nullable UserSettings settings) {

    @NonNull
    public UserEntity composeEntity(final PasswordEncoder passwordEncoder) {
        return UserEntity
                .builder()
                    .username(username())
                    .hash(UUID.randomUUID().toString())
                    .settings(settings())
                    .encodedPassword(passwordEncoder.encode(password()))
                    .active(true)
                .build();
    }

    @NonNull
    public UserEntity update(
            @NonNull final UserEntity entity,
            @NonNull final PasswordEncoder passwordEncoder) {
        entity.setSettings(settings());
        entity.setEncodedPassword(passwordEncoder.encode(password()));
        return entity;
    }

    @NonNull
    public static UserResource convertFrom(@NonNull UserEntity user) {
        return new UserResource(user.getUsername(), "[protected]", user.getSettings());
    }
}