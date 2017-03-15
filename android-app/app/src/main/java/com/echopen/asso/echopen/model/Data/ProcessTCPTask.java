package com.echopen.asso.echopen.model.Data;

/**
 * This class takes as formal input the pixels data coming from hardware, and then, after processing it, refreshes the UI.
 * More precisely, it gets the data from TCP protocol and send it to ScanConversion algorithm class.
 * After the data is processed, it gets it back and displays the final image through the main UI thread.
 */

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Debug;
import android.renderscript.RenderScript;

import com.echopen.asso.echopen.filters.EnvelopDetectionFilter;
import com.echopen.asso.echopen.filters.RenderingContext;
import com.echopen.asso.echopen.preproc.ScanConversion;
import com.echopen.asso.echopen.ui.MainActionController;
import com.echopen.asso.echopen.ui.RenderingContextController;
import com.echopen.asso.echopen.utils.Constants;
import com.parse.gdata.Preconditions;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.net.Socket;

import java.util.Arrays;

import static com.echopen.asso.echopen.utils.Constants.PreProcParam.TCP_IMG_DATA;
import static com.echopen.asso.echopen.utils.Constants.PreProcParam.TCP_NUM_SAMPLES;

public class ProcessTCPTask extends AbstractDataTask {

    private final String TAG = this.getClass().getSimpleName();
    private Socket s;

    private final String ip;

    private final int port;

    private DataInputStream dataInputStream;

    private static int offset_header = 6;

    private RenderScript renderScript;

    private Data data;

    public ProcessTCPTask(Activity activity, MainActionController mainActionController, RenderingContextController iRenderingContextController, ScanConversion scanConversion, String ip, int port) {
        super(activity, mainActionController, scanConversion, iRenderingContextController);
        this.ip = ip;
        this.port = port;
    }

