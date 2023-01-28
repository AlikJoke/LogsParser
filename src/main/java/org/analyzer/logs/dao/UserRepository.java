package org.analyzer.logs.dao;

import org.analyzer.logs.model.UserEntity;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;

public interface UserRepository extends ReactiveMongoRepository<UserEntity, String> {

    @Query("{ 'settings.cleaning_interval' : { $gt : 0 } }")
    Flux<UserEntity> findAllWithClearingSettings();

    @Nonnull
    Mono<UserEntity> findByHash(@Nonnull String hash);
}
