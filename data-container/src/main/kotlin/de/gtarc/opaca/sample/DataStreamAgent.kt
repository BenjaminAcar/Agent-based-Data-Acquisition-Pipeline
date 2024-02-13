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


import java.io.FileInputStream

class DataStreamAgent(name: String, private val filePath: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("GetInfo", mapOf(), "Map"),
            Action("Fail", mapOf(), "void"),
            Action("Deregister", mapOf(), "void")
        ).plus(extraActions),
        listOf(
            Stream("GetStream", Stream.Mode.GET)
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
                "Fail" -> actionFail()
                "Deregister" -> deregister(false)
                in extraActions.map { a -> a.name } -> "Called extra action ${it.name}"
                else -> null
            }
        }

        respond<StreamInvoke, Any?> {
            when (it.name) {
                "GetStream" -> actionGetStream()
                else -> null
            }
        }

    }

    private fun actionGetStream(): FileInputStream {        
        return FileInputStream("${filePath}")
    }
    

    private fun actionFail() {
        throw RuntimeException("Action Failed (as expected)")
    }

    private fun actionGetInfo() = mapOf(
        Pair("name", name),
        Pair("lastMessage", lastMessage),
        Pair("lastBroadcast", lastBroadcast),
        Pair(AgentContainerApi.ENV_CONTAINER_ID, System.getenv(AgentContainerApi.ENV_CONTAINER_ID)),
        Pair(AgentContainerApi.ENV_PLATFORM_URL, System.getenv(AgentContainerApi.ENV_PLATFORM_URL)),
        Pair(AgentContainerApi.ENV_TOKEN, System.getenv(AgentContainerApi.ENV_TOKEN))
    )
}
