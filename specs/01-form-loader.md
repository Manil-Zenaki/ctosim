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
| `pulldata(...)` | empty string `""` (superseded: see spec 03, Amendment 3) |
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

## Clarifications (answers to `01-form-loader.questions.md`)

All fourteen assumptions recorded by the test author are confirmed as the
intended behaviour. In particular:

- `today()` and `now()` keep their native, clock-dependent behaviour in
  this module. Freezing the clock for reproducible simulations is a
  concern of the form walker (spec 04), not of the loader.
- A loaded form keeps its own copy of the definition: opening a session
  never re-reads the original file or stream.
- A function reference is any syntactic XPath function call at the listed
  expression sites, including calls in branches that are never taken;
  text inside string literals, labels, comments and instance data is not
  a reference.

## Amendment: engine limit on body `ref` predicates

Assumption 10 of the questions file said a body predicate that calls a
stubbed function "must be loadable even if JavaRosa checks it early". That
confirmation was wrong and is withdrawn for one case.

While parsing, JavaRosa checks each body control's `ref` against the
instance using a new, empty evaluation context that it creates itself
(`FormInstanceParser.verifyControlBindings`). No custom function can be
registered in that context, so the engine rejects a form in which a body
control's `ref` has a predicate that calls a stubbed function. ODK Collect
rejects such a form for the same reason. Therefore:

- A form in which a **body control's `ref`** calls a stubbed function in a
  predicate is rejected by the engine: `load` throws `FormLoadException`
  (the "engine rejects the form" case).
- This does not affect any other expression site. In particular, stubbed
  functions in `bind` attributes, `setvalue` values, itemset `nodeset`
  (choice filters) and itemset label/value `ref` are evaluable and listed
  by `stubbedFunctions()`.

## Amendment 2: metadata preloads

Real SurveyCTO forms contain metadata fields that the engine fills when an
instance is created: XForm binds with `jr:preload="property"` (parameters
`deviceid`, `subscriberid`, `simserial`, `phonenumber`, `username`),
`jr:preload="timestamp"` (parameters `start`, `end`) and `jr:preload="uid"`.

- A form containing any of these binds loads, and `newEntryController()`
  returns an initialised session (no exception), exactly as for any other
  form. This must hold without the caller configuring anything in the
  engine beforehand.
- In a new session, a field preloaded with property `username` has the
  string value `"simulated"` (the same value as the `username()` stub).
  Fields preloaded with properties `deviceid`, `subscriberid`, `simserial`
  or `phonenumber` have no value (the instance node's value is `null`).
- Timestamp and uid preloads keep the engine's native behaviour (current
  time; random identifier). Their values are not deterministic in this
  module.

Clarification: assumptions 15–18 of the questions file are confirmed, each
checked against JavaRosa 6.0.0 behaviour (`end` is filled only when the
instance is post-processed; timestamps are compared to the clock with a
one-second tolerance; a uid is only required to be a non-blank string
generated per session; unlisted preload situations are unspecified).
