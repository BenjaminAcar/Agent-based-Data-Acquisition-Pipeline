package de.gtarc.opaca.sample

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.container.Invoke
import de.gtarc.opaca.model.Action
import de.gtarc.opaca.model.Stream
import de.gtarc.opaca.model.AgentDescription
import de.gtarc.opaca.model.Message
import de.dailab.jiacvi.behaviour.act

import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.OutputStreamWriter
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataOrchestratorAgent(name: String): AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()


    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action("DownloadResults", mapOf(Pair("containerId", "String"),Pair("agentId", "String"), Pair("fileName", "String")), "String"),
            Action("TriggerAcquisition", mapOf(
                Pair("action", "String"),
                Pair("agentId", "String"), 
                Pair("rtspUrl" , "String"),
                Pair("processingUrl" , "String"),
                Pair("streamSeconds" , "Int"),
                Pair("frameRate" , "Int"),
                Pair("startTime", "String")
                ), 
                "String"),
            Action("GetInfo", mapOf(), "Map"),
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
                "DownloadResults" -> actionDownloadResults(it.parameters["containerId"]!!.asText(), it.parameters["agentId"]!!.asText(), it.parameters["fileName"]!!.asText())
                "TriggerAcquisition" -> actionTriggerAcquisition(
                    it.parameters["action"]!!.asText(), 
                    it.parameters["agentId"]!!.asText(), 
                    it.parameters["rtspUrl"]?.asText() ?: "", 
                    it.parameters["processingUrl"]?.asText() ?: "", 
                    it.parameters["streamSeconds"]?.asInt() ?: 10, 
                    it.parameters["frameRate"]?.asInt() ?: 2,
                    it.parameters["startTime"]?.asText() ?: LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                "GetInfo" -> actionGetInfo()
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

    private fun sanitizeFileName(input: String): String {
        val ipAndPortPattern = "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5})".toRegex()
        return ipAndPortPattern.find(input)?.value?.replace(Regex("[:.]"), "_") ?: ""
    }

    private fun actionDownloadResults(containerId: String, agentId: String, fileName: String): String {
        System.out.println("Start Downloading")
        val responseEntity: ResponseEntity<StreamingResponseBody> = sendOutboundStreamRequest("GetStream", agentId, containerId, true)

        val file = File(fileName)
        FileOutputStream(file).use { outputStream ->
            responseEntity.body?.apply {
                outputStream.use { os -> 
                    this.writeTo(os)
                }
            }
        }

        return "Stream is transferred"
    }

    private fun actionTriggerAcquisition(action: String, agentId: String, rtspUrl: String, processingUrl: String, streamSeconds: Int, frameRate: Int, startTimeStr: String): String {
        System.out.println("Start Acquisition")
        val res = sendOutboundInvoke(action, agentId, mapOf(
            Pair("rtspUrl", rtspUrl), 
            Pair("processingUrl", processingUrl),
            Pair("streamSeconds", streamSeconds),
            Pair("frameRate", frameRate),
            Pair("startTimeStr", startTimeStr)
            ), String::class.java)

        System.out.println("Acquisition is done.")
        return res
    }

}
