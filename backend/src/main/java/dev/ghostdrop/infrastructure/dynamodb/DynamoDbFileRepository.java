package dev.ghostdrop.infrastructure.dynamodb;

import dev.ghostdrop.domain.FileRepository;
import dev.ghostdrop.domain.FileStatus;
import dev.ghostdrop.domain.TemporaryFile;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

public final class DynamoDbFileRepository implements FileRepository {
    private static final DateTimeFormatter EXPIRATION_BUCKET = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH").withZone(ZoneOffset.UTC);
    private final DynamoDbClient client;
    private final String table;

    public DynamoDbFileRepository(DynamoDbClient client, String table) {
        this.client = client;
        this.table = table;
    }

    @Override
    public Optional<TemporaryFile> findById(String id) {
        return Optional.ofNullable(client.getItem(request -> request.tableName(table).key(Map.of("id", value(id)))).item()).filter(values -> !values.isEmpty()).map(this::file);
    }

    @Override
    public Optional<TemporaryFile> reserveDownload(String id, Instant now) {
        try {
            var result = client.updateItem(request -> request.tableName(table).key(Map.of("id", value(id)))
                    .updateExpression("SET downloadCount = downloadCount + :one")
                    .conditionExpression("#status = :available AND expiresAt > :now AND (attribute_not_exists(maxDownloads) OR downloadCount < maxDownloads)")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(":one", number(1), ":available", value(FileStatus.AVAILABLE.name()), ":now", number(now.getEpochSecond())))
                    .returnValues(ReturnValue.ALL_NEW));
            return Optional.of(file(result.attributes()));
        } catch (ConditionalCheckFailedException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void save(TemporaryFile file) {
        client.putItem(request -> request.tableName(table).item(values(file)));
    }

    private Map<String, AttributeValue> values(TemporaryFile file) {
        var values = new HashMap<String, AttributeValue>();
        values.put("id", value(file.id())); values.put("storageKey", value(file.storageKey())); values.put("originalFileName", value(file.originalFileName())); values.put("contentType", value(file.contentType())); values.put("fileSize", number(file.fileSize())); values.put("createdAt", number(file.createdAt().getEpochSecond())); values.put("expiresAt", number(file.expiresAt().getEpochSecond())); values.put("expirationBucket", value(EXPIRATION_BUCKET.format(file.expiresAt()))); values.put("downloadCount", number(file.downloadCount())); values.put("deletionTokenHash", value(file.deletionTokenHash())); values.put("status", value(file.status().name()));
        if (file.maxDownloads() != null) values.put("maxDownloads", number(file.maxDownloads()));
        if (file.passwordHash() != null) values.put("passwordHash", value(file.passwordHash()));
        return values;
    }

    private TemporaryFile file(Map<String, AttributeValue> values) {
        return new TemporaryFile(text(values, "id"), text(values, "storageKey"), text(values, "originalFileName"), text(values, "contentType"), integer(values, "fileSize"), instant(values, "createdAt"), instant(values, "expiresAt"), (int) integer(values, "downloadCount"), values.containsKey("maxDownloads") ? (int) integer(values, "maxDownloads") : null, values.containsKey("passwordHash") ? text(values, "passwordHash") : null, text(values, "deletionTokenHash"), FileStatus.valueOf(text(values, "status")));
    }

    private static AttributeValue value(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue number(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }
    private static String text(Map<String, AttributeValue> values, String name) { return values.get(name).s(); }
    private static long integer(Map<String, AttributeValue> values, String name) { return Long.parseLong(values.get(name).n()); }
    private static Instant instant(Map<String, AttributeValue> values, String name) { return Instant.ofEpochSecond(integer(values, name)); }
}
