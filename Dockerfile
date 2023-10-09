FROM alpine:3.14

ADD target/bundle /persephone
ADD .java_modules /persephone/.java_modules

# Replace Alpine's Java runtime via jlink
RUN apk update \
    && apk upgrade \
    && apk add --no-cache openjdk11 \
    && mkdir -p /persephone/runtimes \
    && jlink --output /persephone/runtimes/linux/ --add-modules $(cat /persephone/.java_modules) \
    && apk del openjdk11 \
    && rm -rf /var/cache/apk/*

WORKDIR /persephone
EXPOSE 8080

# This is mainly a placeholder; the intended use is to pass in args
# when running the Docker image.
CMD ["/persephone/bin/persephone.sh", "--help"]
