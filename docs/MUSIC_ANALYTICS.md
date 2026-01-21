# Music Analytics & Sharing Integration

This document describes the implementation of the Offline Music Analytics system with integrated music sharing capabilities in the bitchat-android application.

## Overview

The music analytics system enables decentralized collection of music playbook data for royalty calculations in markets with limited internet infrastructure. It now includes comprehensive music sharing functionality that allows users to share music files through the BLE mesh network while tracking sharing analytics for distribution insights.

## Architecture

### Core Components

1. **MusicPlayerService** - Media playback with integrated analytics tracking
2. **PlaybackAnalyticsTracker** - Local storage and batch management of playback and sharing records
3. **MusicAnalyticsMeshSync** - BLE mesh synchronization of analytics data
4. **MusicSharingService** - File sharing through mesh network with transfer management
5. **DeviceIdentificationService** - Device fingerprinting and cryptographic signing
6. **ContentIdGenerator** - Unique track identification using audio fingerprinting

### Data Models

- **PlaybackRecord** - Individual playback event with verification data
- **SharingRecord** - Music file sharing event with transfer details
- **TrackMetadata** - Track information (title, artist, duration, fingerprint)
- **Music Analytics Messages** - BLE mesh protocol extensions for data sync
- **Music Sharing Messages** - BLE mesh protocol extensions for file sharing

### UI Components

- **MusicPlayerScreen** - Main player interface with analytics dashboard and sharing controls
- **MusicSharingScreen** - Dedicated sharing interface with transfer management
- **AnalyticsOverviewScreen** - Detailed statistics and device management
- **FilePicker** - Music file selection and library browsing

## Key Features

### Playback Tracking
- Accurate listening time measurement
- Skip count and repeat detection
- Industry-standard royalty qualification (30s or 50% rule)
- Cryptographic signing for fraud prevention

### Content Identification
- Audio fingerprinting for track matching
- Metadata normalization for consistent identification
- Handles tracks without industry ISRCs

### Mesh Synchronization
- Device-to-device playback data sharing
- Aggregator node discovery and sync
- Store-and-forward for offline operation
- Deduplication across multiple sync paths

### Security & Verification
- Ed25519 device-specific signing keys
- Tamper-resistant playback records
- Device fingerprinting without user tracking
- Multi-layer fraud detection

## Usage

### Basic Music Playback
1. Navigate to the Music tab in the app
2. Use "Browse Music Files" to select audio files
3. Play music normally - analytics are tracked automatically
4. View real-time statistics in the Analytics tab

### Aggregator Mode (Burning Centers)
1. Go to Analytics tab
2. Click "Start Aggregator Mode"
3. Device will advertise as collection point
4. Nearby devices will sync their data automatically

### Data Synchronization
- Automatic sync every 30 seconds when aggregators are discovered
- Manual sync available via "Sync Now" button
- Background sync continues when app is backgrounded
- Data persists locally until successfully synced

## Technical Implementation

### Database Schema
```sql
-- Playback records with sync status
CREATE TABLE playback_records (
    recordId TEXT PRIMARY KEY,
    contentId TEXT NOT NULL,
    deviceId TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    durationPlayed INTEGER NOT NULL,
    trackDuration INTEGER NOT NULL,
    playPercentage REAL NOT NULL,
    skipCount INTEGER DEFAULT 0,
    repeatFlag INTEGER DEFAULT 0,
    sourceType TEXT NOT NULL,
    deviceSignature BLOB,
    isSynced INTEGER DEFAULT 0,
    syncedAt INTEGER
);

-- Track metadata
CREATE TABLE track_metadata (
    contentId TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    artist TEXT NOT NULL,
    album TEXT,
    duration INTEGER NOT NULL,
    audioFingerprint BLOB NOT NULL,
    firstSeen INTEGER NOT NULL,
    isSynced INTEGER DEFAULT 0,
    syncedAt INTEGER
);
```

### BLE Protocol Extensions
New message types added to bitchat's binary protocol:
- `PLAYBACK_BATCH` (0x30) - Batch of playback records
- `TRACK_META` (0x31) - Track metadata
- `DEVICE_REGISTER` (0x32) - Device public key registration
- `SYNC_ACK` (0x33) - Acknowledgment with received record IDs
- `AGGREGATOR_BEACON` (0x34) - Aggregator presence advertisement

### Content ID Generation
```kotlin
ContentID = SHA256(
    audio_fingerprint +     // First 30 seconds, simplified spectral hash
    normalized_title +      // Lowercase, stripped punctuation
    normalized_artist +     // Lowercase, stripped punctuation
    duration_bucket         // Rounded to nearest 5 seconds
)[0:32]  // First 32 characters
```

### Device Identification
```kotlin
device_id = SHA256(
    android_id +           // Settings.Secure.ANDROID_ID
    app_install_uuid +     // Generated on first install
    hardware_serial        // Build.SERIAL (if available)
)[0:64]
```

