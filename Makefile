all:
    make install
	make build

run:
	java -jar ./target/client-ng-java-1.0-SNAPSHOT-jar-with-dependencies.jar

clean:
	rm -rf wandb
	rm -rf target
	rm -rf out
	mvn clean

install:
	mvn install

build:
	mvn clean compile
	mvn package