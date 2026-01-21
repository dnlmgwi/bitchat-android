  
TECHNICAL SPECIFICATION

**Offline Music Analytics & Royalty System**

Decentralized Playback Data Collection for Music Publishing

Version 1.0 | January 2026

# **1\. Executive Summary**

This specification defines an offline-first music analytics system designed for markets with limited internet infrastructure. The system enables music publishing and distribution companies to collect accurate playback data and track music distribution patterns for artists, even when end-user devices operate primarily offline.

The system leverages Bluetooth mesh networking (adapted from the open-source bitchat protocol) to aggregate playback data and transfer metadata from consumer devices to local collection points (burning centers, distribution hubs), which then sync to central servers when internet connectivity is available.

**Key Focus**: The system tracks metadata about music transfers (via USB OTG, Bluetooth, or other methods) rather than facilitating file sharing itself. This provides valuable distribution analytics while respecting user privacy and avoiding copyright concerns.

## **1.1 Key Design Goals**

* Operate on low-specification Android and Windows devices
* Function entirely offline with eventual sync (days to months acceptable)
* Generate unique track identifiers without requiring industry-standard ISRCs
* Provide verifiable, tamper-resistant playback records
* **Track music distribution patterns via metadata (not file sharing)**
* **Monitor transfer events via USB OTG, Bluetooth, and other methods**
* Support royalty calculations based on actual listening behavior
* Minimize battery and storage consumption on end-user devices

# **2\. System Architecture**

## **2.1 System Components**

| Component | Platform | Function |
| :---- | :---- | :---- |
| Music Player App | Android (tablets/phones) | Plays music, logs playback data, tracks transfer metadata |
| Desktop Player | Windows (low-spec PCs) | Plays music, logs playback data, tracks transfer metadata |
| Aggregation Hub | Android / Windows | Collects data from nearby devices via Bluetooth mesh |
| Sync Service | Cloud (publisher infrastructure) | Receives, validates, stores data; calculates royalties and distribution analytics |

## **2.2 Data Flow Architecture**

┌─────────────────┐     Bluetooth Mesh      ┌─────────────────┐  
│  Music Player   │◄────────────────────────►│  Music Player   │  
│  (End User)     │   sync playback data     │  (End User)     │  
│                 │   + transfer metadata    │                 │  
└────────┬────────┘                          └────────┬────────┘  
         │                                            │  
         │ proximity sync                             │  
         ▼                                            ▼  
┌──────────────────────────────────────────────────────────────┐  
│                    Aggregation Node                          │  
│              (Burning Center / Local Hub)                    │  
│         - Collects playback data from nearby devices         │  
│         - Tracks music distribution patterns                 │  
│         - Validates and deduplicates                         │  
│         - Stores locally until internet available            │  
└──────────────────────────────────────────────────────────────┘  
                              │  
                              │ periodic internet sync  
                              ▼  
┌──────────────────────────────────────────────────────────────┐  
│                Publishing/Distribution Company               │  
│         - Permanent storage and audit trail                  │  
│         - Cross-validation between aggregators               │  
│         - Royalty calculations and artist payments           │  
│         - Distribution analytics and trend analysis          │  
└──────────────────────────────────────────────────────────────┘

# **3\. Data Models**

## **3.1 Track Identifier (ContentID)**

Since tracks may not have industry-standard ISRCs, the system generates unique identifiers using audio fingerprinting combined with metadata hashing. This allows identification of the same track across different file copies.

### **3.1.1 ContentID Generation**

ContentID \= SHA256(  
    audio\_fingerprint \+     // First 30 seconds, 8kHz mono  
    normalized\_title \+      // Lowercase, stripped punctuation  
    normalized\_artist \+     // Lowercase, stripped punctuation  
    duration\_bucket         // Rounded to nearest 5 seconds  
)\[0:32\]  // First 32 characters

The audio fingerprint uses a lightweight algorithm suitable for low-spec devices: Chromaprint (open source, used by MusicBrainz) or a simplified spectral hash. The fingerprint is generated once when the track is first added to the library.

