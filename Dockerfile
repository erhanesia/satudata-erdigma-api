# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Tahap build
#
# `--platform=$BUILDPLATFORM` memaksa tahap ini berjalan di arsitektur RUNNER,
# bukan arsitektur target. Box tujuan arm64 (t4g) sedangkan runner GitHub
# amd64: tanpa baris ini buildx menjalankan seluruh Maven di bawah emulasi
# QEMU dan build yang semestinya beberapa menit berubah jadi puluhan menit.
# Aman dilakukan karena keluaran tahap ini hanya berkas .jar, yang tidak
# bergantung arsitektur sama sekali.
# ---------------------------------------------------------------------------
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# pom.xml disalin sendirian lebih dulu supaya lapisan unduhan dependensi tetap
# kena cache selama pom tidak berubah. src berubah tiap commit, pom hampir
# tidak pernah -- menyalin keduanya sekaligus membuang cache itu setiap kali.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------------------------------------------------------------------------
# Tahap runtime
#
# JANGAN ganti ke `eclipse-temurin:17-jre-alpine`: varian alpine Temurin hanya
# terbit untuk amd64 (sudah diperiksa di manifest-nya). Di box t4g yang arm64,
# container-nya mati dengan "exec format error" -- pesan yang tidak menyebut
# arsitektur sama sekali dan mudah disalahartikan sebagai jar yang rusak.
# Corretto varian alpine terbit untuk arm64.
# ---------------------------------------------------------------------------
FROM amazoncorretto:17-alpine

# curl dipakai healthcheck docker compose; image dasarnya tidak membawanya.
RUN apk add --no-cache curl \
 && addgroup -S app \
 && adduser -S -G app app

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# Container dibatasi memori lewat `mem_limit` di compose. MaxRAMPercentage
# membuat JVM membaca batas cgroup itu; tanpa baris ini heap default dihitung
# dari RAM host, JVM merasa punya jauh lebih banyak ruang daripada yang
# sebenarnya, dan yang datang adalah OOM kill dari kernel -- bukan galat
# kehabisan heap yang bisa dibaca di log aplikasi.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
