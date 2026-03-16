package com.mabpinto.smartwatchbridge

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var scanner: BluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    private lateinit var statusText: TextView
    private lateinit var batteryText: TextView
    private lateinit var heartText: TextView
    private lateinit var stepsText: TextView
    private lateinit var caloriesText: TextView
    private lateinit var logText: TextView
    private lateinit var scrollView: ScrollView

    private val discoveredDevices = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(40,40,40,120)

        val title = TextView(this)
        title.text = "Smartwatch Bridge"
        title.textSize = 24f
        title.gravity = Gravity.CENTER

        statusText = TextView(this)
        statusText.text = "Status: starting..."

        batteryText = TextView(this)
        batteryText.text = "🔋 Battery: --"

        heartText = TextView(this)
        heartText.textSize = 24f
        heartText.text = "❤️ -- bpm"

        stepsText = TextView(this)
        stepsText.textSize = 24f
        stepsText.text = "👟 --"

        caloriesText = TextView(this)
        caloriesText.textSize = 22f
        caloriesText.text = "🔥 -- kcal"

        root.addView(title)
        root.addView(statusText)
        root.addView(batteryText)
        root.addView(heartText)
        root.addView(stepsText)
        root.addView(caloriesText)

        val buttonsLayout = LinearLayout(this)
        buttonsLayout.orientation = LinearLayout.VERTICAL

        val vibrateButton = Button(this)
        vibrateButton.text = "VIBRATE"

        val heartButton = Button(this)
        heartButton.text = "START HEART"

        val syncButton = Button(this)
        syncButton.text = "SYNC DATA"

        val serviceScanButton = Button(this)
        serviceScanButton.text = "SCAN SERVICES"

        val commandScanButton = Button(this)
        commandScanButton.text = "SCAN COMMANDS"

        val copyLogsButton = Button(this)
        copyLogsButton.text = "COPY LOGS"

        val clearLogsButton = Button(this)
        clearLogsButton.text = "CLEAR LOGS"

        buttonsLayout.addView(vibrateButton)
        buttonsLayout.addView(heartButton)
        buttonsLayout.addView(syncButton)
        buttonsLayout.addView(serviceScanButton)
        buttonsLayout.addView(commandScanButton)
        buttonsLayout.addView(copyLogsButton)
        buttonsLayout.addView(clearLogsButton)

        root.addView(buttonsLayout)

        logText = TextView(this)
        logText.text = "--- BLE LOG ---\n"
        logText.textSize = 13f
        logText.setTextIsSelectable(true)

        scrollView = ScrollView(this)
        scrollView.addView(logText)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )
        params.weight = 1f

        scrollView.layoutParams = params
        root.addView(scrollView)

        setContentView(root)

        vibrateButton.setOnClickListener { sendCommand(0x12,0x01) }

        heartButton.setOnClickListener {
            log("Start heart sensor")
            sendCommand(0x15,0x01)
        }

        syncButton.setOnClickListener { syncWatchData() }

        serviceScanButton.setOnClickListener { scanServices() }

        commandScanButton.setOnClickListener { startCommandScan() }

        copyLogsButton.setOnClickListener {

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("logs", logText.text.toString())
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this,"Logs copied",Toast.LENGTH_SHORT).show()
        }

        clearLogsButton.setOnClickListener {
            logText.text = "--- BLE LOG ---\n"
        }

        log("App started")

        requestBlePermissions()

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            log("Bluetooth not supported")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            log("Bluetooth OFF")
            return
        }

        scanner = bluetoothAdapter.bluetoothLeScanner

        startBleScan()
    }

    private fun log(message:String){
        runOnUiThread{
            logText.append("$message\n")
            scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun requestBlePermissions(){

        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        ActivityCompat.requestPermissions(this,permissions,1)
    }

    private fun startBleScan(){

        log("Scanning devices...")

        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        scanner.startScan(scanCallback)
    }

    private val scanCallback = object:ScanCallback(){

        override fun onScanResult(callbackType:Int,result:ScanResult){

            val device = result.device
            val name = device.name ?: "Unknown"
            val address = device.address
            val rssi = result.rssi

            if(!discoveredDevices.contains(address)){

                discoveredDevices.add(address)

                log("Device: $name | RSSI:$rssi")
            }

            if(name.contains("SW",true) && bluetoothGatt == null){

                log("Watch detected")

                scanner.stopScan(this)

                connectDevice(device)
            }
        }
    }

    private fun connectDevice(device:BluetoothDevice){

        if(ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        log("Connecting...")

        bluetoothGatt = device.connectGatt(this,false,gattCallback)
    }

    private val gattCallback = object:BluetoothGattCallback(){

        override fun onConnectionStateChange(
            gatt:BluetoothGatt,
            status:Int,
            newState:Int
        ){

            if(newState == BluetoothProfile.STATE_CONNECTED){

                log("Connected")

                runOnUiThread {
                    statusText.text = "Status: Connected"
                }

                gatt.discoverServices()
            }

            if(newState == BluetoothProfile.STATE_DISCONNECTED){

                log("Disconnected")

                bluetoothGatt = null

                handler.postDelayed({
                    startBleScan()
                },3000)
            }
        }

        override fun onServicesDiscovered(
            gatt:BluetoothGatt,
            status:Int
        ){

            log("Services discovered")

            enableNotifications(gatt)

            readBatteryLevel(gatt)

            enableBatteryNotifications(gatt)

            syncWatchData()
        }

        override fun onCharacteristicChanged(
            gatt:BluetoothGatt,
            characteristic:BluetoothGattCharacteristic
        ){

            val data = characteristic.value

            val hex = data.joinToString(" ") { "%02X".format(it) }

            log("RX: $hex")

            parsePacket(data)
        }
    }

    private fun syncWatchData(){

        log("Starting full sync")

        handler.postDelayed({ sendCommand(0x20,0x01) },500)
        handler.postDelayed({ sendCommand(0x20,0x02) },1500)
        handler.postDelayed({ sendCommand(0x20,0x03) },2500)
        handler.postDelayed({ sendCommand(0x20,0x04) },3500)
    }

    private fun parsePacket(data:ByteArray){

        if(data.size < 4) {
            log("RX short packet")
            return
        }

        val type = data[2].toInt() and 0xFF
        val subtype = data[3].toInt() and 0xFF

        if(type == 0x20 && subtype == 0x05 && data.size >= 5){

            val battery = data[4].toInt() and 0xFF

            runOnUiThread {
                batteryText.text = "🔋 Battery: $battery%"
            }
        }

        if(type == 0x20 && subtype == 0x07 && data.size >= 7){

            val steps =
                (data[4].toInt() and 0xFF) or
                        ((data[5].toInt() and 0xFF) shl 8) or
                        ((data[6].toInt() and 0xFF) shl 16)

            runOnUiThread {
                stepsText.text = "👟 $steps"
            }
        }

        if(type == 0x20 && subtype == 0x06 && data.size >= 6){

            val bpm = data[5].toInt() and 0xFF

            runOnUiThread {
                heartText.text = "❤️ $bpm bpm"
            }
        }
    }

    private fun enableNotifications(gatt:BluetoothGatt){

        val service = gatt.getService(
            UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")
        ) ?: return

        for(characteristic in service.characteristics){

            if(characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0){

                gatt.setCharacteristicNotification(characteristic,true)

                val descriptor = characteristic.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )

                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                if(descriptor != null){
                    gatt.writeDescriptor(descriptor)
                }

                log("Notifications enabled for ${characteristic.uuid}")
            }
        }
    }

    private fun readBatteryLevel(gatt:BluetoothGatt){

        val service = gatt.getService(
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        )

        val characteristic = service?.getCharacteristic(
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        )

        characteristic?.let {
            gatt.readCharacteristic(it)
        }
    }

    private fun enableBatteryNotifications(gatt:BluetoothGatt){

        val service = gatt.getService(
            UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        )

        val characteristic = service?.getCharacteristic(
            UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        )

        if(characteristic != null){

            gatt.setCharacteristicNotification(characteristic,true)

            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )

            descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            if(descriptor != null){
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    private fun sendCommand(type:Int,subtype:Int){

        val command = byteArrayOf(
            0xFE.toByte(),
            0xEA.toByte(),
            type.toByte(),
            subtype.toByte()
        )

        sendCommandRaw(command)

        log("TX: FE EA ${"%02X".format(type)} ${"%02X".format(subtype)}")
    }

    private fun sendCommandRaw(command:ByteArray){

        val gatt = bluetoothGatt ?: return

        val service = gatt.getService(
            UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")
        )

        val characteristic = service?.getCharacteristic(
            UUID.fromString("0000fee1-0000-1000-8000-00805f9b34fb")
        ) ?: return

        characteristic.value = command

        gatt.writeCharacteristic(characteristic)
    }

    private fun scanServices(){

        val gatt = bluetoothGatt ?: return

        log("===== SERVICES =====")

        for(service in gatt.services){

            log("SERVICE: ${service.uuid}")

            for(characteristic in service.characteristics){

                log("  CHAR: ${characteristic.uuid}")
            }
        }
    }

    private fun startCommandScan(){

        log("Command scan started")

        Thread{

            for(i in 0..30){

                sendCommand(i,1)

                Thread.sleep(500)
            }

        }.start()
    }
}