## **3.2 Transfer Metadata Record**

The system tracks when music files are transferred between devices via various methods (USB OTG, Bluetooth, etc.) without facilitating the actual file sharing. This provides valuable distribution analytics.

| Field | Type | Description |
| :---- | :---- | :---- |
| transfer\_id | UUID | Unique identifier for this transfer event |
| content\_id | String(32) | Track identifier being transferred |
| source\_device\_id | String(64) | Device initiating the transfer |
| target\_device\_id | String(64) | Device receiving the transfer (if known) |
| transfer\_method | Enum | USB\_OTG, BLUETOOTH, WIFI\_DIRECT, UNKNOWN |
| timestamp | Unix epoch | When transfer was initiated |
| file\_size | Integer | Size of transferred file in bytes |
| transfer\_status | Enum | INITIATED, COMPLETED, FAILED, CANCELLED |
| transfer\_duration | Integer | Time taken for transfer (seconds, if completed) |
| device\_signature | String(128) | Cryptographic signature for verification |

### **3.2.1 Transfer Detection Methods**

| Method | Detection Approach | Implementation |
| :---- | :---- | :---- |
| USB OTG | Monitor USB device connections + file system events | Android: UsbManager + FileObserver |
| Bluetooth | Monitor Bluetooth file transfer protocols (OBEX) | Android: BluetoothAdapter state changes |
| WiFi Direct | Monitor WiFi Direct connections + file operations | Android: WifiP2pManager |
| Manual Entry | User-initiated transfer logging | UI button "Log Transfer" |

## **3.3 Playback Record**

| Field | Type | Description |
| :---- | :---- | :---- |
| record\_id | UUID | Unique identifier for this playback event |
| content\_id | String(32) | Track identifier (see 3.1) |
| device\_id | String(64) | Anonymized device fingerprint |
| timestamp | Unix epoch | When playback started |
| duration\_played | Integer | Seconds actually listened |
| track\_duration | Integer | Total track length in seconds |
| play\_percentage | Float | Percentage of track listened (0.0-1.0) |
| skip\_count | Integer | Number of times user skipped within track |
| repeat\_flag | Boolean | Was track played on repeat |
| source\_type | Enum | LOCAL\_FILE, USB\_TRANSFER, BLUETOOTH\_TRANSFER, WIFI\_TRANSFER, MANUAL\_IMPORT |
| device\_signature | String(128) | Cryptographic signature for verification |

## **3.4 Track Metadata Record**

Stored once per unique track, synchronized separately from playback records to reduce data transfer.

| Field | Type | Description |
| :---- | :---- | :---- |
| content\_id | String(32) | Primary key |
| title | String(255) | Track title from file metadata |
| artist | String(255) | Artist name from file metadata |
| album | String(255) | Album name (if available) |
| duration | Integer | Track duration in seconds |
| audio\_fingerprint | Bytes | Compact audio fingerprint for matching |
| first\_seen | Unix epoch | When track was first encountered in system |

# **4\. Device Identification & Verification**

## **4.1 Device Fingerprinting**

Each device generates a stable, anonymized identifier on first app launch. This ID is used for deduplication and fraud detection without tracking individual users.

### **4.1.1 Android Device ID**

device\_id \= SHA256(  
    android\_id \+           // Settings.Secure.ANDROID\_ID  
    app\_install\_uuid \+     // Generated on first install  
    hardware\_serial        // Build.SERIAL (if available)  
)\[0:64\]

### **4.1.2 Windows Device ID**

device\_id \= SHA256(  
    machine\_guid \+         // HKLM\\SOFTWARE\\Microsoft\\Cryptography  
    app\_install\_uuid \+     // Generated on first install  
    disk\_serial            // Primary drive serial  
)\[0:64\]

## **4.2 Record Signing**

Each playback record is signed using a device-specific Ed25519 key pair generated on first install. The private key is stored in secure storage (Android Keystore / Windows DPAPI).

