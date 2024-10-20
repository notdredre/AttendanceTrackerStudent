package dev.kwasi.echoservercomplete

import android.content.Context
import android.content.DialogInterface
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet.Layout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.kwasi.echoservercomplete.chatlist.ChatListAdapter
import dev.kwasi.echoservercomplete.models.ContentModel
import dev.kwasi.echoservercomplete.network.Client
import dev.kwasi.echoservercomplete.network.NetworkMessageInterface
import dev.kwasi.echoservercomplete.network.Server
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapter
import dev.kwasi.echoservercomplete.peerlist.PeerListAdapterInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectInterface
import dev.kwasi.echoservercomplete.wifidirect.WifiDirectManager
import kotlin.system.exitProcess

class CommunicationActivity : AppCompatActivity(), WifiDirectInterface, PeerListAdapterInterface, NetworkMessageInterface {
    private var wfdManager: WifiDirectManager? = null

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private var peerListAdapter:PeerListAdapter? = null
    private var chatListAdapter:ChatListAdapter? = null

    private var wfdAdapterEnabled = false
    private var wfdHasConnection = false
    private var hasDevices = false
    private var server: Server? = null
    private var client: Client? = null
    private var deviceIp: String = ""
    private var studentID: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_communication)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(this, mainLooper, null)
        wfdManager = WifiDirectManager(manager, channel, this)

        peerListAdapter = PeerListAdapter(this)
        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.adapter = peerListAdapter
        rvPeerList.layoutManager = LinearLayoutManager(this)

        chatListAdapter = ChatListAdapter()
        val rvChatList: RecyclerView = findViewById(R.id.rvChat)
        rvChatList.adapter = chatListAdapter
        rvChatList.layoutManager = LinearLayoutManager(this)

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            // If connected to any groups, disconnect
            override fun handleOnBackPressed() {
                if (wfdHasConnection) {
                    val alertBuilder: AlertDialog.Builder = AlertDialog.Builder(this@CommunicationActivity)
                    alertBuilder.setTitle("Leave group?")
                    alertBuilder.setMessage("Are you sure you want to leave this class group?")
                    alertBuilder.setPositiveButton("Leave Group", object: DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            server?.close()
                            client?.close()
                            wfdManager?.disconnect()
                            updateUI()
                        }
                    })
                    alertBuilder.setNegativeButton("Cancel", object: DialogInterface.OnClickListener {
                        override fun onClick(dialog: DialogInterface?, which: Int) {
                            dialog?.cancel()
                        }
                    })
                    val alert: AlertDialog = alertBuilder.create()
                    alert.show()
                }
                else {
                    finish()
                    exitProcess(0)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        wfdManager?.also {
            registerReceiver(it, intentFilter)
        }
    }

    override fun onPause() {
        super.onPause()
        wfdManager?.also {
            unregisterReceiver(it)
        }
    }

    fun createGroup(view: View) {
        wfdManager?.createGroup()
    }

    fun discoverNearbyPeers(view: View) {
        val studentIDET:EditText = findViewById(R.id.etStudentID)
        if (studentIDET.text.isEmpty()) {
            val toast = Toast.makeText(
                this,
                "Please enter a student ID to search for classes.",
                Toast.LENGTH_SHORT
            )
            toast.show()
            return
        }
        if (studentIDET.text.length <= 9) {
            studentID = studentIDET.text.toString().toInt()
            if (studentID in 816000000..816999999) {
                wfdManager?.discoverPeers()
                val classSearchView: ConstraintLayout = findViewById(R.id.searchingClasses)
                if (!hasDevices)
                    classSearchView.visibility = View.VISIBLE
                else {
                    classSearchView.visibility = View.GONE
                    val toast = Toast.makeText(
                        this,
                        "Updating list of nearby class groups...",
                        Toast.LENGTH_SHORT
                    )
                    toast.show()
                }
            }
        }
        else {
            val toast = Toast.makeText(this, "Student ID is invalid. Please enter a valid student ID.", Toast.LENGTH_SHORT)
            toast.show()
        }
    }

    private fun updateUI(){
        //The rules for updating the UI are as follows:
        // IF the WFD adapter is NOT enabled then
        //      Show UI that says turn on the wifi adapter
        // ELSE IF there is NO WFD connection then i need to show a view that allows the user to either
            // 1) create a group with them as the group owner OR
            // 2) discover nearby groups
        // ELSE IF there are nearby groups found, i need to show them in a list
        // ELSE IF i have a WFD connection i need to show a chat interface where i can send/receive messages
        val wfdAdapterErrorView:ConstraintLayout = findViewById(R.id.clWfdAdapterDisabled)
        wfdAdapterErrorView.visibility = if (!wfdAdapterEnabled) View.VISIBLE else View.GONE

        val wfdNoConnectionView:ConstraintLayout = findViewById(R.id.clNoWifiDirectConnection)
        wfdNoConnectionView.visibility = if (wfdAdapterEnabled && !wfdHasConnection) View.VISIBLE else View.GONE

        val rvPeerList: RecyclerView= findViewById(R.id.rvPeerListing)
        rvPeerList.visibility = if (wfdAdapterEnabled && !wfdHasConnection && hasDevices) View.VISIBLE else View.GONE

        val wfdConnectedView:ConstraintLayout = findViewById(R.id.clHasConnection)
        wfdConnectedView.visibility = if(wfdHasConnection)View.VISIBLE else View.GONE

        val classSearchView: ConstraintLayout = findViewById(R.id.searchingClasses)
        classSearchView.visibility = View.GONE

        val groupName: TextView = findViewById(R.id.groupNameTextView)
        val groupDisplay = wfdManager?.groupInfo?.networkName
        groupName.text = groupDisplay
    }

    fun sendMessage(view: View) {
        val etMessage:EditText = findViewById(R.id.etMessage)
        val etString = etMessage.text.toString()
        val content = ContentModel(etString, deviceIp)
        etMessage.text.clear()
        client?.sendMessageEncrypted(etString)
        chatListAdapter?.addItemToEnd(content)

    }

    override fun onWiFiDirectStateChanged(isEnabled: Boolean) {
        wfdAdapterEnabled = isEnabled
        var text = "There was a state change in the WiFi Direct. Currently it is "
        text = if (isEnabled){
            "$text enabled!"
        } else {
            "$text disabled! Try turning on the WiFi adapter"
        }

        val toast = Toast.makeText(this, text, Toast.LENGTH_SHORT)
        //toast.show()
        updateUI()
    }

    override fun onPeerListUpdated(deviceList: Collection<WifiP2pDevice>) {
        val toast = Toast.makeText(this, "Updated listing of nearby WiFi Direct devices", Toast.LENGTH_SHORT)
        //toast.show()

        hasDevices = deviceList.isNotEmpty()
        peerListAdapter?.updateList(deviceList)
        updateUI()
    }

    override fun onGroupStatusChanged(groupInfo: WifiP2pGroup?) {
        val text = if (groupInfo == null){
            "Group is not formed"
        } else {
            "Group has been formed"
        }
        val toast = Toast.makeText(this, text , Toast.LENGTH_SHORT)
        //toast.show()
        wfdHasConnection = groupInfo != null

        Log.i("Network", "groupInfo is $groupInfo")

        if (groupInfo == null){
            server?.close()
            client?.close()
        } else if (groupInfo.isGroupOwner && server == null){
            server = Server(this)
            deviceIp = "192.168.49.1"
            Log.i("Network", "I am the server")
        } else if (!groupInfo.isGroupOwner && client == null) {
            client = Client(this, studentID) { auth ->
                if (!auth) {
                    Looper.prepare()
                    val unauthToast = Toast.makeText(
                        this,
                        "You are not registered for this class. Please contact the lecturer responsible if you believe this is a mistake.",
                        Toast.LENGTH_LONG
                    )
                    unauthToast.show()
                    wfdManager?.disconnect()
                }
            }
            deviceIp = client!!.ip
        }
        Log.i("Network", "The server is $server")
        Log.i("Network", "Device ip is $deviceIp")
        updateUI()
    }

    override fun onDeviceStatusChanged(thisDevice: WifiP2pDevice) {
        val toast = Toast.makeText(this, "Device parameters have been updated" , Toast.LENGTH_SHORT)
        //toast.show()
    }

    override fun onPeerClicked(peer: WifiP2pDevice) {
        wfdManager?.connectToPeer(peer)
    }


    override fun onContent(content: ContentModel) {
        runOnUiThread{
            chatListAdapter?.addItemToEnd(content)
        }
    }

}