plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

java { toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get()) } }

dependencies {
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.protobuf.java)
    compileOnly(libs.tomcat.annotations)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}" }
    plugins { create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}" } }
    generateProtoTasks { all().forEach { it.plugins { create("grpc") } } }
}
