package com.xoclassic.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** The AES key is non-exportable Android Keystore material; preferences contain ciphertext only. */
public final class SecureVault {
    private static final String ALIAS="xo-classic-vault-v1";
    private final SharedPreferences preferences;
    public SecureVault(Context context) { preferences=context.getSharedPreferences("secure-vault",Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(false).build());
        return generator.generateKey();
    }
    public synchronized void put(String name,String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        String record=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!preferences.edit().putString(name,record).commit())throw new Exception("Secure storage unavailable.");
    }
    public synchronized String get(String name) throws Exception {
        String record=preferences.getString(name,null);if(record==null)return null;
        String[] pieces=record.split(":",2);if(pieces.length!=2)throw new Exception("Saved login could not be unlocked.");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(pieces[0],Base64.NO_WRAP)));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(pieces[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    public synchronized void remove(String name) { preferences.edit().remove(name).commit(); }
}
