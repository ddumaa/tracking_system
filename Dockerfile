FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с Tesseract 5.5.0 из jitesoft/tesseract-ocr
FROM jitesoft/tesseract-ocr:5-latest AS tesseract

# Слой с Java (OpenJDK 17)
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
    libjpeg62-turbo-dev \
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

# Скопировать установленные файлы Tesseract из предыдущего слоя
COPY --from=tesseract /usr/local/bin/tesseract /usr/local/bin/tesseract
COPY --from=tesseract /usr/local/share/tessdata/ /usr/local/share/tessdata/
COPY --from=tesseract /usr/local/lib/ /usr/local/lib/



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

# Копируем приложение
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]