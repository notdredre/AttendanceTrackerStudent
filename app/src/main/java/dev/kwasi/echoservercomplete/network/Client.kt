package dev.kwasi.echoservercomplete.network

import android.util.Log
import com.google.gson.Gson
import dev.kwasi.echoservercomplete.models.ContentModel
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.Socket
import kotlin.concurrent.thread
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.SecretKey
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Client (private val networkMessageInterface: NetworkMessageInterface, studentID: Int, authCallback: (auth: Boolean) -> Unit){
    private lateinit var clientSocket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter
    var ip:String = ""
    private lateinit var hashID: String
    private lateinit var aesKey: SecretKeySpec
    private lateinit var aesIV: IvParameterSpec

    private var auth = false

    init {
        thread {
            clientSocket = Socket(InetAddress.getByName("192.168.49.1"), 9999)
            reader = clientSocket.inputStream.bufferedReader()
            writer = clientSocket.outputStream.bufferedWriter()
            ip = clientSocket.inetAddress.hostAddress!!
            generateKey(studentID)

            // Challenge response
            auth = challengeResponse()
            authCallback(auth)
            while(true){
                try{
                    if (auth) {
                        val serverResponse = reader.readLine()
                        if (serverResponse != null) {
                            val serverContent =
                                Gson().fromJson(serverResponse, ContentModel::class.java)
                            networkMessageInterface.onContent(serverContent)
                        }
                    }
                    else
                        return@thread
                } catch(e: Exception){
                    Log.e("CLIENT", "An error has occurred in the client")
                    e.printStackTrace()
                    break
                }
            }
        }
    }

    private fun sendMessage(content: ContentModel){
        thread {
            if (clientSocket.isClosed){
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

    fun clientSocketOpen(): Boolean {
        return !clientSocket.isClosed
    }

    fun getAuth(): Boolean {
        return auth
    }

    fun sendMessageEncrypted(message: String) {
        thread {
            if (clientSocket.isClosed){
                throw Exception("We aren't currently connected to the server!")
            }
            val cypher = encryptMessage(message, aesKey, aesIV) // Encrypt plaintext
            val messageContent = ContentModel(cypher, ip) // Create ContentModel with cypher
            val contentAsStr:String = Gson().toJson(messageContent)
            writer.write("$contentAsStr\n")
            writer.flush()
        }
    }
    fun challengeResponse(): Boolean{
        val request = ContentModel("I am here", ip)
        sendMessage(request) // Send challenge
        val challenge = reader.readLine()
        Log.i("CHALLENGE", "Challenge is: $challenge")
        if (challenge != null) {
            val challengeContent = Gson().fromJson(challenge, ContentModel::class.java) // Receive R
            val nonce = challengeContent.message // Plaintext R
            sendMessageEncrypted(nonce) // Encrypt nonce with hash of ID and send

            // Assuming lecturer sends some kind of confirmation if in class
            val confirm = reader.readLine()
            Log.i("CONFIRM", "Confirm is: $confirm")
            if (confirm == null) {
                close()
                return false
            }
            return false
        }
        close()
        return false
    }

    // Encryption
    private fun generateKey(studentID: Int) {
        hashID = hashStrSha256(studentID.toString()) // Hash student ID
        aesKey = generateAESKey(hashID)
        aesIV = generateIV(hashID)
    }
    private fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte) }

    private fun getFirstNChars(str: String, n:Int) = str.substring(0,n)

    fun hashStrSha256(str: String): String{
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(UTF_8))
        return hashedString.toHex();
    }

    fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = getFirstNChars(seed,32)
        val secretKey = SecretKeySpec(first32Chars.toByteArray(), "AES")
        return secretKey
    }

    fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = getFirstNChars(seed, 16)
        return IvParameterSpec(first16Chars.toByteArray())
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptMessage(plaintext: String, aesKey:SecretKey, aesIv: IvParameterSpec):String{
        val plainTextByteArr = plaintext.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)

        val encrypt = cipher.doFinal(plainTextByteArr)
        return Base64.Default.encode(encrypt)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decryptMessage(encryptedText: String, aesKey:SecretKey, aesIv: IvParameterSpec):String{
        val textToDecrypt = Base64.Default.decode(encryptedText)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")

        cipher.init(Cipher.DECRYPT_MODE, aesKey,aesIv)

        val decrypt = cipher.doFinal(textToDecrypt)
        return String(decrypt)

    }
}