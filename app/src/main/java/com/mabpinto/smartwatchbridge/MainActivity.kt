package com.mabpinto.smartwatchbridge

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.util.*

class MainActivity : ComponentActivity() {

    companion object {
        val UUID_SERVICE = UUID.fromString("0000feea-0000-1000-8000-00805f9b34fb")
        val UUID_WRITE   = UUID.fromString("0000fee2-0000-1000-8000-00805f9b34fb")
        val UUID_NOTIFY  = UUID.fromString("0000fee3-0000-1000-8000-00805f9b34fb")
        val UUID_DESC    = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val UUID_BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val UUID_BATTERY_CHAR    = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

        const val BATTERY_INTERVAL = 60000L // 🔋 Intervalo de 1 minuto (60.000 ms)
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val queue: Queue<ByteArray> = LinkedList()
    private var isWriting = false
    private var lastBatteryLevel = "--"

    // 🕒 Handler para agendamento automático
    private val mainHandler = Handler(Looper.getMainLooper())
    private val batteryRunnable = object : Runnable {
        override fun run() {
            readBatteryLevel()
            mainHandler.postDelayed(this, BATTERY_INTERVAL)
        }
    }

    private lateinit var statusText: TextView
    private lateinit var heartText: TextView
    private lateinit var stepsText: TextView
    private lateinit var logText: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUI()
        requestPermissions()
        startScan()
    }

    // Parar o agendamento se a app for fechada
    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(batteryRunnable)
    }

    private fun setupUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40,40,40,40)
        }

        statusText = TextView(this).apply { text = "Procurando..." }
        heartText = TextView(this).apply { textSize = 24f; text = "❤️ -- bpm" }
        stepsText = TextView(this).apply { textSize = 24f; text = "👟 -- passos" }

        root.addView(statusText)
        root.addView(heartText)
        root.addView(stepsText)

        val grid = GridLayout(this).apply { columnCount = 2 }

        fun btn(label:String, action:()->Unit) {
            grid.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        btn("VIBRAR") { send(0x53, byteArrayOf(0x0C)) }
        btn("FIND") { send(0x61) }
        btn("START HR") { startHR() }
        btn("SYNC STEPS") { send(0x08) }

        root.addView(grid)

        logText = TextView(this).apply { textSize = 10f }
        scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1,0,1f)
            addView(logText)
        }

        root.addView(scroll)
        setContentView(root)
    }

    // =========================
    // 🔋 BATTERY LOGIC
    // =========================
    private fun readBatteryLevel() {
        val g = bluetoothGatt ?: return
        val batChar = g.getService(UUID_BATTERY_SERVICE)?.getCharacteristic(UUID_BATTERY_CHAR)

        if (batChar != null && !isWriting &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            log("🔋 Lendo Bateria...")
            g.readCharacteristic(batChar)
        }
    }

    private fun build(cmd:Int, payload:ByteArray = byteArrayOf()):ByteArray{
        val p = ByteArray(20)
        p[0]=0xFE.toByte()
        p[1]=0xEA.toByte()
        p[2]=0x20
        p[3]=(5+payload.size).toByte()
        p[4]=cmd.toByte()

        System.arraycopy(payload,0,p,5,payload.size)

        var sum=0
        for(i in 0 until 19) sum+=p[i].toInt() and 0xFF
        p[19]=(sum and 0xFF).toByte()

        return p
    }

    private fun send(cmd:Int, payload:ByteArray = byteArrayOf()){
        enqueue(build(cmd,payload))
    }

    private fun startHR(){
        log("💓 Start HR")
        send(0x6D, byteArrayOf(0x00))
    }

    private fun enqueue(p:ByteArray){
        queue.add(p)
        if(!isWriting) next()
    }

    private fun next(){
        val g = bluetoothGatt ?: return
        val p = queue.poll() ?: run { isWriting=false; return }

        val char = g.getService(UUID_SERVICE)?.getCharacteristic(UUID_WRITE)

        if(char!=null &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){

            isWriting=true
            char.value=p
            g.writeCharacteristic(char)
            log("TX: ${p.joinToString(" ") { "%02X".format(it) }}")
        }
    }

    private val callback = object:BluetoothGattCallback(){

        override fun onConnectionStateChange(g:BluetoothGatt, s:Int, new:Int){
            if(new==BluetoothProfile.STATE_CONNECTED){
                runOnUiThread{ statusText.text="Conectado" }
                g.discoverServices()
            } else if (new == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.removeCallbacks(batteryRunnable)
                runOnUiThread { statusText.text = "Desconectado" }
            }
        }

        override fun onServicesDiscovered(g:BluetoothGatt, s:Int){
            val notifyChar = g.getService(UUID_SERVICE)?.getCharacteristic(UUID_NOTIFY)

            if(notifyChar!=null &&
                ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){

                g.setCharacteristicNotification(notifyChar,true)
                val desc = notifyChar.getDescriptor(UUID_DESC)
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(desc)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            // Após ativar notificações, iniciamos o loop da bateria
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mainHandler.post(batteryRunnable)
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if(status == BluetoothGatt.GATT_SUCCESS && c.uuid == UUID_BATTERY_CHAR){
                val battery = c.value[0].toInt() and 0xFF
                lastBatteryLevel = "$battery%"
                runOnUiThread {
                    statusText.text = "Conectado 🔋 $lastBatteryLevel"
                }
                log("Battery: $lastBatteryLevel")
            }
        }

        override fun onCharacteristicWrite(g:BluetoothGatt, c:BluetoothGattCharacteristic, s:Int){
            isWriting=false
            // Pequeno delay para estabilidade do rádio Bluetooth
            mainHandler.postDelayed({ next() }, 50)
        }

        override fun onCharacteristicChanged(g:BluetoothGatt, c:BluetoothGattCharacteristic){
            val data = c.value ?: return
            if(data.size < 5) return

            val type = data[2].toInt() and 0xFF
            val sub  = data[3].toInt() and 0xFF

            runOnUiThread {
                if(type==0x20 && sub==0x06){
                    val bpm = if(data.size > 5) data[5].toInt() and 0xFF else data[4].toInt() and 0xFF
                    if(bpm in 40..200){
                        heartText.text = "❤️ $bpm bpm"
                    }
                }

                if(type==0x20 && sub==0x08 && data.size >= 6){
                    val steps = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
                    if(steps in 0..50000) {
                        stepsText.text = "👟 $steps passos"
                    }
                }
            }
        }
    }

    private fun log(s:String){
        runOnUiThread{
            logText.append("\n$s")
            scroll.post{ scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun startScan(){
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val scanner = adapter.bluetoothLeScanner
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED) return

        scanner.startScan(object:ScanCallback(){
            override fun onScanResult(t:Int, r:ScanResult){
                if(r.device.name?.contains("SW/90",true)==true && bluetoothGatt==null){
                    scanner.stopScan(this)
                    bluetoothGatt = r.device.connectGatt(this@MainActivity,false,callback)
                }
            }
        })
    }

    private fun requestPermissions(){
        ActivityCompat.requestPermissions(this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),1)
    }
}