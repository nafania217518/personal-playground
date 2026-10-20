rootProject.name = "quote-microservices"
include("proto", "gateway", "inventory-service", "pricing-service")

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
