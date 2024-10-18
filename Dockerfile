FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17.0.2-jdk-slim-buster

# Установка необходимых пакетов
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    curl \
    gnupg2 \
    libnss3 \
    libgconf-2-4 \
    libxss1 \
    libxi6 \
    libgdk-pixbuf2.0-0 \
    fonts-liberation \
    libappindicator3-1 \
    libxrandr2 \
    libx11-xcb1 \
    libasound2 \
    libatk1.0-0 \
    libxcomposite1 \
    libgtk-3-0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Задайте версию ChromeDriver
ARG CHROMEDRIVER_VERSION=126.0.6478.26

# Установка ChromeDriver
RUN wget -N https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip \
    && unzip chromedriver-linux64.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chromedriver \
    && rm chromedriver-linux64.zip

# Установка Google Chrome
RUN wget -N https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chrome-linux64.zip \
    && unzip chrome-linux64.zip -d /usr/local/bin/ \
    && chmod +x /usr/local/bin/chrome \
    && rm chrome-linux64.zip

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]