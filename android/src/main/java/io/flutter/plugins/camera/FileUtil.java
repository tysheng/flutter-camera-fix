package io.flutter.plugins.camera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Created by tysheng
 * Date: 2019-11-27 17:25.
 * Email: tyshengsx@gmail.com
 */
public class FileUtil {


    public static void copy(File src, File dst, boolean deleteSrc) throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;

        try {
            inputChannel = new FileInputStream(src).getChannel();
            outputChannel = new FileOutputStream(dst).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            if (inputChannel != null) {
                inputChannel.close();
            }

            if (outputChannel != null) {
                outputChannel.close();
            }

            if (deleteSrc) {
                src.delete();
            }
        }
    }
}
