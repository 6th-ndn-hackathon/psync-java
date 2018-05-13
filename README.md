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

    https://github.com/kallerosenbaum/ibltj

Install the ibltj as:

    mvn package
    mvn install:install-file -Dfile=target/iblt-1.0-SNAPSHOT.jar -DgroupId=se.rosenbaum -DartifactId=iblt -Dversion=1.0-SNAPSHOT -Dpackaging=jar

### Bloom Filter

    Guava library

    <dependency>
      <groupId>com.google.guava</groupId>
      <artifactId>guava</artifactId>
      <version>22.0</version>
    </dependency>

## IDE

Use eclipse and export as Maven project (May need to install Maven plugin)
