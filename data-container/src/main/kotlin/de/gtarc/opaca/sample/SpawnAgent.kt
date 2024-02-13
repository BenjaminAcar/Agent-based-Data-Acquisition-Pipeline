package de.gtarc.opaca.sample

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.container.Invoke
import de.gtarc.opaca.container.StreamInvoke
import de.gtarc.opaca.model.Action
import de.gtarc.opaca.model.Stream
import de.gtarc.opaca.model.AgentDescription
import de.gtarc.opaca.model.Message
import de.dailab.jiacvi.behaviour.act


import java.io.ByteArrayInputStream
import java.nio.charset.Charset

class SpawnAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("GetInfo", mapOf(), "Map"),
            Action("SpawnCaptureAndProcessAgent", mapOf(Pair("name", "String")), "void"),
            Action("SpawnFileManagerAgent", mapOf(Pair("name", "String")), "void"),
            Action("SpawnStreamAgent", mapOf(Pair("name", "String"), Pair("filePath", "String")), "void"),
            Action("Deregister", mapOf(), "void")
        ),
        listOf(
        )
    )

    override fun behaviour() = act {

        on<Message> {
            log.info("ON $it")
            lastMessage = it.payload
        }

        listen<Message>("topic") {
            log.info("LISTEN $it")
            lastBroadcast = it.payload
        }

        respond<Invoke, Any?> {
            log.info("RESPOND $it")
            when (it.name) {
                "GetInfo" -> actionGetInfo()
                "SpawnCaptureAndProcessAgent" -> spawnCaptureAndProcessAgent(it.parameters["name"]!!.asText())
                "SpawnFileManagerAgent" -> spawnFileManagerAgent(it.parameters["name"]!!.asText())
                "SpawnStreamAgent" -> spawnStreamAgent(it.parameters["name"]!!.asText(), it.parameters["filePath"]!!.asText())
                "Deregister" -> deregister(false)
                else -> Unit
            }
        }
    }


    private fun actionGetInfo() = mapOf(
        Pair("name", name),
        Pair("lastMessage", lastMessage),
        Pair("lastBroadcast", lastBroadcast),
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )

    private fun spawnCaptureAndProcessAgent(name: String) {
        system.spawnAgent(DataCaptureAndProcessAgent(name))
    }

    private fun spawnStreamAgent(name: String, filePath: String) {
        system.spawnAgent(DataStreamAgent(name, filePath))
    }

    private fun spawnFileManagerAgent(name: String) {
        system.spawnAgent(FileManagerAgent(name))
    }
}
