FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY lifemetrics.jar app.jar
EXPOSE 8080
# 프론트(static)는 이미지에 넣지 않고 /app/static 으로 볼륨 마운트한다.
# → 프론트만 바꿀 때는 rsync 만으로 반영되고, 이미지 재빌드/재시작이 필요 없다.
# JAVA_OPTS 로 힙 등을 조절한다(개발 서버는 메모리를 제한해서 띄움).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --spring.web.resources.static-locations=file:/app/static/,classpath:/static/"]
