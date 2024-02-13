package de.gtarc.opaca.sample

import de.gtarc.opaca.api.AgentContainerApi
import de.gtarc.opaca.container.AbstractContainerizedAgent
import de.gtarc.opaca.container.Invoke
import de.gtarc.opaca.model.Action
import de.gtarc.opaca.model.AgentDescription
import de.gtarc.opaca.model.Message
import de.dailab.jiacvi.behaviour.act

import java.util.UUID
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

import java.text.SimpleDateFormat

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import javax.imageio.ImageIO
import java.net.URL
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.image.BufferedImage

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request

import kotlin.math.max
import kotlin.concurrent.thread

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.*
import java.nio.file.Files
import java.nio.file.Paths

import java.time.Instant

class DataCaptureAndProcessAgent(name: String) : AbstractContainerizedAgent(name = name) {

    private var lastMessage: Any? = null
    private var lastBroadcast: Any? = null

    private var extraActions = mutableListOf<Action>()

    override fun getDescription() = AgentDescription(
        this.name,
        this.javaClass.name,
        listOf(
            Action(
                "CaptureAndProcessData",
                mapOf(
                    Pair("rtspUrl" , "String"),
                    Pair("processingUrl" , "String"),
                    Pair("streamSeconds" , "Int"),
                    Pair("frameRate" , "Int"),
                    Pair("startTime", "String")
                ),
                "String"
            ),
            Action("GetInfo", mapOf(), "Map"),
            Action("Deregister", mapOf(), "Void")
        ),
        listOf(
        )
    )

