package org.parser.app.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "logs")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
public class LogRecord {

    @Id
    @EqualsAndHashCode.Include
    private String id;
    @Field(
            excludeFromSource = true,
            type = FieldType.Date,
            format = { DateFormat.date_hour_minute_second_millis, DateFormat.date_hour_minute_second }
    )
    private LocalDateTime timestamp;
    @Field(excludeFromSource = true)
    private String level;
    @Field(excludeFromSource = true)
    private String source;
    @Field(excludeFromSource = true)
    private String category;
    @Field(excludeFromSource = true)
    private String thread;
    @Field(store = true)
    private String record;
}
