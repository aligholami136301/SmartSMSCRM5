package ir.smartsmscrm.v4.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Only local password-encrypted exports. Never store password or plaintext backup on disk. */
object SecureBackup {
    private val magic="SMSCRM4\n".toByteArray(Charsets.UTF_8)
    private fun key(password:CharArray,salt:ByteArray): SecretKeySpec {
        val spec=PBEKeySpec(password,salt,200_000,256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES")
        } finally {spec.clearPassword()}
    }
    fun encrypt(plaintext:String,password:String):ByteArray {
        require(password.length>=10) {"رمز باید حداقل ۱۰ کاراکتر باشد"}
        val salt=ByteArray(16);val iv=ByteArray(12)
        SecureRandom().nextBytes(salt);SecureRandom().nextBytes(iv)
        val zipped=ByteArrayOutputStream()
        GZIPOutputStream(zipped).use {it.write(plaintext.toByteArray(Charsets.UTF_8))}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key(password.toCharArray(),salt),GCMParameterSpec(128,iv))
        return magic+salt+iv+cipher.doFinal(zipped.toByteArray())
    }
    fun decrypt(file:ByteArray,password:String):String {
        require(file.size>magic.size+16+12+16 && file.take(magic.size).toByteArray().contentEquals(magic)) {"فرمت فایل پشتیبان صحیح نیست"}
        val salt=file.copyOfRange(magic.size,magic.size+16)
        val iv=file.copyOfRange(magic.size+16,magic.size+28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(password.toCharArray(),salt),GCMParameterSpec(128,iv))
        val zipped=cipher.doFinal(file.copyOfRange(magic.size+28,file.size))
        return GZIPInputStream(ByteArrayInputStream(zipped)).bufferedReader(Charsets.UTF_8).use {it.readText()}
    }
}
