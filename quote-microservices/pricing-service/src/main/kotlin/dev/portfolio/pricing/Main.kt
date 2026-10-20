package dev.portfolio.pricing

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.ServerInterceptors
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dev.portfolio.pricing.Main")

class Handle(val httpPort: Int, val grpcPort: Int, private val http: EmbeddedServer<*, *>, private val grpc: Server) {
    /** Blocks the caller until stop() is called. Keeps main() alive. */
    fun await() = grpc.awaitTermination()

    fun stop() {
        http.stop(0, 1000)
        grpc.shutdownNow().awaitTermination()
    }
}

fun start(httpPort: Int, grpcPort: Int, book: PriceBook = PriceBook()): Handle {
    val grpc = ServerBuilder.forPort(grpcPort)
        .addService(ServerInterceptors.intercept(PricingService(book), CorrelationInterceptor()))
        .build().start()
    val http = embeddedServer(Netty, port = httpPort) { module(book) }.start(wait = false)
    val boundHttp = runBlocking { http.engine.resolvedConnectors().first().port }
    log.info("pricing-service started http={} grpc={}", boundHttp, grpc.port)
    return Handle(boundHttp, grpc.port, http, grpc)
}

fun main() {
    val handle = start(
        httpPort = System.getenv("HTTP_PORT")?.toInt() ?: 8082,
        grpcPort = System.getenv("GRPC_PORT")?.toInt() ?: 9092,
    )
    Runtime.getRuntime().addShutdownHook(Thread { handle.stop() })
    handle.await()
}
