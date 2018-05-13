# psync-java
Java Port of PSync

## Dependencies

    https://github.com/named-data/jndn
    https://github.com/kallerosenbaum/ibltj

Install the ibltj as:

    mvn package
    mvn install:install-file -Dfile=target/iblt-1.0-SNAPSHOT.jar -DgroupId=se.rosenbaum -DartifactId=iblt -Dversion=1.0-SNAPSHOT -Dpackaging=jar

## IDE

Use eclipse and export as Maven project
