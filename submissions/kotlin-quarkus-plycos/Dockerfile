FROM ubuntu:24.04
WORKDIR /opt

SHELL ["/bin/bash", "-c"]

COPY --chmod=0755 install.sh ./

RUN ./install.sh

COPY fresh_feed.db ./feed.db

COPY ./.mvn/ ./.mvn/
COPY --chmod=0755 ./mvnw ./
COPY pom.xml ./
COPY ./src/ ./src/

COPY --chmod=0755 build.sh ./
RUN ./build.sh

COPY --chmod=0755 start.sh ./
ENTRYPOINT [ "bash", "-c", "./start.sh" ]
