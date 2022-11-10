files = '''
async-http-client-2.12.3.jar
async-http-client-netty-utils-2.12.3.jar
auto-common-1.2.1.jar
auto-service-1.0.1.jar
auto-service-annotations-1.0.1.jar
byte-buddy-1.12.10.jar
checker-qual-3.12.0.jar
commons-exec-1.3.jar
error_prone_annotations-2.11.0.jar
failsafe-3.2.4.jar
failureaccess-1.0.1.jar
guava-31.1-jre.jar
j2objc-annotations-1.3.jar
jakarta.activation-1.2.2.jar
jcommander-1.82.jar
jsr305-3.0.2.jar
jtoml-2.0.0.jar
listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar
netty-buffer-4.1.78.Final.jar
netty-codec-4.1.78.Final.jar
netty-codec-http-4.1.78.Final.jar
netty-codec-socks-4.1.78.Final.jar
netty-common-4.1.78.Final.jar
netty-handler-4.1.78.Final.jar
netty-handler-proxy-4.1.78.Final.jar
netty-reactive-streams-2.0.4.jar
netty-resolver-4.1.78.Final.jar
netty-transport-4.1.78.Final.jar
netty-transport-classes-epoll-4.1.78.Final.jar
netty-transport-classes-kqueue-4.1.78.Final.jar
netty-transport-native-epoll-4.1.78.Final.jar
netty-transport-native-kqueue-4.1.78.Final.jar
netty-transport-native-unix-common-4.1.78.Final.jar
opentelemetry-api-1.15.0.jar
opentelemetry-context-1.15.0.jar
opentelemetry-exporter-logging-1.15.0.jar
opentelemetry-sdk-1.15.0.jar
'''

f = files.split()

for file in f:
    print(f'<classpathentry kind="lib" path="lib/selenium/lib/{file}"/>')
