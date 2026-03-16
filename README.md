SmartwatchBridge

Android application for exploring and communicating with a generic smartwatch using Bluetooth Low Energy (BLE).

This project aims to reverse engineer and interact with the Bluetooth GATT services of a smartwatch in order to read data such as battery level and heart rate and understand the device communication protocol.

---

Project Goals

The main objective of this project is to understand how a generic smartwatch communicates over BLE and create an Android bridge capable of interacting with it.

Goals include:

- Connect to the smartwatch via BLE
- Discover and analyze GATT services
- Read battery level
- Read heart rate data
- Understand proprietary BLE characteristics
- Document discovered UUIDs and commands
- Provide a development base for smartwatch integrations

---

Reference Device

This project was developed and tested using the following smartwatch:

Device: FILA SW/90

Because many generic smartwatches share the same firmware, the project may also work with other similar devices.

---

Technologies Used

- Android (Java / Kotlin)
- Bluetooth Low Energy (BLE)
- Android Bluetooth GATT API
- Android Studio

Development environment:

IDE: Android Studio

---

Features

Current features implemented or under development:

- BLE device scanning
- Smartwatch connection
- GATT service discovery
- Battery level reading
- Heart rate characteristic exploration

Future features planned:

- Notifications from phone to watch
- Step counter data
- Sleep tracking data
- Watch commands
- Data synchronization

---

BLE Architecture

The smartwatch communicates using Bluetooth Low Energy (BLE) and exposes services through the GATT (Generic Attribute Profile).

Typical BLE hierarchy:

Device
→ Services
→ Characteristics
→ Descriptors

Example:

Service UUID
└ Characteristic UUID
  └ Value (read/write/notify)

Understanding this structure allows the application to communicate with the smartwatch.

---

Tools Used for Reverse Engineering

The following tools were used to analyze the BLE communication:

- nRF Connect (BLE debugging)
- Android BLE Scanner
- GATT service explorer
- Android Logcat

These tools help discover services, characteristics and possible command formats.

---

Project Structure

Main Android project structure:

SmartwatchBridge
│
├ app
│ ├ src
│ │ ├ main
│ │ │ ├ java
│ │ │ ├ res
│ │ │ └ AndroidManifest.xml
│ │
├ gradle
├ build.gradle
└ settings.gradle

---

How to Build the Project

1. Clone the repository

git clone https://github.com/yourusername/SmartwatchBridge.git

2. Open the project in Android Studio

3. Let Gradle sync dependencies

4. Connect an Android device

5. Run the application

---

Current Development Status

Project status: Work in Progress

The goal is to progressively understand the smartwatch BLE protocol and document it.

---

Roadmap

Planned improvements:

- Improve BLE connection stability
- Map all smartwatch GATT services
- Decode proprietary characteristics
- Implement data synchronization
- Add UI for smartwatch information

---

Contributing

Contributions are welcome.

If you have a smartwatch using a similar BLE protocol you can:

- Test the project
- Report issues
- Share discovered UUIDs
- Suggest improvements

---

1. GATT Services Discovery
## Discovered GATT Services

During BLE scanning and exploration of the smartwatch, several GATT services were identified.

These services provide access to device information, battery status and proprietary smartwatch communication channels.

| Service Name | UUID | Type | Description |
|--------------|------|------|-------------|
| Battery Service | 0000180F-0000-1000-8000-00805f9b34fb | Standard BLE | Provides battery level information |
| Device Information | 0000180A-0000-1000-8000-00805f9b34fb | Standard BLE | Contains device metadata such as firmware version |
| Smartwatch Proprietary Service | FEE0 | Custom | Main smartwatch communication service |
| Smartwatch Proprietary Service | FEE1 | Custom | Additional communication channel |
| Smartwatch Proprietary Service | FEE2 | Custom | Command and control channel |
These proprietary services are commonly used in low-cost smartwatches to handle communication between the mobile application and the device.

2. Smartwatch BLE Characteristics
## Discovered GATT Characteristics

The following characteristics were discovered while exploring the smartwatch BLE services.

| Characteristic UUID | Properties | Service | Description |
|---------------------|-----------|--------|-------------|
| 2A19 | Read / Notify | Battery Service | Battery level value |
| FEE2 | Write | Proprietary Service | Used to send commands to the smartwatch |
| FEE2 | Write Without Response | Proprietary Service | Fast command transmission channel |
Important Characteristic
The characteristic FEE2 appears to be the main communication endpoint between the Android application and the smartwatch.
Properties:
Write
Write Without Response
This strongly suggests it is used as a command channel.

3. BLE Command Communication
## BLE Command Communication

Communication with the smartwatch appears to be performed through the characteristic:

FEE2

Commands are written to this characteristic using BLE write operations.

Two communication modes are available:

Write
Write Without Response

The second mode is typically used for fast command transmission where acknowledgement from the device is not required.
Possible command categories
Typical smartwatch BLE commands usually include:
Battery status requests
Heart rate measurements
Time synchronization
Notification forwarding
Step count synchronization
Device configuration

4. Example Command Structure (Hypothesis)
## Possible BLE Packet Structure

Initial reverse engineering suggests commands may follow a structured packet format.

Example packet layout:

[HEADER] [COMMAND] [DATA] [CHECKSUM]

Example:

AA 01 00 FF

Further reverse engineering is required to fully decode the protocol.

5. Tools Used During Reverse Engineering
## Tools Used for BLE Analysis

The following tools were used to analyze the smartwatch communication:

- nRF Connect
- Android Logcat
- Bluetooth GATT Explorer
- Android BLE Scanner
- Custom Android BLE debugging tools


Disclaimer

This project is intended for research and educational purposes related to BLE communication with smartwatches.

The project is not affiliated with any smartwatch manufacturer.

---

License

MIT License
Copyright (c) 2026 Miguel Pinto
GitHub: https://github.com/mabpintoPT
