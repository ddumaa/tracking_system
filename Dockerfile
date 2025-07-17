# Слой с Maven и сборкой проекта
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с рантаймом Java
FROM openjdk:17-jdk-slim

# Установка зависимостей
RUN apt-get update && \
    apt-get install -y \
      wget \
      curl \
      unzip \
      gnupg \
      apt-transport-https \
      software-properties-common \
      fonts-liberation \
      libatk-bridge2.0-0 \
      libatk1.0-0 \
      libcups2 \
      libdrm2 \
      libgbm1 \
      libnspr4 \
      libnss3 \
      libxcomposite1 \
      libxdamage1 \
      libxrandr2 \
      xdg-utils \
      libasound2 \
      libxshmfence1 \
      libappindicator3-1 && \
    curl -sSL https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/138.0.7204.157/linux64/chrome-linux64.zip -o /tmp/chrome.zip && \
    unzip /tmp/chrome.zip -d /opt/chrome && \
    ln -s /opt/chrome/chrome-linux64/chrome /usr/bin/google-chrome && \
    curl -sSL https://edgedl.me.gvt1.com/edgedl/chrome/chrome-for-testing/138.0.7204.157/linux64/chromedriver-linux64.zip -o /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /tmp/*.zip /tmp/chromedriver-linux64 && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Копируем собранное приложение из предыдущего шага
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]
