# syntax=docker/dockerfile:1

FROM gradle:9.6.0-jdk21-ubi10 AS builder

WORKDIR /build

RUN chown gradle:gradle /build

COPY --chown=gradle:gradle . ./

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle :broker:installDist -x test --no-daemon

FROM amazoncorretto:21-al2

WORKDIR /app

COPY --from=builder /build/broker/build/install/broker/lib/ /app/lib/

EXPOSE 8080

ENTRYPOINT ["java", "-cp", "/app/lib/*", "org.example.fleet.broker.MainApp"]