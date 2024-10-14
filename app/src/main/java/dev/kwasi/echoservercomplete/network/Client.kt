package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket
import kotlin.concurrent.thread

class Client (private val networkMessageInterface: NetworkMessageInterface){
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    var ip:String = ""

    init {
        thread {
            clientSocket = Socket("192.168.49.1", Server.PORT)
            reader = clientSocket.inputStream.bufferedReader()
            writer = clientSocket.outputStream.bufferedWriter()
            ip = clientSocket.inetAddress.hostAddress!!
            while(true){
                if (clientSocket.isClosed)
                    return@thread
                try{
                    val serverResponse = reader.readLine()
                    if (serverResponse != null){
                        val serverContent = Gson().fromJson(serverResponse, ContentModel::class.java)
                        networkMessageInterface.onContent(serverContent)
                    }
                } catch(e: Exception){
                    Log.e("CLIENT", "An error has occurred in the client")
                    e.printStackTrace()
                    break
                }
            }
        }
    }

    fun sendMessage(content: ContentModel){
        thread {
            if (!clientSocket.isConnected){
                throw Exception("We aren't currently connected to the server!")
            }
            val contentAsStr:String = Gson().toJson(content)
            writer.write("$contentAsStr\n")
            writer.flush()
        }

    }

    fun close(){
        clientSocket.close()
    }
}