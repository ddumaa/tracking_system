# Слой с Maven и сборкой проекта
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с Ubuntu
FROM ddumaa/tesseract:5.5

# Устанавливаем Java и другие зависимости
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    software-properties-common \
    apt-transport-https \
    openjdk-17-jdk \
    unzip \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/*

# Добавляем ключ Google Chrome напрямую в доверенные ключи
RUN wget -q -O /usr/share/keyrings/google-chrome-archive-keyring.gpg https://dl.google.com/linux/linux_signing_key.pub && \
    echo "deb [arch=amd64 signed-by=/usr/share/keyrings/google-chrome-archive-keyring.gpg] http://dl.google.com/linux/chrome/deb/ stable main" > /etc/apt/sources.list.d/google-chrome.list

# Устанавливаем Google Chrome
RUN apt-get update && apt-get install -y google-chrome-stable && \
    rm -rf /var/lib/apt/lists/*

# Устанавливаем ChromeDriver
ENV CHROMEDRIVER_VERSION=131.0.6778.204
RUN wget -N "https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip" -O /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64

# Копируем собранное приложение из предыдущего шага
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]