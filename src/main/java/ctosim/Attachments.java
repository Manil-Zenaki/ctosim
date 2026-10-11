package ctosim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.xpath.XPathException;

/**
 * The CSV files attached to a form, read by {@code pulldata()}. See
 * specs/03-surveycto-functions.md, Amendment 3. Files are read on first use and kept.
 */
final class Attachments {

    /** No media directory: every attached file is missing. */
    static final Attachments NONE = new Attachments(null);

    private final Path directory;
    private final Map<String, Table> tables = new HashMap<>();

    Attachments(Path directory) {
        this.directory = directory;
    }

    /** The literal sources among {@code sources} whose CSV file is not in the media directory. */
    SortedSet<String> missing(Set<String> sources) {
        TreeSet<String> missing = new TreeSet<>();
        for (String source : sources) {
            if (directory == null || !Files.exists(directory.resolve(source + ".csv"))) {
                missing.add(source);
            }
        }
        return Collections.unmodifiableSortedSet(missing);
    }

    String pulldata(String source, String column, String keyColumn, String key) {
        Table table = table(source);
        return table == null ? "" : table.lookup(column, keyColumn, key);
    }

    /**
     * The file {@code <source>.csv}, read on first use, or null when it is missing (or there is
     * no media directory). A file that cannot be read throws {@link XPathException}.
     */
    Table table(String source) {
        if (directory == null) {
            return null;
        }
        Path file = directory.resolve(source + ".csv");
        synchronized (tables) {
            Table table = tables.get(source);
            if (table == null) {
                if (!Files.exists(file)) {
                    return null;
                }
                table = Table.read(file);
                tables.put(source, table);
            }
            return table;
        }
    }

    IFunctionHandler pulldataHandler() {
        return new IFunctionHandler() {
            @Override
            public String getName() {
                return "pulldata";
            }

            @Override
            public List<Class<?>[]> getPrototypes() {
                return List.<Class<?>[]>of(new Class<?>[] {String.class, String.class, String.class, String.class});
            }

            @Override
            public boolean rawArgs() {
                return false;
            }

            @Override
            public Object eval(Object[] args, EvaluationContext ec) {
                return pulldata((String) args[0], (String) args[1], (String) args[2], (String) args[3]);
            }
        };
    }

    /** One CSV file: its header and data rows. */
    record Table(List<String> header, List<List<String>> rows) {

        String lookup(String column, String keyColumn, String key) {
            int value = header.indexOf(column);
            int keyIndex = header.indexOf(keyColumn);
            if (value < 0 || keyIndex < 0) {
                return "";
            }
            for (List<String> row : rows) {
                if (key.equals(cell(row, keyIndex))) {
                    return cell(row, value);
                }
            }
            return "";
        }

        private static String cell(List<String> row, int i) {
            return i < row.size() ? row.get(i) : "";
        }

        static Table read(Path file) {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new XPathException("pulldata: the attached file " + file.getFileName() + " cannot be read (" + e + ")");
            }
            if (text.startsWith("﻿")) {
                text = text.substring(1);
            }
            List<List<String>> records = parse(text, file);
            List<String> header = records.isEmpty() ? List.of() : records.get(0);
            return new Table(header, records.isEmpty() ? List.of() : records.subList(1, records.size()));
        }

        private static List<List<String>> parse(String text, Path file) {
            List<List<String>> records = new ArrayList<>();
            List<String> record = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            boolean any = false; // the current record has content
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"') {
                        if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                            field.append('"');
                            i++;
                        } else {
                            quoted = false;
                        }
                    } else {
                        field.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                    any = true;
                } else if (c == ',') {
                    record.add(field.toString());
                    field.setLength(0);
                    any = true;
                } else if (c == '\n' || c == '\r') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    record.add(field.toString());
                    field.setLength(0);
                    records.add(record);
                    record = new ArrayList<>();
                    any = false;
                } else {
                    field.append(c);
                    any = true;
                }
            }
            if (quoted) {
                throw new XPathException("pulldata: the attached file " + file.getFileName() + " has an unclosed quote");
            }
            if (any || field.length() > 0) {
                record.add(field.toString());
                records.add(record);
            }
            return records;
        }
    }
}
