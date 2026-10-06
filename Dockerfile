# ============================================================================
#  Dockerfile - runs the real Java web application in a container
#  ---------------------------------------------------------------------------
#  Used by Cloud Run (Google) or by any other container host, so the complete
#  application with the login system and the live notification stream can be
#  published on the internet.
#
#  Build :  docker build -t tollbooth-web .
#  Run   :  docker run -p 8080:8080 tollbooth-web
#           then open http://localhost:8080
#
#  Cloud Run with Firebase Hosting :  see docs/DEPLOY_PUBLIC.md (option A)
# ============================================================================

# ---------------------------------------------------------------------------
#  STAGE 1 : compile the Java sources (this stage is thrown away afterwards)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /src

# The whole project is copied, then the useless folders are removed again.
# (.dockerignore already keeps big things such as .git and out/ outside.)
COPY . /src
RUN rm -rf /src/out /src/data /src/.git

# Compile every class and copy the web page next to the class files, exactly
# like the run-web.sh script does on a normal computer.
RUN mkdir -p /src/out/static \
 && javac -d /src/out $(find tollbooth -name "*.java") \
 && cp -f resources/static/* /src/out/static/ \
 && echo "compiled classes :" && find /src/out -name "*.class" | wc -l

# ---------------------------------------------------------------------------
#  STAGE 2 : the small runtime image (JDK runtime only, no compiler)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

# The application writes transactions.txt and data/users.json in the working
# folder. /tmp is the folder that is always writable, also on Cloud Run, so the
# server runs from there.
WORKDIR /tmp

COPY --from=build /src/out /app/out

# Cloud Run sends the port in the PORT variable (8080 by default).
ENV PORT=8080
EXPOSE 8080

# --quiet      : no per-request log line (Cloud Run logs every line, so this
#                keeps the log readable)
# --port       : the port given by the host
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 -cp /app/out tollbooth.web.WebLauncher --port \"${PORT}\" --quiet"]
