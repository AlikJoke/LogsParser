package org.parser.app.rest;

import lombok.extern.slf4j.Slf4j;
import org.parser.app.service.LogsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping("/api/logs")
@Slf4j
public class LogsController {

    @Autowired
    private LogsService service;

    @PostMapping("/index")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> load(
            @RequestPart("file") Flux<FilePart> file,
            @RequestPart(value = "recordPatterns", required = false) LogRecordFormatResource recordPattern) {

        final Flux<File> tempFiles = file.flatMap(this::createTempFile);

        return file
                .zipWith(tempFiles)
                .flatMap(tuple ->
                        this.service
                                .index(Mono.just(tuple.getT2()), tuple.getT1().filename(), recordPattern)
                                .doOnNext(v -> tuple.getT2().delete())
                )
                .then()
                .onErrorResume(this::onError);
    }

    @PostMapping(value = "/query")
    @ResponseStatus(HttpStatus.OK)
    public Mono<LogRecordsCollectionResource> read(@RequestBody RequestSearchQuery query) {
        return this.service.searchByQuery(query)
                            .collectList()
                            .map(LogRecordsCollectionResource::new)
                            .onErrorResume(this::onError);
    }

    private <T> Mono<T> onError(final Throwable ex) {
        log.error("", ex);
        return Mono.error(ex);
    }

    private Mono<File> createTempFile(final FilePart filePart) {
        try {
            final File result = Files.createTempFile(filePart.filename(), null).toFile();
            return filePart.transferTo(result).thenReturn(result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}