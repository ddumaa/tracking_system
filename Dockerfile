FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17.0.2-jdk-slim-buster

# Установите необходимые пакеты
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    gnupg \
    apt-transport-https

# Добавление репозитория для Tesseract 5.x
RUN apt-get update && apt-get install -y \
    wget gnupg apt-transport-https && \
    echo "deb http://deb.debian.org/debian bullseye-backports main" > /etc/apt/sources.list.d/backports.list && \
    apt-get update && apt-get install -y \
    tesseract-ocr/tesseract-ocr5 \
    tesseract-ocr-eng \
    tesseract-ocr-rus \
    libleptonica-dev && \
    rm -rf /var/lib/apt/lists/*

# Добавьте репозиторий Google Chrome
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && apt-get install -y google-chrome-stable

# Установите ChromeDriver версии 130.0.6723.58
RUN wget -N "https://storage.googleapis.com/chrome-for-testing-public/130.0.6723.58/linux64/chromedriver-linux64.zip" -O /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]