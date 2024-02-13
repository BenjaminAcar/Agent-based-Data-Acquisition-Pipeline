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


import java.io.IOException
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import kotlin.concurrent.thread

class FileManagerAgent(name: String) : AbstractContainerizedAgent(name=name) {

    private var lastMessage: Any? = null;
    private var lastBroadcast: Any? = null;

    private var extraActions = mutableListOf<Action>();

    override fun getDescription() = AgentDescription(
            this.name,
            this.javaClass.name,
            listOf(
                Action("GetFilesForCameraID", mapOf(Pair("rtspUrl", "String")), "List<String>"),
                Action("GetInfo", mapOf(), "Map"),
                Action("Fail", mapOf(), "void"),
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
                "GetFilesForCameraID" -> actionGetFilesForCameraId(it.parameters["rtspUrl"]!!.asText())
                "GetInfo" -> actionGetInfo()
                "Fail" -> actionFail()
                "Deregister" -> deregister(false)
                else -> Unit
            }
        }

    }


    private fun sanitizeFileName(input: String): String {
        val ipAndPortPattern = "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5})".toRegex()
        return ipAndPortPattern.find(input)?.value?.replace(Regex("[:.]"), "_") ?: ""
    }

    private fun actionGetFilesForCameraId(rtspUrl: String): List<String> {
        val sanitizedCameraId = sanitizeFileName(rtspUrl)
        val directory = File("./")
        val matchingFiles = mutableListOf<String>()

        if (directory.exists() && directory.isDirectory) {
            val files = directory.listFiles()
            files?.filter { it.name.contains(sanitizedCameraId) && it.name.endsWith(".zip") }
                ?.mapTo(matchingFiles) { it.name }
                ?: throw UncheckedIOException(IOException("Directory not found or is not a directory."))
        }

        return matchingFiles
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
