package ctosim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The field catalog written by the XLSForm converter. See specs/08-surveycto-exporter.md. */
public final class FieldCatalog {

    private final String formId;
    private final String title;
    private final String version;
    private final List<FieldInfo> fields;

    private FieldCatalog(String formId, String title, String version, List<FieldInfo> fields) {
        this.formId = formId;
        this.title = title;
        this.version = version;
        this.fields = List.copyOf(fields);
    }

    public static FieldCatalog load(Path json) throws IOException {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(Files.readString(json));
        } catch (JsonProcessingException e) {
            throw invalid(json, "not valid JSON: " + e.getOriginalMessage());
        } catch (IOException e) {
            throw new IOException("cannot read field catalog " + json + ": " + e, e);
        }
        if (root == null || !root.isObject()) {
            throw invalid(json, "not a JSON object");
        }
        JsonNode formId = root.get("form_id");
        if (formId == null || formId.isNull()) {
            throw invalid(json, "no form_id");
        }
        JsonNode fieldsNode = root.get("fields");
        if (fieldsNode == null || !fieldsNode.isArray()) {
            throw invalid(json, "no fields array");
        }
        List<FieldInfo> fields = new ArrayList<>();
        for (int i = 0; i < fieldsNode.size(); i++) {
            JsonNode field = fieldsNode.get(i);
            if (!field.isObject()) {
                throw invalid(json, "field " + (i + 1) + " is not an object");
            }
            for (String key : List.of("name", "type", "xpath", "row")) {
                if (field.get(key) == null || field.get(key).isNull()) {
                    throw invalid(json, "field " + (i + 1) + " has no " + key);
                }
            }
            fields.add(new FieldInfo(field.get("name").asText(), field.get("type").asText(),
                    field.get("xpath").asText(), field.get("row").asInt()));
        }
        String id = formId.asText();
        JsonNode titleNode = root.get("title");
        String title = titleNode == null || titleNode.isNull() || titleNode.asText().isEmpty()
                ? id : titleNode.asText();
        JsonNode versionNode = root.get("version");
        String version = versionNode == null || versionNode.isNull() ? null : versionNode.asText();
        return new FieldCatalog(id, title, version, fields);
    }

    private static IOException invalid(Path json, String reason) {
        return new IOException("invalid field catalog " + json + ": " + reason);
    }

    public String formId() {
        return formId;
    }

    public String title() {
        return title;
    }

    /** The form version, or {@code null} when absent. */
    public String version() {
        return version;
    }

    /** The fields in catalog order. */
    public List<FieldInfo> fields() {
        return fields;
    }
}
