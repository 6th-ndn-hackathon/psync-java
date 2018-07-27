# psync-java
Java Port of PSync

## Dependencies

### NDN:

    https://github.com/named-data/jndn

Modified MemoryContentCache.java to expose pendingInterestTable to application.
When we publish data in PSync we need to go over the pending interests and see
if their subscription list (which is a part of the interest) matches the produced
data before sending the data.

### IBF:

Ported version of C++:
    https://github.com/gavinandresen/IBLT_Cplusplus

### Bloom Filter

    Guava library

    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>22.0</version>
    </dependency>

## IDE

Use eclipse and export as Maven project (May need to install Maven plugin)