## Integration with Bitchat

The music analytics system is designed to coexist with bitchat's existing functionality:

- **Shared BLE Mesh**: Uses same Bluetooth infrastructure for data sync
- **Shared Crypto**: Leverages existing EncryptionService for key management
- **Shared UI**: Integrated via bottom navigation tabs
- **Shared Services**: Uses MeshForegroundService for background operation

## Configuration

### Permissions Required
- `BLUETOOTH_SCAN` - Device discovery
- `BLUETOOTH_CONNECT` - Device connections
- `BLUETOOTH_ADVERTISE` - Aggregator beacons
- `READ_EXTERNAL_STORAGE` - Music file access
- `RECORD_AUDIO` - Audio fingerprinting (future enhancement)

### Storage Requirements
- ~350 bytes per playback record (uncompressed)
- ~180 bytes per record (compressed batches)
- ~500 bytes per track metadata
- Typical usage: <1MB for 30 days of data per user

## Future Enhancements

1. **Advanced Audio Fingerprinting** - Integration with Chromaprint library
2. **Publisher API Integration** - Direct upload to music publishing companies
3. **Cross-Platform Sync** - Windows desktop player integration
4. **Advanced Fraud Detection** - Machine learning-based anomaly detection
5. **Royalty Calculation** - Built-in payment calculation and distribution

## Testing

Run the music analytics tests:
```bash
./gradlew test --tests "*MusicAnalyticsTest*"
```

## Troubleshooting

### Common Issues

**Music files not loading:**
- Check file permissions and storage access
- Ensure files are in supported formats (MP3, M4A, AAC, OGG, FLAC, WAV)
- Verify file paths are accessible

**Sync not working:**
- Check Bluetooth permissions are granted
- Ensure devices are within BLE range (~10 meters)
- Verify mesh service is running in background

**High battery usage:**
- Reduce sync frequency in debug settings
- Use aggregator mode sparingly
- Ensure proper background optimization settings

### Debug Information

Access debug information in the Analytics tab:
- Device ID and public key fingerprint
- Mesh peer ID for network debugging
- Sync statistics and error logs
- Discovered aggregator information

## Security Considerations

- Device IDs are anonymized and cannot be traced to individuals
- Playback records are cryptographically signed to prevent tampering
- No personal information is collected or transmitted
- All data remains local until explicitly synced to trusted aggregators
- Aggregator certificates provide chain of trust to publishers

## Compliance

The system is designed to comply with:
- GDPR privacy requirements (no personal data collection)
- Music industry royalty calculation standards
- Android security and permission best practices
- Bluetooth Low Energy specification requirements

### Music Sharing
- Broadcast sharing to all nearby devices via BLE mesh
- Direct sharing to specific devices with targeted transfers
- Real-time transfer progress monitoring and cancellation
- Automatic discovery of shared music from nearby devices
- Request/response protocol for accessing shared content
- Chunked file transfer with integrity verification

## Key Features

### Playback Tracking
- Accurate listening time measurement
- Skip count and repeat detection
- Industry-standard royalty qualification (30s or 50% rule)
- Cryptographic signing for fraud prevention

### Music Sharing & Distribution
- **Broadcast Sharing**: Share music files to all nearby devices simultaneously
- **Direct Sharing**: Target specific devices for private file transfers
- **Discovery**: Automatically discover shared music from nearby users
- **Transfer Management**: Real-time progress tracking with pause/resume/cancel
- **Integrity Verification**: MD5 checksums ensure file transfer accuracy
- **Sharing Analytics**: Track sharing events for distribution insights

### Content Identification
- Audio fingerprinting for track matching
- Metadata normalization for consistent identification
- Handles tracks without industry ISRCs
- Duplicate detection across different file sources

### Mesh Synchronization
- Device-to-device playback and sharing data sync
- Aggregator node discovery and automatic sync
- Store-and-forward for offline operation
- Deduplication across multiple sync paths
- Multi-hop relay through mesh network

## Usage

### Basic Music Playback & Sharing
1. Navigate to the Music tab in the app
2. Use "Browse Music Files" to select audio files
3. Play music normally - analytics are tracked automatically
4. Use the Share button to broadcast music to nearby devices
5. View sharing progress in the Sharing tab

### Music Discovery & Acquisition
1. Go to the Sharing tab to see discovered music from nearby devices
2. Browse available tracks with metadata and file sizes
3. Request desired tracks from other users
4. Monitor download progress and manage active transfers
5. Received music appears in your local library

### Advanced Sharing Options
- **Broadcast Mode**: Share to all nearby devices (default)
- **Direct Mode**: Share to specific device IDs
- **Auto-Discovery**: Automatically announce your shared music
- **Transfer Limits**: Configure maximum file sizes and concurrent transfers
- **Sharing Preferences**: Control which files are available for sharing