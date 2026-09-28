# Performance notes

Decisions from performance investigations, so they are not repeated.

## Generated codecs per type (September 2026): not planned

A hand-written `Person` codec, the best case a runtime or build-time code generator could produce, was compared with the codec in JMH (3 forks, 100 to 3,000 rows). It decoded 1.20–1.28× faster with about 4% less allocation, and encoded no faster (0.93–1.00×). Most remaining time is converting text values, which generated code cannot avoid, and a generator would need its own reimplementation of Jackson's property semantics and a new dependency. The gain does not justify that.

## Scalar readers for creator arguments and direct writers for other leaf types (September 2026): not planned

Reading `String`/`int`/`long`/`double`/`boolean` creator arguments straight from the parser, and writing enums, `BigDecimal`, dates and UUIDs straight through their serializers, measured within noise against `main`. Creator arguments are boxed into the constructor's argument array either way, and those leaf types are already objects, so there was no boxing or per-value dispatch left to remove. The setter and scalar-property specializations that were kept gained because they avoid boxing.
