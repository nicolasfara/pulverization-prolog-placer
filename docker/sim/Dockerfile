FROM eclipse-temurin:22

RUN mkdir /experiment
WORKDIR /experiment

RUN apt-get update
RUN apt-get install -y software-properties-common
RUN add-apt-repository ppa:swi-prolog/stable
RUN apt update
RUN apt install -y swi-prolog-java

COPY effects effects
COPY gradle gradle
COPY src src
COPY docker docker
COPY *.kts ./
COPY *.properties ./

RUN sed -i '/alias(libs.plugins.gitSemVer)/d' build.gradle.kts
RUN sed -i '/alias(libs.plugins.multiJvmTesting)/d' build.gradle.kts
RUN sed -i '/multiJvm {/,/}/d' build.gradle.kts

COPY gradlew* ./

ENV LD_LIBRARY_PATH=/usr/lib/swi-prolog/lib/x86_64-linux
ENV SWI_HOME_DIR=/usr/lib/swi-prolog
ENV SWI_EXEC_FILE=/usr/lib/swi-prolog/bin/x86_64-linux/swipl
ENV SWIPL_BOOT_FILE=/usr/lib/swi-prolog/boot.prc

RUN CI=true ./gradlew runAllGraphic
RUN rm -rf data
RUN ./gradlew --stop
CMD ./gradlew runAllBatch
