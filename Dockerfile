# 멀티스테이지. 빌드 도구를 실행 이미지에 남기지 않으려는 것이고,
# 무엇보다 빌드가 로컬 환경이 아니라 이 파일 하나로 재현된다.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 해석을 먼저 캐시한다. src 만 바뀐 커밋에서는 이 레이어가 그대로 재사용된다.
COPY gradlew ./
COPY gradle gradle
# gradle.properties 를 빼먹으면 이미지 빌드만 그 설정을 못 본다. 저장소에는 박혀 있는데
# 운영 빌드에서만 안 먹는 설정이 생기는 자리다 (인코딩·힙 크기 같은 것들).
COPY settings.gradle build.gradle gradle.properties ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
# 약관·개인정보처리방침. processResources 가 여기서 리소스로 복사한다 (v1.21.0).
# .dockerignore 가 docs 를 빼면서 이 두 장만 예외로 들여보낸다 — 둘 중 하나라도
# 어긋나면 빌드는 성공하고 운영에서만 /terms·/privacy 가 500 이 난다.
COPY docs docs
# 테스트는 이미지 빌드에서 돌리지 않는다. 91개를 배포마다 9분씩 다시 도는 값이
# 크고, 같은 커밋을 이미 로컬과 CI 에서 검증하기 때문이다.
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar

# 이 이미지는 배포용이다. 프로파일을 안 주고 띄우면 dev(H2 인메모리)로 떠서
# 데이터가 사라지는 사고가 나므로 기본값을 prod 로 못 박는다. 필요하면 덮어쓸 수 있다.
ENV SPRING_PROFILES_ACTIVE=prod

# Railway 는 PORT 를 주입한다. application.yaml 이 ${PORT:8080} 으로 받는다.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