signature \= Ed25519.sign(  
    private\_key,  
    record\_id \+ content\_id \+ device\_id \+ timestamp \+ duration\_played  
)

The public key is registered with the aggregation node on first sync, creating a chain of trust that can be verified all the way to the publishing company.

# **5\. Bluetooth Mesh Protocol**

The system adapts bitchat's Bluetooth Low Energy mesh networking for playback data synchronization. This enables device-to-device data transfer without internet connectivity.

## **5.1 Protocol Adaptation from bitchat**

| bitchat Feature | Analytics System Use | Modifications |
| :---- | :---- | :---- |
| BLE mesh networking | Data sync between devices | Same protocol, different payload |
| Peer discovery | Find nearby players/aggregators | Add role advertisement (player vs hub) |
| Message relay (multi-hop) | Reach aggregators via other devices | Prioritize aggregator routing |
| Store and forward | Queue data until aggregator found | Extended retention (days vs hours) |
| AES-256 encryption | Protect data in transit | No changes needed |
| LZ4 compression | Reduce bandwidth for records | Apply to record batches |

## **5.2 Message Types**

| Type ID | Name | Purpose |
| :---- | :---- | :---- |
| 0x20 | PLAYBACK\_BATCH | Batch of playback records (up to 100 records per message) |
| 0x21 | TRACK\_META | Track metadata for new content\_ids |
| 0x22 | TRANSFER\_BATCH | Batch of transfer metadata records |
| 0x23 | DEVICE\_REGISTER | Register device public key with aggregator |
| 0x24 | SYNC\_ACK | Acknowledgment with list of received record\_ids |
| 0x25 | AGGREGATOR\_BEACON | Aggregator advertising its presence and capacity |

## **5.3 Sync Protocol Flow**

1\. Player device discovers nearby peers via BLE scan  
2\. If AGGREGATOR\_BEACON received:  
   a. Send DEVICE\_REGISTER (if first contact)  
   b. Send PLAYBACK\_BATCH messages  
   c. Wait for SYNC\_ACK  
   d. Mark acknowledged records as synced  
3\. If only other players found:  
   a. Exchange PLAYBACK\_BATCH and TRANSFER\_BATCH via mesh relay  
   b. Records propagate toward aggregators  
4\. Retry unacknowledged records on next sync opportunity

# **6\. Aggregation Nodes (Burning Centers)**

Aggregation nodes are trusted devices at burning centers or distribution points. They collect data from consumer devices and batch-upload to the publishing company when internet is available.

## **6.1 Aggregator Requirements**

| Requirement | Specification |
| :---- | :---- |
| Hardware | Android 8.0+ phone/tablet OR Windows 10+ PC with Bluetooth LE |
| Storage | Minimum 2GB free (supports \~5 million playback records) |
| Registration | Must register with publishing company (receives aggregator certificate) |
| Internet | Periodic access required (weekly recommended, monthly acceptable) |
| Security | Tamper-detection via certificate chain and signed batches |

## **6.2 Aggregator Functions**

* Broadcast AGGREGATOR\_BEACON to attract nearby player devices

* Receive and validate playback records (verify signatures)

* Deduplicate records (same record\_id from multiple relay paths)

* Store records in local SQLite database

* Batch and compress records for upload

* Sign batches with aggregator certificate

* Upload to publishing company API when internet available

* Receive acknowledgments and purge uploaded records

## **6.3 Deduplication Strategy**

Records may arrive at an aggregator via multiple paths (directly from device, relayed through other devices, or from another aggregator). Deduplication uses record\_id as primary key:

ON RECEIVE playback\_record:  
    IF record\_id EXISTS in local\_db:  
        SKIP (already have this record)  
    ELSE IF signature\_valid(record):  
        INSERT into local\_db  
        FORWARD to other aggregators (if connected)

# **7\. Verification & Anti-Fraud**

