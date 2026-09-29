# 心语 (Xinyu) · AI 情绪日记与匿名树洞
# Copyright (c) 2026 CCnotcc016 (2947208937@qq.com). All rights reserved.
# 仅供交流学习使用，未经授权禁止商业售卖；详见项目根目录 LICENSE。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
