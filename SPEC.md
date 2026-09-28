# JSVRO Wire Format v1

JSVRO is a row-oriented JSON sequence with a schema value followed by positional row values.

The design goals are:

- remain readable in a terminal and with `curl`
- eliminate repeated property names from homogeneous object streams
- support incremental encoding and decoding
- preserve JSON scalar values and `null`
- keep the schema language-neutral

## Stream structure

A JSVRO stream is a sequence of independent JSON values separated by JSON whitespace. Writers SHOULD use a single LF (`\n`) after every value.

The first value MUST be the schema object:

```json
{"jsvro":"1","columns":[...]}
```

Every following value is one row and MUST be a JSON array whose positions correspond to the schema's `columns` array.

Example:

```json
{"jsvro":"1","columns":[{"name":"name","type":"string"},{"name":"age","type":"integer"}]}
["Alice",30]
["Bob",40]
```

An empty result contains only the schema value.

## Schema

The root schema contains:

- `jsvro` — format version, currently the string `"1"`
- `columns` — ordered column definitions for each row

A column contains:

- `name` — property name
- `type` — JSVRO type name
- `columns` — present for `object`; ordered nested object columns
- `items` — present for `array`; schema for each array item

Array item schemas omit `name`, because the array position itself is unnamed.

## Types

JSVRO v1 defines:

- `string`
- `integer`
- `number`
- `decimal`
- `boolean`
- `date`
- `datetime`
- `uuid`
- `binary`
- `object`
- `array`
- `map`

The schema does not expose Java class names.

## Nested objects

Nested statically-shaped objects are positional recursively.

```json
{"jsvro":"1","columns":[{"name":"name","type":"string"},{"name":"address","type":"object","columns":[{"name":"city","type":"string"},{"name":"country","type":"string"}]}]}
["Alice",["Oslo","NO"]]
```

A nested object value MAY be `null`.

## Arrays

Arrays preserve ordinary JSON array structure. Their item representation follows the `items` schema.

```json
{"name":"addresses","type":"array","items":{"type":"object","columns":[{"name":"city","type":"string"},{"name":"country","type":"string"}]}}
```

A value for that column can be:

```json
[["Oslo","NO"],["Bergen","NO"]]
```

## Maps

Maps retain ordinary JSON object form because map keys are dynamic data, not statically-known property names.

```json
{"name":"attributes","type":"map"}
```

with a row value such as:

```json
{"desk":"FX","region":"NO"}
```

## Nulls

`null` is represented as the ordinary JSON literal `null`. JSVRO v1 intentionally does not define null bitmaps.

## Object property order

The schema defines the wire order. A decoder MUST interpret row positions using that schema order rather than source-language declaration order.

The Java reference implementation derives this order from Jackson's serialization property model and caches the resulting codec.

## Versioning

A v1 reader MUST reject a schema whose `jsvro` value it does not support.

## Schema resolution

The writer's schema travels with every stream, so a reader can decode streams written with a different schema than its own:

- Column names MUST be unique within one `columns` array.
- A reader MUST match the writer's columns to its own by name, not position, at every level of nested objects and array items.
- Columns the reader does not know, and columns the reader expects but the stream lacks, MUST be handled the way the reader's JSON binding handles unknown and absent object properties.
- A column's kind is object, array, map or scalar; every other type is a scalar. When a column the reader knows has a different kind in the stream, the reader MUST reject the stream before reading any row. Differences between scalar types are left to the reader's usual JSON coercion rules.
- Rows MUST still match the writer's schema.

A stream decodes to the same result as the equivalent JSON objects would. The Java reference decoder resolves by name once per incoming schema and then decodes positionally.

## Media type

The Spring integration currently uses the vendor media type:

```text
application/vnd.jsvro
```

This is a project media type, not an IANA-registered media type.

## Non-goals in v1

JSVRO v1 does not define:

- dictionary/string-ID encoding
- enum ordinals
- null bitmaps
- delta encoding
- columnar batches
- binary packing
- polymorphic type tags
- cyclic schemas

Those optimizations either reduce terminal readability or require substantially more schema semantics.
