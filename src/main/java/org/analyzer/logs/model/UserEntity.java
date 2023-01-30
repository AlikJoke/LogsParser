package org.analyzer.logs.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document("users")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class UserEntity {

    @Id
    @EqualsAndHashCode.Include
    @NonNull
    @Indexed(unique = true)
    private String username;
    @NonNull
    @ToString.Exclude
    @Field("encoded_password")
    private String encodedPassword;
    @NonNull
    @ToString.Exclude
    @Indexed(unique = true)
    private String hash;
    @Indexed
    private LocalDateTime modified;

    private boolean active;

    private UserSettings settings;

    public boolean disable() {
        if (this.active) {
            this.active = false;
            return true;
        }

        return false;
    }
}
