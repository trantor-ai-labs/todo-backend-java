# The toolchain is supplied, not borrowed.
#
# pom.xml declares maven.compiler.release=21. This image is chosen to satisfy that declaration and
# nothing else — it is not "a Java image we like". If the declaration changes, this changes with it.
#
# The alternative is what happened while this repository was being built: a host with JDK 26 and no
# Maven, compiling with whatever javac was on PATH. That works until the machine differs, and then
# the failure belongs to the laptop rather than to the code.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY src ./src
# No dependency resolution step, because there are no dependencies. That is the property that lets
# this build run with no network and no registry, years from now.
RUN mkdir -p /out && javac --release 21 -d /out src/main/java/xyz/trantor/todo/*.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /out ./classes
ENV PORT=8081
EXPOSE 8081
# A container has no idea what hostname it is reachable as, and the Todo-Backend spec requires every
# todo to carry a url that resolves for the client. BASE_URL is how the estate tells it.
CMD ["java", "-cp", "classes", "xyz.trantor.todo.TodoServer"]
