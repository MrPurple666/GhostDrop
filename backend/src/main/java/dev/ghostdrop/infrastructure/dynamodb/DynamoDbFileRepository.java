package dev.ghostdrop.infrastructure.dynamodb;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.TemporaryFile;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DynamoDbFileRepository implements FileRepository {
    private static final DateTimeFormatter EXPIRATION_BUCKET =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(ZoneOffset.UTC);
    private final DynamoDbClient client;
    private final String table;

    public DynamoDbFileRepository(DynamoDbClient client, String table) {
        this.client = client;
        this.table = table;
    }

    @Override
    public Optional<TemporaryFile> findById(String id) {
        return Optional.ofNullable(
                        client.getItem(
                                        request ->
                                                request.tableName(table)
                                                        .key(Map.of("id", value(id))))
                                .item())
                .filter(values -> !values.isEmpty())
                .map(this::file);
    }

    @Override
    public Optional<TemporaryFile> reserveDownload(String id, Instant now) {
        try {
            var result =
                    client.updateItem(
                            request ->
                                    request.tableName(table)
                                            .key(Map.of("id", value(id)))
                                            .updateExpression(
                                                    "SET downloadCount = downloadCount + :one")
                                            .conditionExpression(
                                                    "#status = :available AND"
                                                        + " attribute_exists(scannedAt) AND"
                                                        + " expiresAt > :now AND"
                                                        + " (attribute_not_exists(maxDownloads) OR"
                                                        + " downloadCount < maxDownloads)")
                                            .expressionAttributeNames(Map.of("#status", "status"))
                                            .expressionAttributeValues(
                                                    Map.of(
                                                            ":one",
                                                            number(1),
                                                            ":available",
                                                            value(FileStatus.AVAILABLE.name()),
                                                            ":now",
                                                            number(now.getEpochSecond())))
                                            .returnValues(ReturnValue.ALL_NEW));
            return Optional.of(file(result.attributes()));
        } catch (ConditionalCheckFailedException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void beginScan(String storageKey) {
        transition(
                storageKey,
                "SET #status = :target",
                "#status = :from",
                Map.of(
                        ":from",
                        value(FileStatus.PENDING_UPLOAD.name()),
                        ":target",
                        value(FileStatus.PENDING_SCAN.name())));
    }

    @Override
    public Optional<TemporaryFile> markAvailableAfterScan(String storageKey, Instant scannedAt) {
        return transition(
                storageKey,
                "SET #status = :target, scannedAt = :scannedAt",
                "#status IN (:fromOne, :fromTwo) AND expiresAt > :now",
                Map.of(
                        ":fromOne",
                        value(FileStatus.PENDING_UPLOAD.name()),
                        ":fromTwo",
                        value(FileStatus.PENDING_SCAN.name()),
                        ":target",
                        value(FileStatus.AVAILABLE.name()),
                        ":scannedAt",
                        number(scannedAt.getEpochSecond()),
                        ":now",
                        number(scannedAt.getEpochSecond())));
    }

    @Override
    public Optional<TemporaryFile> markInfected(String storageKey, Instant scannedAt) {
        return transition(
                storageKey,
                "SET #status = :target, scannedAt = :scannedAt",
                "#status IN (:fromOne, :fromTwo)",
                Map.of(
                        ":fromOne",
                        value(FileStatus.PENDING_UPLOAD.name()),
                        ":fromTwo",
                        value(FileStatus.PENDING_SCAN.name()),
                        ":target",
                        value(FileStatus.INFECTED.name()),
                        ":scannedAt",
                        number(scannedAt.getEpochSecond())));
    }

    @Override
    public Optional<TemporaryFile> markScanFailed(String storageKey, Instant scannedAt) {
        return transition(
                storageKey,
                "SET #status = :target, scannedAt = :scannedAt",
                "#status IN (:fromOne, :fromTwo)",
                Map.of(
                        ":fromOne",
                        value(FileStatus.PENDING_UPLOAD.name()),
                        ":fromTwo",
                        value(FileStatus.PENDING_SCAN.name()),
                        ":target",
                        value(FileStatus.SCAN_FAILED.name()),
                        ":scannedAt",
                        number(scannedAt.getEpochSecond())));
    }

    private Optional<TemporaryFile> transition(
            String storageKey,
            String updateExpression,
            String conditionExpression,
            Map<String, AttributeValue> values) {
        var items =
                client.query(
                                request ->
                                        request.tableName(table)
                                                .indexName("storage-key-index")
                                                .keyConditionExpression("storageKey = :key")
                                                .expressionAttributeValues(
                                                        Map.of(":key", value(storageKey))))
                        .items();
        if (items.isEmpty()) return Optional.empty();
        try {
            var result =
                    client.updateItem(
                            request ->
                                    request.tableName(table)
                                            .key(Map.of("id", value(text(items.getFirst(), "id"))))
                                            .updateExpression(updateExpression)
                                            .conditionExpression(conditionExpression)
                                            .expressionAttributeNames(Map.of("#status", "status"))
                                            .expressionAttributeValues(values)
                                            .returnValues(ReturnValue.ALL_NEW));
            return Optional.of(file(result.attributes()));
        } catch (ConditionalCheckFailedException exception) {
            return Optional.empty();
        }
    }

    @Override
    public java.util.List<TemporaryFile> findExpired(Instant now) {
        var files = new java.util.ArrayList<TemporaryFile>();
        for (var bucket :
                java.util.List.of(
                        EXPIRATION_BUCKET.format(now.minusSeconds(3600)),
                        EXPIRATION_BUCKET.format(now))) {
            files.addAll(
                    client
                            .query(
                                    request ->
                                            request.tableName(table)
                                                    .indexName("expiration-bucket-index")
                                                    .keyConditionExpression(
                                                            "expirationBucket = :bucket AND"
                                                                + " expiresAt <= :now")
                                                    .expressionAttributeValues(
                                                            Map.of(
                                                                    ":bucket",
                                                                    value(bucket),
                                                                    ":now",
                                                                    number(now.getEpochSecond()))))
                            .items()
                            .stream()
                            .map(this::file)
                            .toList());
        }
        return files;
    }

    @Override
    public void delete(String id) {
        client.deleteItem(request -> request.tableName(table).key(Map.of("id", value(id))));
    }

    @Override
    public void save(TemporaryFile file) {
        client.putItem(request -> request.tableName(table).item(values(file)));
    }

    private Map<String, AttributeValue> values(TemporaryFile file) {
        var values = new HashMap<String, AttributeValue>();
        values.put("id", value(file.id()));
        values.put("storageKey", value(file.storageKey()));
        values.put("originalFileName", value(file.originalFileName()));
        values.put("contentType", value(file.contentType()));
        values.put("fileSize", number(file.fileSize()));
        values.put("createdAt", number(file.createdAt().getEpochSecond()));
        values.put("expiresAt", number(file.expiresAt().getEpochSecond()));
        values.put("expirationBucket", value(EXPIRATION_BUCKET.format(file.expiresAt())));
        values.put("downloadCount", number(file.downloadCount()));
        values.put("deletionTokenHash", value(file.deletionTokenHash()));
        values.put("status", value(file.status().name()));
        if (file.maxDownloads() != null) values.put("maxDownloads", number(file.maxDownloads()));
        if (file.passwordHash() != null) values.put("passwordHash", value(file.passwordHash()));
        if (file.scannedAt() != null)
            values.put("scannedAt", number(file.scannedAt().getEpochSecond()));
        return values;
    }

    private TemporaryFile file(Map<String, AttributeValue> values) {
        return new TemporaryFile(
                text(values, "id"),
                text(values, "storageKey"),
                text(values, "originalFileName"),
                text(values, "contentType"),
                integer(values, "fileSize"),
                instant(values, "createdAt"),
                instant(values, "expiresAt"),
                (int) integer(values, "downloadCount"),
                values.containsKey("maxDownloads") ? (int) integer(values, "maxDownloads") : null,
                values.containsKey("passwordHash") ? text(values, "passwordHash") : null,
                text(values, "deletionTokenHash"),
                values.containsKey("scannedAt") ? instant(values, "scannedAt") : null,
                FileStatus.valueOf(text(values, "status")));
    }

    private static AttributeValue value(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static AttributeValue number(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    private static String text(Map<String, AttributeValue> values, String name) {
        return values.get(name).s();
    }

    private static long integer(Map<String, AttributeValue> values, String name) {
        return Long.parseLong(values.get(name).n());
    }

    private static Instant instant(Map<String, AttributeValue> values, String name) {
        return Instant.ofEpochSecond(integer(values, name));
    }
}