    override fun behaviour() = act {

        on<Message> {
            println("ON $it")
            lastMessage = it.payload
        }

        listen<Message>("topic") {
            println("LISTEN $it")
            lastBroadcast = it.payload
        }

        respond<Invoke, Any?> {
            println("RESPOND $it")
            when (it.name) {
                    "CaptureAndProcessData" -> 
                    actionCaptureAndProcessData(    
                        it.parameters["rtspUrl"]?.asText() ?: "", 
                        it.parameters["processingUrl"]?.asText() ?: "", 
                        it.parameters["streamSeconds"]?.asInt() ?: 10, 
                        it.parameters["frameRate"]?.asInt() ?: 2,
                        it.parameters["startTime"]?.asText() ?: LocalDateTime.now().plusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                "GetInfo" -> actionGetInfo()
                "Deregister" -> deregister(false)
                else -> extraActions.firstOrNull { a -> a.name == it.name }?.let { "Called extra action ${it.name}" }
            }
        }

    }

    fun generateRandomUUID(): String {
        return UUID.randomUUID().toString()
    }


    private fun sanitizeFileName(input: String): String {
        val ipAndPortPattern = "([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,5})".toRegex()
        return ipAndPortPattern.find(input)?.value?.replace(Regex("[:.]"), "_") ?: ""
    }

    private fun actionCaptureAndProcessData(rtspUrl: String, processingUrl: String, streamSeconds: Int, frameRate: Int, startTimeStr: String): String {
        val sanitizedCameraId = sanitizeFileName(rtspUrl)
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        var totalFramesProcessed = 0

        val outputFolder = File("${sanitizedCameraId}_$timestamp").apply {
            if (!exists()) mkdir()
        }

        val outputFolderPath = outputFolder.absolutePath
        val zipPath = "${outputFolderPath}.zip"

        val berlinZoneId = ZoneId.of("Europe/Berlin")

        val startTime = LocalDateTime.parse(startTimeStr)
                            .atZone(berlinZoneId)
                            .toInstant()
                            .toEpochMilli()

        val isCapturingDone = AtomicBoolean(false)
        val frameQueue = LinkedBlockingQueue<Pair<ByteArray, String>>()

        val grabber = FFmpegFrameGrabber(rtspUrl).apply {
            try {
                println("Starting frame grabber for URL: $rtspUrl")
                start()
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Failed to start frame grabber", e)
            }
        }

        while (System.currentTimeMillis() < startTime) {
            println("Waiting to reach the scheduled start time...")
            Thread.sleep(1000) // Sleep for a short duration to prevent busy waiting
        }

        val frameIntervalMillis = (1000.0 / frameRate).toLong() // Milliseconds per frame


        val captureThread = thread(start = true) {
            try {
                var nextCaptureTime = startTime
                while (System.currentTimeMillis() - startTime < streamSeconds * 1000) {
                    println(System.currentTimeMillis() - startTime < streamSeconds * 1000)
                    val currentTime = System.currentTimeMillis()
                    if (currentTime >= nextCaptureTime) {
                        val captureStartTime = System.currentTimeMillis()
                        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                        try {
                            val frame = captureFrame(grabber)
                            frameQueue.put(Pair(frame, timestamp))
                        } catch (e: Exception) {
                            println("Error capturing frame at $timestamp: ${e.message}")
                            e.printStackTrace()
                        }
                        nextCaptureTime += frameIntervalMillis
                    }
                    Thread.sleep(max(0, nextCaptureTime - System.currentTimeMillis()))
                }
            } catch (e: Exception) {
                println("Exception in capture thread: ${e.message}")
                e.printStackTrace()
            } finally {
                while (!frameQueue.isEmpty()) {
                    Thread.sleep(100) // Check every 100ms
                }
                isCapturingDone.set(true)
            }
            }

        val processThread = thread(start = true) {
            try {
                println("Process thread started at: ${LocalDateTime.now()}")
                while (!isCapturingDone.get()) {
                    val (frame, timestamp) = frameQueue.poll(5, TimeUnit.SECONDS) ?: continue
                    println("Processing frame at: $timestamp")
                    try {
                        val blurredFrame = sendFrameForProcessing(frame, processingUrl)
                        val filePath = "$outputFolderPath/$timestamp.jpg"
                        saveToFile(blurredFrame, filePath)
                        println("Saved frame at: $filePath")
                        totalFramesProcessed++
                    } catch (e: Exception) {
                        println("Error processing frame at $timestamp: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                println("Exception in process thread: ${e.message}")
                e.printStackTrace()
            } finally {
                println("Process thread finished at: ${LocalDateTime.now()}")
            }
        }

        captureThread.join()
        processThread.join()
        zipImages(outputFolderPath, zipPath)
        grabber.stop()

        system.spawnAgent(DataStreamAgent("${sanitizedCameraId}_$timestamp", zipPath)) 

        val currentDateTimeInBerlin = ZonedDateTime.now(berlinZoneId)

        // Define the format to match the specified format "%Y-%m-%dT%H:%M"
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        // Format the current date-time to match the specified format
        val currentTimestampFormatted = currentDateTimeInBerlin.format(dateTimeFormatter)

        // File(outputFolderPath).deleteRecursively()

        return "Capture and process data action completed at $currentTimestampFormatted. Start Time: $startTimeStr. Total Frames: $totalFramesProcessed"
    
    }

    fun captureFrame(grabber: FFmpegFrameGrabber): ByteArray {
        val frame = grabber.grab()
        val converter = Java2DFrameConverter()
        val bufferedImage: BufferedImage = converter.convert(frame)

        val baos = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "jpg", baos)
        baos.flush()
        return baos.toByteArray()
    }

    fun sendFrameForProcessing(frame: ByteArray, processingUrl: String): ByteArray {
        val timeout = 330L

        // Create a custom OkHttpClient with increased timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .writeTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .build()

        // Create a multipart body builder
        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Add the frame as a file
        val frameBody = frame.toRequestBody("image/jpeg".toMediaTypeOrNull())
        requestBodyBuilder.addFormDataPart("file", "frame.jpg", frameBody)

        // Build the request body
        val requestBody = requestBodyBuilder.build()

        // Build the request
        val request = Request.Builder()
            .url(processingUrl)
            .post(requestBody)
            .build()

        // Execute the request
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            return response.body?.bytes() ?: throw IOException("Response body is null")
        }
    }


    fun saveToFile(imageData: ByteArray, filePath: String) {
        val bais = ByteArrayInputStream(imageData)
        val image = ImageIO.read(bais)
        ImageIO.write(image, "jpg", File(filePath))
    }

    fun zipImages(directoryPath: String, zipFilePath: String) {
        ZipOutputStream(FileOutputStream(zipFilePath)).use { zos ->
            Files.walk(Paths.get(directoryPath)).filter { path -> Files.isRegularFile(path) }.forEach { path ->
                val zipEntry = ZipEntry(path.fileName.toString())
                zos.putNextEntry(zipEntry)
                Files.copy(path, zos)
                zos.closeEntry()
            }
        }
    }


    fun deleteImagesInFolder(folderPath: String) {
        Files.walk(Paths.get(folderPath))
            .filter { Files.isRegularFile(it) }
            .forEach { Files.delete(it) }
    }

    fun deleteZipFile(zipFilePath: String) {
        Files.deleteIfExists(Paths.get(zipFilePath))
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
