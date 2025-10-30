package edu.udb.sv

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var connectButton: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnStop: Button
    private lateinit var btnToggleMode: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvMotorState: TextView

    // BLE
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothGatt: BluetoothGatt? = null

    // UUIDs del servicio y características
    private val SERVICE_UUID = UUID.fromString("19B10000-E8F2-537E-4F6C-D104768A1214")
    private val COMANDO_CHARACTERISTIC_UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
    private val SENSOR_CHARACTERISTIC_UUID = UUID.fromString("19B10002-E8F2-537E-4F6C-D104768A1214")

    private var comandoCharacteristic: BluetoothGattCharacteristic? = null
    private var sensorCharacteristic: BluetoothGattCharacteristic? = null

    private var isConnected = false
    private var isScanning = false
    private var modoAutomatico = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 1
        private const val SCAN_PERIOD: Long = 10000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initializeBluetooth()
    }

    private fun initViews() {
        connectButton = findViewById(R.id.connectButton)
        btnLeft = findViewById(R.id.btnLeft)
        btnRight = findViewById(R.id.btnRight)
        btnStop = findViewById(R.id.btnStop)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        tvStatus = findViewById(R.id.tvStatus)
        tvDistance = findViewById(R.id.tvDistance)
        tvMotorState = findViewById(R.id.tvMotorState)

        setupClickListeners()

        // Inicialmente deshabilitar botones de control
        btnLeft.isEnabled = false
        btnRight.isEnabled = false
        btnStop.isEnabled = false
        btnToggleMode.isEnabled = false
    }

    private fun initializeBluetooth() {
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            tvStatus.text = "Bluetooth no disponible"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            tvStatus.text = "Bluetooth desactivado"
            return
        }

        checkPermissions()
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    private fun checkPermissions() {
        val permissions = getRequiredPermissions()
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, REQUEST_BLUETOOTH_PERMISSIONS)
        } else {
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_BLUETOOTH_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startScanning()
                } else {
                    tvStatus.text = "Permisos denegados"
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (isScanning) return

        tvStatus.text = "Buscando ARDUINO..."
        isScanning = true
        connectButton.isEnabled = false

        val leScanner = bluetoothAdapter.bluetoothLeScanner

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                val device = result.device
                val deviceName = device.name ?: "Sin nombre"

                if (deviceName == "ARDUINO") {
                    leScanner.stopScan(this)
                    isScanning = false
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                isScanning = false
                handler.post { connectButton.isEnabled = true }
            }
        }

        handler.postDelayed({
            if (isScanning) {
                leScanner.stopScan(scanCallback)
                isScanning = false
                handler.post {
                    connectButton.isEnabled = true
                    if (!isConnected) {
                        tvStatus.text = "ARDUINO no encontrado"
                    }
                }
            }
        }, SCAN_PERIOD)

        leScanner.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        tvStatus.text = "Conectando a ARDUINO..."
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    handler.post {
                        tvStatus.text = "Conectado a ARDUINO"
                        connectButton.text = "Desconectar"
                        isConnected = true
                        connectButton.isEnabled = true

                        btnLeft.isEnabled = true
                        btnRight.isEnabled = true
                        btnStop.isEnabled = true
                        btnToggleMode.isEnabled = true
                    }
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    handler.post {
                        disconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setupCharacteristics(gatt)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            if (characteristic.uuid == SENSOR_CHARACTERISTIC_UUID) {
                val data = characteristic.getStringValue(0)
                handler.post {
                    processSensorData(data ?: "")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID)
        if (service != null) {
            comandoCharacteristic = service.getCharacteristic(COMANDO_CHARACTERISTIC_UUID)
            sensorCharacteristic = service.getCharacteristic(SENSOR_CHARACTERISTIC_UUID)

            if (comandoCharacteristic != null && sensorCharacteristic != null) {
                gatt.setCharacteristicNotification(sensorCharacteristic, true)
                val descriptor = sensorCharacteristic!!.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(command: String) {
        if (!isConnected || comandoCharacteristic == null) return

        comandoCharacteristic!!.value = command.toByteArray()
        bluetoothGatt?.writeCharacteristic(comandoCharacteristic)
    }

    private fun processSensorData(data: String) {
        val parts = data.split(",")
        parts.forEach { part ->
            when {
                part.startsWith("DISTANCIA:") -> {
                    val distance = part.removePrefix("DISTANCIA:")
                    tvDistance.text = "$distance cm"
                }
                part.startsWith("MODO:") -> {
                    val mode = part.removePrefix("MODO:")
                    val nuevoModoAuto = mode == "AUTO"

                    if (nuevoModoAuto != modoAutomatico) {
                        modoAutomatico = nuevoModoAuto
                        btnToggleMode.text = if (modoAutomatico) "Cambiar a Manual" else "Cambiar a Automático"
                    }
                }
                part.startsWith("MOTOR:") -> {
                    val motorState = part.removePrefix("MOTOR:")
                    tvMotorState.text = "$motorState"
                }
            }
        }
    }

    private fun setupClickListeners() {
        connectButton.setOnClickListener {
            if (isConnected) {
                disconnect()
            } else {
                startScanning()
            }
        }

        btnLeft.setOnClickListener {
            if (!modoAutomatico) {
                sendCommand("IZQUIERDA")
            }
        }

        btnRight.setOnClickListener {
            if (!modoAutomatico) {
                sendCommand("DERECHA")
            }
        }

        btnStop.setOnClickListener {
            sendCommand("DETENER")
        }

        btnToggleMode.setOnClickListener {
            modoAutomatico = !modoAutomatico
            btnToggleMode.text = if (modoAutomatico) "Cambiar a Manual" else "Cambiar a Automático"

            val comando = if (modoAutomatico) "MODO_AUTO" else "MODO_MANUAL"
            sendCommand(comando)
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isScanning = false

        handler.post {
            isConnected = false
            modoAutomatico = false
            connectButton.text = "Conectar BLE"
            tvStatus.text = "Desconectado"
            tvDistance.text = "-- cm"
            tvMotorState.text = "--"

            btnLeft.isEnabled = false
            btnRight.isEnabled = false
            btnStop.isEnabled = false
            btnToggleMode.isEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}
