# Слой с Maven и сборкой проекта
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Слой с Ubuntu и Tesseract
FROM ddumaa/tesseract:5.5

# Устанавливаем Java и другие зависимости
RUN apt-get update && apt-get install -y \
    wget \
    gnupg \
    software-properties-common \
    apt-transport-https \
    openjdk-17-jdk \
    unzip \
    libopencv-dev \
    python3-opencv \
    && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/


RUN apt-get install -y wget
RUN wget -q https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb
RUN apt-get install -y ./google-chrome-stable_current_amd64.deb

# Устанавливаем ChromeDriver
ENV CHROMEDRIVER_VERSION=131.0.6778.204
RUN wget -N "https://storage.googleapis.com/chrome-for-testing-public/${CHROMEDRIVER_VERSION}/linux64/chromedriver-linux64.zip" -O /tmp/chromedriver.zip && \
    unzip /tmp/chromedriver.zip -d /tmp && \
    mv /tmp/chromedriver-linux64/chromedriver /usr/local/bin/ && \
    chmod +x /usr/local/bin/chromedriver && \
    rm -rf /tmp/chromedriver.zip /tmp/chromedriver-linux64

ENV LD_LIBRARY_PATH=/usr/lib/jni:$LD_LIBRARY_PATH
ENV JAVA_OPTS="-Djava.library.path=/usr/lib/jni"
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8

# Копируем собранное приложение из предыдущего шага
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]