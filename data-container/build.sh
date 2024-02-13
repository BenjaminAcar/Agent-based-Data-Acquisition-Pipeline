rm target/* -rf
mvn install
docker build -t data-agent-container-image .