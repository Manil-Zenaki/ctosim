# Spec 01 — FormLoader

## Purpose

Read a SurveyCTO form definition in XForm (XML) format and return a loaded
form from which independent form-entry sessions can be opened with the
JavaRosa engine. Make SurveyCTO-specific XPath functions evaluable, so a
form that uses them can be filled in without errors.

## Public API (package `ctosim`)

```java
public final class FormLoader {
    public static LoadedForm load(java.nio.file.Path xform) throws FormLoadException;
    public static LoadedForm load(java.io.InputStream in, String sourceName) throws FormLoadException;
}

public final class LoadedForm {
    public String sourceName();
    public String formId();
    public String version();                 // null when absent
    public String title();
    public java.util.SortedSet<String> stubbedFunctions();
    public org.javarosa.form.api.FormEntryController newEntryController();
}

public class FormLoadException extends Exception {
    public FormLoadException(String message);
    public FormLoadException(String message, Throwable cause);
}
```

## Behaviour

### Loading

- `load(Path)` reads the file at the given path. `sourceName()` is then the
  file name (last path element, e.g. `household.xml`).
- `load(InputStream, sourceName)` reads the stream to its end; the caller
  keeps ownership and closing it is the caller's job. `sourceName()` returns
  the given name.
- Input is UTF-8. A leading UTF-8 byte-order mark is accepted.
- Loading does not depend on the default locale, time zone or the current
  date.

### Metadata

- `formId()` is the `id` attribute of the root element of the primary
  instance (the first `<instance>` element of the model).
- `version()` is the `version` attribute of that same element, as a string,
  or `null` if it is absent.
- `title()` is the text of `<h:title>`, with leading and trailing white
  space removed. An absent title gives the empty string.

### Entry sessions

- `newEntryController()` returns a JavaRosa `FormEntryController`
  positioned at the beginning of the form (its current event is
  `FormEntryController.EVENT_BEGINNING_OF_FORM`), backed by a fresh,
  initialised instance: default values are set and `calculate` expressions
  have been evaluated.
- Each call returns a session over its own instance. Answers given in one
  session are never visible in another session from the same `LoadedForm`,
  including sessions opened before or after.
- A `LoadedForm` can open any number of sessions.

### SurveyCTO functions

SurveyCTO adds XPath functions that the ODK engine does not provide. In
every entry session, the following functions are evaluable with any number
of arguments of any type, and return a fixed value:

| Function | Returns |
|---|---|
| `pulldata(...)` | empty string `""` |
| `plug-in-metadata(...)` | empty string `""` |
| `username(...)` | string `"simulated"` |
| `duration(...)` | number `0` |
| `phone-call-log(...)` | empty string `""` |
| `phone-call-duration(...)` | number `0` |

Any other function that the engine does not implement natively is also
evaluable and returns the empty string `""`.

Functions the engine implements natively (for example `selected`,
`count-selected`, `if`, `concat`, `today`, `jr:choice-name`) keep their
standard ODK behaviour.

`stubbedFunctions()` lists, in natural string order, the names of the
functions **referenced by the form** that are handled by a stub (from the
table or the catch-all rule) rather than natively. It considers every
XPath expression in the form: `bind` attributes (`calculate`, `relevant`,
`constraint`, `required`, `readonly`), `setvalue` actions, and `nodeset`
or `ref` expressions in the body (including choice-filter predicates).
A form that references no such function gives an empty set. The returned
set cannot be modified.

### Errors

`load` throws `FormLoadException` — never another exception type — when:

- the path does not exist or cannot be read;
- the input is empty;
- the input is not well-formed XML;
- the input is XML but not an XForm (no `h:html` root with a `model`);
- the primary instance root has no `id` attribute;
- the engine rejects the form (for example an XPath expression that does
  not parse).

The message of the exception names the source (`sourceName`, or the path
for unreadable files) and says what is wrong in plain words. When the
failure comes from an underlying exception, it is kept as the cause.

Passing a `null` path, stream or source name throws `NullPointerException`.
