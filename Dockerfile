# The recipe Fly.io follows to package the site and its server into a container.
# It has two steps: compile the Java code with the full JDK, then copy just the
# result into a much smaller image that can only run Java, not build it.

# ---------- Step 1: compile ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
# Refuse to build while the pages still have made-up details (phone, email, license...).
# Search the .html files for "PLACEHOLDER:" to find them. The colon matters: the notes at the
# top of each page mention the word without one, and shouldn't block the build.
COPY *.html ./
RUN if grep -l "PLACEHOLDER:" *.html; then \
      echo "The pages above still have PLACEHOLDER details. Fill them in before deploying." >&2; exit 1; \
    fi
COPY server/src/ src/
RUN javac -d classes --release 21 $(find src -name '*.java')
# Squash the site's text with Brotli now, since Java can't do it while serving.
RUN apk add --no-cache brotli
COPY style.css favicon.svg robots.txt sitemap.xml ./
COPY js/ js/
# The visitor counter's token (set in fly.toml under [build.args]; empty means off). The
# pages are squashed with it already in them, so they match what the server sends.
ARG ANALYTICS_TOKEN=""
RUN java -cp classes eande.Server brotli-sources && brotli -q 11 --rm br/*

# ---------- Step 2: the image that actually runs ----------
# The JRE ("Java runtime") has no compiler, so there are fewer programs inside to exploit.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /srv

# Only the compiled server and the public site files go in. Notes like README.md
# and the checklists never reach the live server, and neither does the source code.
COPY --from=build /build/classes/ classes/
COPY --from=build /build/br/ br/
COPY *.html style.css favicon.svg logo.webp apple-touch-icon.png og-image.png robots.txt sitemap.xml ./
# The motion: GSAP and js/motion.js.
COPY js/ js/

# Run as a regular user rather than root, so a bug in the server can't take over the container.
RUN adduser -S -H web
USER web

# The same visitor counter token, for the running server.
ARG ANALYTICS_TOKEN=""
ENV ANALYTICS_TOKEN=$ANALYTICS_TOKEN

# Most hosts tell the server which port to use through $PORT. If none is given, use 8080.
ENV PORT=8080
EXPOSE 8080
# "exec" lets the host stop the server cleanly.
# The memory settings keep Java well inside Fly's smallest (256 MB) machine.
# DisableAttachMechanism stops other programs in the container from hooking into the running server.
CMD exec java -Xmx96m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=48m -XX:+ExitOnOutOfMemoryError \
    -XX:+DisableAttachMechanism \
    -cp classes eande.Server
