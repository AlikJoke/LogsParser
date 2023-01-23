package org.analyzer.logs.service;

import org.analyzer.logs.model.LogRecordEntity;
import reactor.core.publisher.Flux;

import javax.annotation.Nonnull;
import java.util.function.Function;

public interface PostFilter extends Function<Flux<LogRecordEntity>, Flux<LogRecordEntity>> {

    @Nonnull
    Object getParameters();

    void setParameters(@Nonnull Object parameters);

    @Nonnull
    Class<?> getParametersClass();

    @Nonnull
    String getName();
}