The system must prevent fraudulent inflation of play counts, which directly impacts royalty calculations. Multiple layers of verification are employed.

## **7.1 Device-Level Verification**

* Cryptographic signatures: Each record signed with device-specific Ed25519 key

* Key attestation: Android Keystore / Windows TPM backing where available

* Monotonic timestamps: Records must have increasing timestamps per device

* Duration bounds: duration\_played cannot exceed track\_duration

## **7.2 Aggregator-Level Verification**

* Signature validation: Verify all record signatures before storage

* Rate limiting: Flag devices submitting \>500 plays/day for review

* Pattern detection: Identify bot-like behavior (perfect loops, zero skips)

* Cross-device correlation: Same track playing simultaneously on multiple devices

## **7.3 Publisher-Level Verification**

* Aggregator authentication: Verify aggregator certificate chain

* Batch integrity: Verify batch signatures

* Cross-aggregator deduplication: Same record\_id from different aggregators

* Statistical analysis: Detect anomalies in regional playback patterns

* Audit trail: Full history of all records with provenance

## **7.4 Fraud Indicators**

| Indicator | Detection Method | Action |
| :---- | :---- | :---- |
| Excessive play rate | \>500 plays/day from single device | Flag for manual review |
| Perfect loops | 100% completion, zero skips, repeating | Weight reduction (0.5x) |
| Time travel | Non-monotonic timestamps from device | Reject affected records |
| Invalid signature | Signature verification fails | Reject record |
| Unknown device | Public key not registered | Queue for registration |
| Duplicate submission | Same record\_id already processed | Ignore duplicate |

# **8\. Royalty Calculation**

The publishing company uses aggregated playback data to calculate royalties. The formula accounts for actual listening behavior rather than simple play counts.

## **8.1 Play Value Calculation**

play\_value \= base\_value  
           × completion\_factor  
           × fraud\_weight  
           × regional\_factor

WHERE:  
  completion\_factor \= MIN(1.0, duration\_played / track\_duration)  
  fraud\_weight \= 1.0 (normal) | 0.5 (flagged) | 0.0 (rejected)  
  regional\_factor \= configured per aggregator location

## **8.2 Minimum Play Threshold**

Following industry standards, a play only counts toward royalties if the user listened to at least 30 seconds OR 50% of the track (whichever is shorter). This prevents accidental skips from counting.

## **8.3 Aggregation Period**

Royalties are calculated monthly based on all verified records received by the end of the calculation period. Given the offline nature of the system, records may arrive weeks or months after the actual playback. The system uses the playback timestamp (not receipt timestamp) for period assignment, with a configurable late-arrival window.

# **9\. Technical Implementation**

## **9.1 Android Player App**

| Component | Technology |
| :---- | :---- |
| Language | Kotlin (minimum Android 8.0 / API 26\) |
| UI Framework | Jetpack Compose (or XML for older devices) |
| Audio Playback | ExoPlayer (handles various formats, low memory) |
| Local Database | Room (SQLite wrapper) |
| Bluetooth Mesh | Adapted from bitchat (Nordic BLE library) |
| Cryptography | BouncyCastle (Ed25519, AES-GCM) |
| Audio Fingerprint | Chromaprint (via JNI) or simplified Java implementation |
| Key Storage | Android Keystore |

## **9.2 Windows Desktop App**

| Component | Technology |
| :---- | :---- |
| Framework | Electron (cross-platform) or .NET MAUI (native) |
| Audio Playback | NAudio or system MediaPlayer |
| Local Database | SQLite (same schema as Android) |
| Bluetooth | Windows.Devices.Bluetooth (UWP) or 32feet.NET |
| Cryptography | libsodium or BouncyCastle |
| Audio Fingerprint | Chromaprint (native library) |
| Key Storage | Windows DPAPI |

## **9.3 Low-Spec Device Optimizations**

* Lazy fingerprinting: Generate audio fingerprint only on first play, not library scan

* Batch database writes: Queue records in memory, flush every 30 seconds

