FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с Tesseract 5.5.0 из jitesoft/tesseract-ocr
FROM jitesoft/tesseract-ocr:5-5.5.0 AS tesseract

# Установка необходимых инструментов и библиотек
FROM openjdk:17-slim-bullseye

# Установка необходимых инструментов и библиотек
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    git \
    build-essential \
    cmake \
    pkg-config \
    libleptonica-dev \
    libtiff-dev \
    libpng-dev \
    libjpeg-dev \
    zlib1g-dev \
    libicu-dev \
    libpango1.0-dev \
    libglib2.0-dev \
    libtool \
    autoconf \
    automake \
    gcc g++ \
    unzip


# Установите Google Chrome
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && apt-get install -y google-chrome-stable

# Установите ChromeDriver
RUN wget -N "https://storage.googleapis.com/chrome-for-testing-public/130.0.6723.58/linux64/chromedriver-linux64.zip" -O /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64

# Скачиваем и добавляем языковые файлы для Tesseract
RUN mkdir -p /usr/local/share/tessdata/ && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/eng.traineddata -O /usr/local/share/tessdata/eng.traineddata && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/rus.traineddata -O /usr/local/share/tessdata/rus.traineddata

# Устанавливаем переменную окружения для указания пути к языковым данным
ENV TESSDATA_PREFIX=/usr/local/share/tessdata/

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]