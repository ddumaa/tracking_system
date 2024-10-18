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

# Добавление ключа Google
RUN wget -q -O - https://dl.google.com/linux/linux_signing_key.pub | apt-key add -

# Добавление репозитория Google Chrome
RUN echo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" >> /etc/apt/sources.list.d/google-chrome.list

# Установка Google Chrome
RUN apt-get update && apt-get install -y google-chrome-stable && apt-get clean

# Установка ChromeDriver (при этом версия должна совпадать с установленной версией Chrome)
RUN CHROMEDRIVER_VERSION=$(google-chrome --version | grep -oP '\d+\.\d+\.\d+' | head -n 1) && \
    echo "ChromeDriver version: $CHROMEDRIVER_VERSION" && \
    wget -N "https://chromedriver.storage.googleapis.com/${CHROMEDRIVER_VERSION}/chromedriver_linux64.zip" && \
    unzip chromedriver_linux64.zip -d /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm chromedriver_linux64.zip

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]