* Compressed storage: LZ4 compress record batches \>10 records

* Adaptive BLE: Reduce scan frequency on low battery

* Memory limits: Cap in-memory record queue at 1000 entries

* Background sync: Use Android WorkManager / Windows Background Tasks

# **10\. Storage Requirements**

## **10.1 Per-Record Storage**

| Data Type | Size |
| :---- | :---- |
| Playback record (uncompressed) | \~350 bytes |
| Playback record (LZ4 compressed batch) | \~180 bytes average |
| Track metadata (per unique track) | \~500 bytes |
| Audio fingerprint | \~128 bytes |

## **10.2 Typical Device Storage**

Assuming average user plays 50 tracks/day with 30-day retention before sync:

Playback records: 50 × 30 × 180 bytes \= 270 KB  
Track metadata:   500 unique tracks × 500 bytes \= 250 KB  
Total per user:   \~520 KB (well under 1 MB)

Aggregator nodes storing 10,000 devices × 30 days \= \~5 GB, which fits comfortably on low-end devices with 2GB+ free storage.

# **11\. Publisher API Specification**

Aggregators upload data to the publishing company via a REST API when internet is available.

## **11.1 Endpoints**

| Method | Endpoint | Purpose |
| :---- | :---- | :---- |
| POST | /api/v1/aggregators/register | Register new aggregator, receive certificate |
| POST | /api/v1/batches | Upload signed batch of playback records |
| POST | /api/v1/tracks | Upload new track metadata |
| GET | /api/v1/batches/{id}/status | Check batch processing status |
| GET | /api/v1/aggregators/{id}/stats | Get aggregator statistics |

## **11.2 Batch Upload Format**

{  
  "aggregator\_id": "agg\_abc123",  
  "batch\_id": "batch\_xyz789",  
  "timestamp": 1706745600,  
  "record\_count": 1547,  
  "records": \[/\* compressed, base64-encoded \*/\],  
  "signature": "Ed25519 signature of batch contents"  
}

# **12\. Implementation Roadmap**

## **Phase 1: Foundation (Months 1-2)**

* Fork bitchat-android, extract Bluetooth mesh networking layer

* Implement basic Android music player with local playback logging

* Implement ContentID generation (audio fingerprinting)

* Create local SQLite schema for records and metadata

* Basic device identification and key generation

## **Phase 2: Mesh Sync (Months 3-4)**

* Adapt bitchat protocol for playback record messages

* Implement device-to-device sync

* Build aggregator node application

* Implement record signing and verification

* Test multi-hop relay scenarios

## **Phase 3: Publisher Integration (Months 5-6)**

* Build publisher-side API and database

* Implement aggregator registration and certificate system

* Build batch upload and verification pipeline

* Implement deduplication across aggregators

* Basic fraud detection rules

## **Phase 4: Windows & Polish (Months 7-8)**

* Port player application to Windows

* Ensure cross-platform Bluetooth compatibility

* Optimize for low-spec devices

* Build royalty calculation module

* Admin dashboard for publishers

## **Phase 5: Pilot & Iterate (Months 9-12)**

* Deploy pilot with 2-3 burning centers

* Monitor data quality and fraud patterns

* Iterate on fraud detection

* Scale to additional regions

* Integrate with existing royalty payment systems

# **Appendix A: bitchat Code Reuse**

The following bitchat-android components can be directly reused or adapted:

| bitchat File | Reuse Level | Notes |
| :---- | :---- | :---- |
| BluetoothMeshService.kt | High (adapt) | Core mesh networking |
| BinaryProtocol.kt | Medium (extend) | Add new message types |
| EncryptionService.kt | High (reuse) | Ed25519, AES-GCM |
| ChatViewModel.kt | Low (reference) | Replace with analytics model |
| MainActivity.kt | Medium (adapt) | Permission handling |

Repository: https://github.com/permissionlesstech/bitchat-android

License: MIT (allows commercial use and modification)

*— End of Specification —*