package org.analyzer.logs.rest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.NonNull;

import java.util.List;

@JsonSerialize
@JsonAutoDetect
public record LogRecordsCollectionResource(@NonNull List<String> records) {
}