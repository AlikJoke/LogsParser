package org.analyzer.logs.service.elastic;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import org.analyzer.logs.dao.LogRecordRepository;
import org.analyzer.logs.dao.LogsStatisticsRepository;
import org.analyzer.logs.model.LogRecordEntity;
import org.analyzer.logs.model.LogsStatisticsEntity;
import org.analyzer.logs.model.UserEntity;
import org.analyzer.logs.service.*;
import org.analyzer.logs.service.std.DefaultLogsAnalyzer;
import org.analyzer.logs.service.std.postfilters.PostFiltersSequenceBuilder;
import org.analyzer.logs.service.util.JsonConverter;
import org.analyzer.logs.service.util.LongRunningTaskExecutor;
import org.analyzer.logs.service.util.UnzipperUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import reactor.util.Logger;
import reactor.util.Loggers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class ElasticLogsService implements LogsService {

    private static final Logger logger = Loggers.getLogger(ElasticLogsService.class);

    private static final String STATISTICS_CACHE = "statistics";

    @Autowired
    private ElasticsearchTemplate template;
    @Autowired
    private LogRecordRepository logRecordRepository;
    @Autowired
    private LogRecordsParser parser;
    @Autowired
    private UnzipperUtil zipUtil;
    @Autowired
    private SearchQueryParser<StringQuery> queryParser;
    @Autowired
    private PostFiltersSequenceBuilder postFiltersSequenceBuilder;
    @Autowired
    private DefaultLogsAnalyzer logsAnalyzer;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private LogsStatisticsRepository statisticsRepository;
    @Autowired
    private LongRunningTaskExecutor taskExecutor;
    @Autowired
    private CurrentUserAccessor userAccessor;
    @Autowired
    private LogKeysFactory logKeysFactory;
    @Autowired
    private JsonConverter jsonConverter;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CurrentUserQueryService currentUserQueryService;

    private Counter indexedRecordsCounter;
    private Counter indexedFilesCounter;
    private Counter simpleSearchRequestsCounter;
    private Counter extendedSearchRequestsCounter;
    private Counter logsAnalyzeCounter;
    private Counter elasticIndexRequestsCounter;

    @PostConstruct
    private void init() {
        this.indexedRecordsCounter = createMeterCounter("logs.indexed.records", "All logs indexed records count", null);
        this.indexedFilesCounter = createMeterCounter("logs.indexed.files", "All indexed logs files count", null);
        this.simpleSearchRequestsCounter = createMeterCounter("logs.simple.search.requests", "All simple search requests count", "simple");
        this.extendedSearchRequestsCounter = createMeterCounter("logs.extended.search.requests", "All extended search requests count", "extended");
        this.logsAnalyzeCounter = createMeterCounter("logs.analyze.requests", "All logs analyze requests count", null);
        this.elasticIndexRequestsCounter = createMeterCounter("logs.index.requests", "All logs index requests to elastic count", null);
    }

    @Override
    @NonNull
    public IndexingProcess index(
            @NonNull File logFile,
            @Nullable LogRecordFormat recordFormat) {

        final var uuidKey = UUID.randomUUID().toString();
        final var userEntity = this.userAccessor.get();
        final var processFuture = this.taskExecutor.execute(
                () -> this.zipUtil.flat(logFile)
                                    .forEach(file -> processLogFile(userEntity, uuidKey, recordFormat, file))
        );

        return new IndexingProcess(uuidKey, processFuture);
    }

    @Nonnull
    @Override
    public List<String> searchByQuery(@Nonnull SearchQuery searchQuery) {
        final var user = this.userAccessor.get();
        this.taskExecutor.execute(
                () -> {
                    try (final var userContext = this.userAccessor.as(user)) {
                        this.currentUserQueryService.create(searchQuery);
                    }
                }
        );

        return searchByFilterQuery(searchQuery)
                .stream()
                .map(LogRecordEntity::getSource)
                .toList();
    }

    @NonNull
    @Override
    public MapLogsStatistics analyze(@NonNull AnalyzeQuery analyzeQuery) {
        final var filteredRecords = searchByFilterQuery(analyzeQuery);
        return analyze(filteredRecords, analyzeQuery);
    }

    @NonNull
    @Override
    public Optional<LogsStatisticsEntity> findStatisticsByKey(@NonNull String key) {
        final var valueOps = this.redisTemplate.opsForValue();
        final var entityFromCache = valueOps.get(createStatsRedisKey(key));
        if (entityFromCache != null) {
            return Optional.of(entityFromCache)
                            .map(LogsStatisticsEntity.class::cast);
        }

        final var entityFromStorage = this.statisticsRepository.findByDataQueryRegexOrId(key, key);
        entityFromStorage
                .ifPresent(stats -> valueOps.set(createStatsRedisKey(key), stats));

        return entityFromStorage;
    }

    @NonNull
    @Override
    public List<LogsStatisticsEntity> findAllStatisticsByUserKeyAndCreationDate(
            @NonNull String userKey,
            @NonNull LocalDateTime beforeDate) {
        return this.statisticsRepository.findAllByUserKeyAndCreationDateBefore(userKey, beforeDate);
    }

    @Override
    public void deleteStatistics(@NonNull List<LogsStatisticsEntity> stats) {
        this.statisticsRepository.deleteAll(stats);
    }

    @NonNull
    @Override
    public List<String> deleteAllStatisticsByUserKeyAndCreationDate(@NonNull String userKey, @NonNull LocalDateTime beforeDate) {
        return this.statisticsRepository.deleteAllByUserKeyAndCreationDateBefore(userKey, beforeDate)
                                        .stream()
                                        .map(LogsStatisticsEntity::getId)
                                        .peek(indexingKeys -> deleteAllStatsKeys())
                                        .toList();
    }

    @Override
    public void deleteByQuery(@NonNull SearchQuery deleteQuery) {
        this.logRecordRepository.deleteAll(searchByFilterQuery(deleteQuery));
    }

    private void deleteAllStatsKeys() {
        final var scanOptions = ScanOptions
                                    .scanOptions()
                                        .match(STATISTICS_CACHE + ":*")
                                    .build();

        try (final var cursor = this.redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                this.redisTemplate.delete(cursor.next());
            }
        }
    }

    private MapLogsStatistics analyze(
            final List<LogRecordEntity> records,
            final AnalyzeQuery analyzeQuery) {

        final var stats = this.logsAnalyzer.analyze(records, analyzeQuery);
        logsAnalyzeCounter.increment();

        processStatsSaving(analyzeQuery, stats, this.userAccessor.get().getHash());
        return stats;
    }

    private void processStatsSaving(
            final AnalyzeQuery analyzeQuery,
            final MapLogsStatistics stats,
            final String userKey) {

        if (analyzeQuery.save()) {
            saveStatsEntity(analyzeQuery, stats.toResultMap(), userKey);
        }
    }

    private void saveStatsEntity(
            final AnalyzeQuery analyzeQuery,
            final Map<String, Object> stats,
            final String userKey) {

        final var entity =
                new LogsStatisticsEntity()
                        .setId(analyzeQuery.getId())
                        .setCreated(LocalDateTime.now())
                        .setTitle(analyzeQuery.analyzeResultName())
                        .setDataQuery(analyzeQuery.toSearchQuery().toJson(this.jsonConverter))
                        .setUserKey(userKey)
                        .setStats(stats);
        this.statisticsRepository.save(entity);
    }

    private List<LogRecordEntity> searchByFilterQuery(@Nonnull SearchQuery searchQuery) {

        final var user = this.userAccessor.get();
        final var query = this.queryParser.parse(searchQuery, user.getHash());
        (searchQuery.extendedFormat() ? extendedSearchRequestsCounter : simpleSearchRequestsCounter).increment();

        final var postFilters = this.postFiltersSequenceBuilder.build(searchQuery.postFilters());

        final List<LogRecordEntity> logRecords =
                template.search(query, LogRecordEntity.class)
                        .stream()
                        .map(SearchHit::getContent)
                        .toList();

        return postFilters
                .stream()
                .reduce(Function.<List<LogRecordEntity>> identity(), Function::andThen, (pf1, pf2) -> pf2)
                .apply(logRecords);
    }

    private Counter createMeterCounter(
            final String metricName,
            final String description,
            final String type) {
        final var builder = Counter.builder(metricName)
                                    .description(description);
        if (type != null) {
            builder.tag("type", type);
        }

        return builder.register(this.meterRegistry);
    }

    private void processLogFile(final UserEntity user, final String indexingKey, final LogRecordFormat recordFormat, final File file) {

        final var userIndexingKey = this.logKeysFactory.createUserIndexingKey(user.getHash(), indexingKey);
        this.indexedFilesCounter.increment();

        try (final var userContext = this.userAccessor.as(user);
             final var packageIterator = this.parser.parse(this.logKeysFactory.createIndexedLogFileKey(indexingKey, file.getName()), file, recordFormat)) {

            while (packageIterator.hasNext()) {
                final var recordsPackage = packageIterator.next();

                this.logRecordRepository.saveAll(recordsPackage);
                this.indexedRecordsCounter.increment(recordsPackage.size());
                this.elasticIndexRequestsCounter.increment();
            }

            // TODO обработка статистики без нагрузок на память
            final var analyzeQuery = new AnalyzeQueryOnIndexWrapper(indexingKey);
            analyze(analyzeQuery);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String createStatsRedisKey(final String key) {
        return STATISTICS_CACHE + ":" + key;
    }
}
