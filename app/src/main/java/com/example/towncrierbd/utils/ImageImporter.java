package com.example.towncrierbd.utils;
import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import java.io.InputStream;
import java.io.IOException;
/** Bound memory usage and honor camera orientation for both post and profile photos. */
public final class ImageImporter {
    public static Bitmap decode(Context context,Uri uri) throws IOException {
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0||options.outWidth>30000||options.outHeight>30000)throw new IOException("Unsupported image");
        options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1600)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap image;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){image=BitmapFactory.decodeStream(in,null,options);}
        if(image==null)throw new IOException("Unable to read image");
        int orientation=1;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){orientation=new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
        Matrix matrix=new Matrix();
        switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;default:return image;}
        Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),matrix,true);if(rotated!=image)image.recycle();return rotated;
    }
}
