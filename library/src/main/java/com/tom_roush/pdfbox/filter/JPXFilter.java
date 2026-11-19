/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDJPXColorSpace;

/**
 * Decompress data encoded using the wavelet-based JPEG 2000 standard,
 * reproducing the original data.
 *
 * JP2ForAndroid is an optional runtime dependency. When it is absent, JPX
 * decoding and encoding fail with a clear MissingImageReaderException rather
 * than making the PDFBox library itself depend on a retired artifact repository.
 *
 * @author John Hewson
 * @author Timo Boehme
 */
public final class JPXFilter extends Filter
{
    private static final int CACHE_SIZE = 1024;
    private static final String JP2_DECODER = "com.gemalto.jp2.JP2Decoder";
    private static final String JP2_ENCODER = "com.gemalto.jp2.JP2Encoder";

    /**
     * {@inheritDoc}
     */
    @Override
    public DecodeResult decode(InputStream encoded, OutputStream decoded, COSDictionary
        parameters, int index, DecodeOptions options) throws IOException
    {
        DecodeResult result = new DecodeResult(new COSDictionary());
        result.getParameters().addAll(parameters);
        Bitmap image = readJPX(encoded, options, result);

        int arrLen = image.getWidth() * image.getHeight();
        int[] pixels = new int[arrLen];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

        // Batch writes to decoded; this is substantially faster than writing one byte at a time.
        byte[] buffer = new byte[CACHE_SIZE * 3];
        int pos = 0;

        for (int i = 0; i < arrLen; i++)
        {
            if (pos + 3 >= buffer.length)
            {
                decoded.write(buffer, 0, pos);
                pos = 0;
            }
            int color = pixels[i];
            buffer[pos] = (byte)Color.red(color);
            buffer[pos + 1] = (byte)Color.green(color);
            buffer[pos + 2] = (byte)Color.blue(color);
            pos += 3;
        }
        decoded.write(buffer, 0, pos);
        return result;
    }

    @Override
    public DecodeResult decode(InputStream encoded, OutputStream decoded,
        COSDictionary parameters, int index) throws IOException
    {
        return decode(encoded, decoded, parameters, index, DecodeOptions.DEFAULT);
    }

    // Use reflection so the optional JP2ForAndroid codec is not a compile-time dependency.
    private Bitmap readJPX(InputStream input, DecodeOptions options, DecodeResult result) throws IOException
    {
        final Bitmap image;
        try
        {
            Class<?> decoderClass = Class.forName(JP2_DECODER);
            Object decoder = decoderClass.getConstructor(InputStream.class).newInstance(input);
            image = (Bitmap)decoderClass.getMethod("decode").invoke(decoder);
        }
        catch (ClassNotFoundException ex)
        {
            throw new MissingImageReaderException("Cannot read JPX image: JP2Android is not installed.");
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IOException("Unable to decode JPX image with JP2Android.", ex);
        }

        COSDictionary parameters = result.getParameters();

        // "If the image stream uses the JPXDecode filter, this entry is optional
        // and shall be ignored if present"
        //
        // note that indexed color spaces make the BPC logic tricky, see PDFBOX-2204
//        int bpc = image.getColorModel().getPixelSize() / image.getRaster().getNumBands();
//        parameters.setInt(COSName.BITS_PER_COMPONENT, bpc); TODO: PdfBox-Android

        // "Decode shall be ignored, except in the case where the image is treated as a mask"
        if (!parameters.getBoolean(COSName.IMAGE_MASK, false))
        {
            parameters.setItem(COSName.DECODE, null);
        }

        // override dimensions, see PDFBOX-1735
        parameters.setInt(COSName.WIDTH, image.getWidth());
        parameters.setInt(COSName.HEIGHT, image.getHeight());

        // extract embedded color space
        if (!parameters.containsKey(COSName.COLORSPACE) && Build.VERSION.SDK_INT > Build.VERSION_CODES.O)
        {
            result.setColorSpace(new PDJPXColorSpace(image.getColorSpace()));
        }

        return image;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void encode(InputStream input, OutputStream encoded, COSDictionary parameters)
        throws IOException
    {
        Bitmap bitmap = BitmapFactory.decodeStream(input);
        final byte[] jpeBytes;
        try
        {
            Class<?> encoderClass = Class.forName(JP2_ENCODER);
            Object encoder = encoderClass.getConstructor(Bitmap.class).newInstance(bitmap);
            jpeBytes = (byte[])encoderClass.getMethod("encode").invoke(encoder);
        }
        catch (ClassNotFoundException ex)
        {
            throw new MissingImageReaderException("Cannot write JPX image: JP2Android is not installed.");
        }
        catch (ReflectiveOperationException ex)
        {
            throw new IOException("Unable to encode JPX image with JP2Android.", ex);
        }

        IOUtils.copy(new ByteArrayInputStream(jpeBytes), encoded);
        encoded.flush();
    }
}
