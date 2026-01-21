package com.bitchat.android.music

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.bitchat.android.crypto.EncryptionService
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

/**
 * Device identification service for music analytics
 * Generates stable, anonymized device identifiers and manages signing keys
 * Based on specification section 4 (Device Identification & Verification)
 */
class DeviceIdentificationService(private val context: Context) {
    
    companion object {
        private const val TAG = "DeviceIdentificationService"
        private const val PREFS_NAME = "music_analytics_device"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_INSTALL_UUID = "install_uuid"
        private const val KEY_PRIVATE_KEY = "ed25519_private_key"
        private const val KEY_PUBLIC_KEY = "ed25519_public_key"
    }
    
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    private var _deviceId: String? = null
    private var _keyPair: Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters>? = null
    
    /**
     * Get the stable device ID (64-char anonymized fingerprint)
     * Generated once on first app launch and persisted securely
     */
    fun getDeviceId(): String {
        if (_deviceId == null) {
            _deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: generateDeviceId()
        }
        return _deviceId!!
    }
    
    /**
     * Generate device ID using Android-specific identifiers
     * device_id = SHA256(android_id + app_install_uuid + hardware_serial)[0:64]
     */
    private fun generateDeviceId(): String {
        try {
            // Get or create install UUID
            val installUuid = prefs.getString(KEY_INSTALL_UUID, null) ?: run {
                val uuid = UUID.randomUUID().toString()
                prefs.edit().putString(KEY_INSTALL_UUID, uuid).apply()
                uuid
            }
            
            // Get Android ID (stable per app install)
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
            
            // Get hardware serial (if available)
            val hardwareSerial = try {
                android.os.Build.SERIAL
            } catch (e: Exception) {
                "unknown"
            }
            
            // Combine identifiers
            val combined = androidId + installUuid + hardwareSerial
            
            // Generate SHA256 hash and take first 64 characters
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
            val hexString = hash.joinToString("") { "%02x".format(it) }
            val deviceId = hexString.take(64)
            
            // Store for future use
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
            
            Log.i(TAG, "Generated new device ID: ${deviceId.take(16)}...")
            return deviceId
        } catch (e: Exception) {
            Log.e(TAG, "Error generating device ID", e)
            // Fallback to random UUID
            val fallbackId = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_DEVICE_ID, fallbackId).apply()
            return fallbackId
        }
    }
    
    /**
     * Get or generate Ed25519 key pair for signing playback records
     */
    fun getKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        if (_keyPair == null) {
            _keyPair = loadOrGenerateKeyPair()
        }
        return _keyPair!!
    }
    
    /**
     * Load existing key pair or generate new one
     */
    private fun loadOrGenerateKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val privateKeyBytes = prefs.getString(KEY_PRIVATE_KEY, null)
        val publicKeyBytes = prefs.getString(KEY_PUBLIC_KEY, null)
        
        return if (privateKeyBytes != null && publicKeyBytes != null) {
            try {
                // Load existing keys
                val privateKey = Ed25519PrivateKeyParameters(android.util.Base64.decode(privateKeyBytes, android.util.Base64.DEFAULT))
                val publicKey = Ed25519PublicKeyParameters(android.util.Base64.decode(publicKeyBytes, android.util.Base64.DEFAULT))
                Log.i(TAG, "Loaded existing Ed25519 key pair")
                Pair(privateKey, publicKey)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load existing keys, generating new ones", e)
                generateNewKeyPair()
            }
        } else {
            generateNewKeyPair()
        }
    }
    
    /**
     * Generate new Ed25519 key pair and store securely
     */
    private fun generateNewKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val keyPairGenerator = Ed25519KeyPairGenerator()
        keyPairGenerator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = keyPairGenerator.generateKeyPair()
        
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        
        // Store keys securely
        val privateKeyBase64 = android.util.Base64.encodeToString(privateKey.encoded, android.util.Base64.DEFAULT)
        val publicKeyBase64 = android.util.Base64.encodeToString(publicKey.encoded, android.util.Base64.DEFAULT)
        
        prefs.edit()
            .putString(KEY_PRIVATE_KEY, privateKeyBase64)
            .putString(KEY_PUBLIC_KEY, publicKeyBase64)
            .apply()
        
        Log.i(TAG, "Generated new Ed25519 key pair")
        return Pair(privateKey, publicKey)
    }
    
    /**
     * Sign playback record data with device private key
     */
    fun signPlaybackRecord(record: com.bitchat.android.music.model.PlaybackRecord): ByteArray {
        val (privateKey, _) = getKeyPair()
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        
        val dataToSign = record.getDataForSigning()
        signer.update(dataToSign, 0, dataToSign.size)
        
        return signer.generateSignature()
    }
    
    /**
     * Sign transfer record data with device private key
     */
    fun signTransferRecord(record: com.bitchat.android.music.model.TransferRecord): ByteArray {
        val (privateKey, _) = getKeyPair()
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        
        val dataToSign = record.getSigningData()
        signer.update(dataToSign, 0, dataToSign.size)
        
        return signer.generateSignature()
    }
    
    /**
     * Verify playback record signature
     */
    fun verifyPlaybackRecord(record: com.bitchat.android.music.model.PlaybackRecord, publicKeyBytes: ByteArray): Boolean {
        return try {
            val publicKey = Ed25519PublicKeyParameters(publicKeyBytes)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            
            val dataToVerify = record.getDataForSigning()
            signer.update(dataToVerify, 0, dataToVerify.size)
            
            record.deviceSignature?.let { signature ->
                signer.verifySignature(signature)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signature", e)
            false
        }
    }
    
    /**
     * Verify transfer record signature
     */
    fun verifyTransferRecord(record: com.bitchat.android.music.model.TransferRecord, publicKeyBytes: ByteArray): Boolean {
        return try {
            val publicKey = Ed25519PublicKeyParameters(publicKeyBytes)
            val signer = Ed25519Signer()
            signer.init(false, publicKey)
            
            val dataToVerify = record.getSigningData()
            signer.update(dataToVerify, 0, dataToVerify.size)
            
            record.deviceSignature?.let { signature ->
                signer.verifySignature(signature)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying transfer signature", e)
            false
        }
    }
    
    /**
     * Get public key bytes for registration with aggregators
     */
    fun getPublicKeyBytes(): ByteArray {
        val (_, publicKey) = getKeyPair()
        return publicKey.encoded
    }
    
    /**
     * Get public key as hex string for display/transmission
     */
    fun getPublicKeyHex(): String {
        return getPublicKeyBytes().joinToString("") { "%02x".format(it) }
    }
}