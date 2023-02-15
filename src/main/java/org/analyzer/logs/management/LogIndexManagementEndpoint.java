package org.analyzer.logs.management;

import org.analyzer.logs.service.management.LogsManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "logs")
public class LogIndexManagementEndpoint {

    @Autowired
    private LogsManagementService managementService;

    @ReadOperation
    public Map<String, Object> read(@Selector String operation) {
        return switch (operation) {
            case "exists" -> Map.of("index-exists", this.managementService.existsIndex());
            case "information" -> readInformation();
            default -> throw new UnsupportedOperationException(operation);
        };
    }

    @ReadOperation
    public Map<String, Object> readInformation() {
        return this.managementService.indexInfo();
    }

    @WriteOperation
    public boolean write(@Selector String operation) {
        return switch (operation) {
            case "create" -> this.managementService.createIndex();
            case "refresh" -> {
                this.managementService.refreshIndex();
                yield true;
            }
            default -> throw new UnsupportedOperationException(operation);
        };
    }

    @DeleteOperation
    public boolean delete(@Selector String operation) {
        if ("drop".equals(operation)) {
            return this.managementService.dropIndex();
        }

        throw new UnsupportedOperationException(operation);
    }
}
