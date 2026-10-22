package com.example.vulnerable

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.xml.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement

@Serializable
data class SimpleData(@XmlElement val value: String)

fun main() {
    embeddedServer(Netty, port = 8880) {
        install(ContentNegotiation) {
            xml()
        }
        
        routing {
            post("/data") {
                println("Testing DOCTYPE XXE with call.receive<SimpleData>()...")
                
                try {
                    val data = call.receive<SimpleData>()
                    println("Data received: ${data.value}")
                    
                    call.respondText("""
                    Data processed: ${data.value}

                    ${if (data.value.contains("root:") || data.value.contains("java")) 
                    "XXE SUCCESS!" else "Normal data"}
                    """.trimIndent())
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
                }
            }
        }
    }.start(wait = true)
}