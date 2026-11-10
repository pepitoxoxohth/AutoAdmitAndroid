package com.xoclassic.app;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, URI-granted access to one update APK, never other app files. */
public final class UpdateProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File target(Uri uri)throws FileNotFoundException{if(!"content".equals(uri.getScheme())||!(getContext().getPackageName()+".updates").equals(uri.getAuthority())||!"/update.apk".equals(uri.getPath())||uri.getQuery()!=null)throw new FileNotFoundException();File f=AppUpdater.file(getContext());if(!f.isFile())throw new FileNotFoundException();return f;}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{if(!"r".equals(mode))throw new FileNotFoundException();return ParcelFileDescriptor.open(target(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    public String getType(Uri uri){return "application/vnd.android.package-archive";}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){try{File f=target(uri);MatrixCursor c=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});c.addRow(new Object[]{"AutoAdmit.apk",f.length()});return c;}catch(FileNotFoundException e){return null;}}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
