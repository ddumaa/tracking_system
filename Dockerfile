FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17.0.2-jdk-slim-buster

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
    automake

# Обновление CMake (если стандартная версия слишком старая)
RUN apt-get remove -y cmake && \
    wget https://github.com/Kitware/CMake/releases/download/v3.31.0/cmake-3.31.0-linux-x86_64.tar.gz && \
    tar -xvzf cmake-3.31.0-linux-x86_64.tar.gz --strip-components=1 -C /usr/local

# Скачивание исходного кода Tesseract 5.5.0
RUN wget https://github.com/tesseract-ocr/tesseract/archive/refs/tags/5.5.0.tar.gz -O /tmp/tesseract-5.5.0.tar.gz && \
    tar -xvzf /tmp/tesseract-5.5.0.tar.gz -C /tmp && \
    cd /tmp/tesseract-5.5.0 && \
    mkdir build && \
    cd build && \
    cmake .. && \
    make && \
    make install && \
    ldconfig

# Скачивание языковых данных
RUN mkdir -p /usr/local/share/tessdata/ && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/eng.traineddata -O /usr/local/share/tessdata/eng.traineddata && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/rus.traineddata -O /usr/local/share/tessdata/rus.traineddata

# Установить переменную окружения
ENV TESSDATA_PREFIX=/usr/local/share/tessdata/

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