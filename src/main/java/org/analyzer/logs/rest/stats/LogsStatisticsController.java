package org.analyzer.logs.rest.stats;

import lombok.extern.slf4j.Slf4j;
import org.analyzer.logs.model.LogsStatisticsEntity;
import org.analyzer.logs.service.LogsService;
import org.analyzer.logs.service.MapLogsStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/logs/statistics")
@Slf4j
public class LogsStatisticsController {

    @Autowired
    private LogsService service;

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> analyze(@RequestBody RequestAnalyzeQuery query) {
        return this.service.analyze(query)
                            .flatMap(MapLogsStatistics::toResultMap)
                            .onErrorResume(this::onError);
    }

    @GetMapping("/{statisticsKey}")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Map<String, Object>> find(@PathVariable("statisticsKey") String statisticsKey) {
        return this.service.findStatisticsByKey(statisticsKey)
                            .map(LogsStatisticsEntity::getStats)
                            .onErrorResume(this::onError);
    }

    private <T> Mono<T> onError(final Throwable ex) {
        log.error("", ex);
        return Mono.error(ex);
    }
}