# psync-java
Java Port of PSync

## Dependencies

### NDN:

    https://github.com/named-data/jndn

Modified MemoryContentCache.java to expose pendingInterestTable to application.
When we publish data in PSync we need to go over the pending interests and see
if their subscription list (which is a part of the pending interest) matches the produced
data before sending the data.

### IBF:

C++ port of:
    https://github.com/gavinandresen/IBLT_Cplusplus

### Bloom Filter

C++ port of:
    https://github.com/ArashPartow/bloom

## IDE

Use eclipse and export as Maven project (May need to install Maven plugin)