    protected Void doInBackground(Void... Voids) {
        InputStream stream;
        int rows = Constants.PreProcParam.NUM_SAMPLES;
        //int cols = Constants.PreProcParam.NUM_IMG_DATA;

        try {
            //unplugging the stream operation
            //s = new Socket(ip, port);
            //stream = s.getInputStream();
            //checkStreamIsNotEmpty(stream);

            byte[] message;
            Integer[] lRawImageData;

            // get config - for the moment not implemented
            //getDeviceConfig(stream);

            while (true) {
                try {
                    RenderingContext lCurrentRenderingContext = mRenderingContextController.getCurrentRenderingContext();;

                    // getting data from local
                    lRawImageData = getRawImageDataFromLocal();
                    //lRawImageData = getRawImageData(stream);
                    scanconversion.setData(data);

                    try {
                        refreshUI(scanconversion);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    //Debug.startMethodTracing("trace_app");
                    //rawDataPipeline(ScanConversion.getInstance(), lCurrentRenderingContext, lRawImageData);
                    //Debug.stopMethodTracing();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Integer[] getRawImageDataFromLocal() {
        AssetManager assetManager = activity.getResources().getAssets();
        InputStream inputStream = null;
        try {
            inputStream = assetManager.open("data/raw_data/data_simu.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
        InputStreamReader isReader = new InputStreamReader(inputStream);
        data = new Data(isReader);
        int[] envelopeCropData = new int[1024*64];
        Integer[] envelopeData = data.getEnvelopeIntegerData();

        for (int i = 0; i < 64; i++) {
            for (int j = 0; j < 1024; j++) {
                envelopeCropData[ i*1024 + j ] = envelopeData[i*1478 + j].intValue();
            }
        }
        data.setEnvelopeData(envelopeCropData);
        launchScanConversion(data);
        return data.getEnvelopeIntegerData();
    }

    private void launchScanConversion(Data data) {
        scanconversion = new ScanConversion(data);
        ScanConversion.compute_tables();
        createScript();
    }

    private void createScript() {
        renderScript = RenderScript.create(activity);
        scanconversion.setRenderScript(renderScript);
    }

    private Integer[] getRawImageData(InputStream iStream) throws IOException{
        // device config is temporary stored in the app
        Integer lNbSamplesPerLine = Constants.PreProcParam.NUM_SAMPLES_PER_LINE;
        Integer lNbLinesPerImage = Constants.PreProcParam.NUM_LINES_PER_IMAGE;
        Integer lSampleSize = Constants.PreProcParam.NUM_BYTES_PER_SAMPLE;

        Integer[] lRawImageData = new Integer[lNbLinesPerImage * lNbSamplesPerLine];
        Arrays.fill(lRawImageData, 0, lNbLinesPerImage * lNbSamplesPerLine, 0);
        // 2 bytes for line index + 2 bytes * nb of samples per line
        byte[] lLineData = new byte[lNbSamplesPerLine * lSampleSize + lSampleSize + offset_header];
        Integer[] lAliasedLineData;
        Integer lLineOffset;

        dataInputStream = new DataInputStream(iStream);


        Integer lLineNumber = 0;

        for(Integer i = 0; i < lNbLinesPerImage; i++){
            dataInputStream.readFully(lLineData);

            lLineNumber = (lLineData[1+offset_header] << 8) | (lLineData[0+offset_header] & 0x00ff);

            lAliasedLineData = new Integer[lNbSamplesPerLine];
            for(Integer j = 1; j <= lNbSamplesPerLine; j++)
            {
                lAliasedLineData[(j- 1)] = (lLineData[2*j + 1+offset_header] << 8) | (lLineData[2*j+offset_header] & 0x00ff);
            }
            lLineOffset = (lLineNumber - 1) * lNbSamplesPerLine;
            System.arraycopy(lAliasedLineData, 0, lRawImageData, lLineOffset , lAliasedLineData.length);
            offset_header = 0;
        }
        return lRawImageData;
    }

    private void getDeviceConfig(InputStream iStream) throws IOException{
        byte[] lConfig = new byte[6];

        // TODO: implement Device configuration reading to replace app hard coded values
        dataInputStream = new DataInputStream(iStream);
        dataInputStream.readFully(lConfig);

    }


    private byte[] deepInsidePacket(int len, InputStream stream) throws IOException {
        byte[] buffer = new byte[len];
        //int rows = Constants.PreProcParam.NUM_SAMPLES;
        int start_line = Constants.PreProcParam.NUM_IMG_DATA;

        dataInputStream = new DataInputStream(stream);

        while(buffer[0] != start_line/2 + 1){
            dataInputStream.readFully(buffer);
        }
        return getDeepInsidePacket(buffer, stream);
    }

    private byte[] getDeepInsidePacket(byte[] buffer, InputStream stream) throws IOException {
        int rows = Constants.PreProcParam.NUM_SAMPLES;
        int cols = Constants.PreProcParam.NUM_IMG_DATA;
        byte[] tmpBuffer = new byte[rows+1];
        byte[] finalBuffer = new byte[cols * rows];
        int count_lines = 0;
        int half_count_lines = 0;

        System.arraycopy(buffer, 1, finalBuffer, cols/2 * rows, rows);
        dataInputStream = new DataInputStream(stream);

        while(true) {
            dataInputStream.readFully(tmpBuffer);
            System.arraycopy(tmpBuffer, 1, finalBuffer, (cols / 2 + 1 + count_lines) * rows, rows);
            count_lines++;
            if (count_lines == 31) {
                while (true) {
                    dataInputStream.readFully(tmpBuffer);
                    System.arraycopy(tmpBuffer, 1, finalBuffer, (31 - half_count_lines) * rows, rows);
                    half_count_lines++;
                    if (half_count_lines == 32) {
                        break;
                    }
                }
                break;
            }
        }
        return finalBuffer;
    }

    /**
     * Permits to check the InputStream is empty or not
     * Please note that only the returned InputStream must be consummed.
     *
     * see:
     * http://stackoverflow.com/questions/1524299/how-can-i-check-if-an-inputstream-is-empty-without-reading-from-it
     *
     * @param inputStream
     * @return
     */
    private static InputStream checkStreamIsNotEmpty(InputStream inputStream) throws IOException {
        Preconditions.checkArgument(inputStream != null, "The InputStream is mandatory");
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
        int b;
        b = pushbackInputStream.read();
        if ( b == -1 ) {
            pushbackInputStream = null;
            checkStreamIsNotEmpty(inputStream);
        }
        pushbackInputStream.unread(b);
        return pushbackInputStream;
    }
}
