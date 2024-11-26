FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с Java (OpenJDK 17)
FROM openjdk:17-slim-bullseye

# Устанавливаем необходимые зависимости
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    apt-transport-https \
    && echo "deb http://ftp.debian.org/debian bullseye-backports main" > /etc/apt/sources.list.d/backports.list \
    && apt-get update

# Устанавливаем Tesseract из backports
RUN apt-get -t bullseye-backports install -y \
    tesseract-ocr \
    tesseract-ocr-eng \
    tesseract-ocr-rus \
    libleptonica-dev \
    libtesseract-dev \
    libpng-dev \
    libjpeg-dev \
    && rm -rf /var/lib/apt/lists/*

# Скачиваем и добавляем языковые файлы для Tesseract
RUN mkdir -p /usr/local/share/tessdata/ && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/eng.traineddata -O /usr/local/share/tessdata/eng.traineddata && \
    wget https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/rus.traineddata -O /usr/local/share/tessdata/rus.traineddata

# Указываем путь к языковым файлам в переменной окружения
ENV TESSDATA_PREFIX=/usr/local/share/

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

# Копируем приложение
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]