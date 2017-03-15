package com.echopen.asso.echopen.model.Data;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;

import com.echopen.asso.echopen.filters.EnvelopDetectionFilter;
import com.echopen.asso.echopen.filters.IntensityUniformGainFilter;
import com.echopen.asso.echopen.filters.IntensityToRGBFilter;
import com.echopen.asso.echopen.filters.RenderingContext;
import com.echopen.asso.echopen.preproc.ScanConversion;
import com.echopen.asso.echopen.ui.MainActionController;
import com.echopen.asso.echopen.ui.RenderingContextController;
import com.echopen.asso.echopen.utils.Constants;

import java.util.Arrays;

/**
 * Core class of data collecting routes. Whether the protocol is chosen to be TCP, UDP or fetching data from local,
 * the dedicated classes inherits from @this
 */
abstract public class AbstractDataTask extends AsyncTask<Void, Void, Void> {

    private final String TAG = this.getClass().getSimpleName();

    protected Activity activity;

    protected ScanConversion scanconversion;

    protected MainActionController mainActionController;

    protected RenderingContextController mRenderingContextController;

    public AbstractDataTask(Activity activity, MainActionController mainActionController, ScanConversion scanConversion, RenderingContextController iRenderingContextController) {
        this.scanconversion = scanConversion;
        this.activity = activity;
        this.mainActionController = mainActionController;

        this.mRenderingContextController = iRenderingContextController;
    }

    protected void refreshUI(ScanConversion scanconversion) {
        int[] scannedArray = scanconversion.getDataFromInterpolation();

        /*IntensityUniformGainFilter lIntensityGainFilter = new IntensityUniformGainFilter();
        lIntensityGainFilter.setImageInput(scannedArray, scannedArray.length);
        lIntensityGainFilter.applyFilter(iCurrentRenderingContext.getIntensityGain());
        int[] scannedGainArray = lIntensityGainFilter.getImageOutput();

        IntensityToRGBFilter lIntensityToRGBFilter = new IntensityToRGBFilter();
        lIntensityToRGBFilter.setImageInput(scannedGainArray, scannedGainArray.length);
        lIntensityToRGBFilter.applyFilter(iCurrentRenderingContext.getLookUpTable());*/
        int colors[] =  new int[scannedArray.length];

        //Arrays.fill(colors, 0, 4*scannedArray.length, Color.WHITE);
        for (int i = 0; i < scannedArray.length; i++) {
            colors[i] = scannedArray[i] | scannedArray[i] << 8 | scannedArray[i] << 16 | 0xFF000000;
        }
        final Bitmap bitmap = Bitmap.createBitmap(colors, 512*Constants.PreProcParam.SCALE_IMG_FACTOR, 512/Constants.PreProcParam.SCALE_IMG_FACTOR, Bitmap.Config.ARGB_8888);
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActionController.displayMainFrame(bitmap);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void rawDataPipeline(ScanConversion scanconversion,RenderingContext iCurrentRenderingContext, Integer[] iRawImageData) {

        // envelop detection filter
        EnvelopDetectionFilter lEnvelopDetectionFilter = new EnvelopDetectionFilter();
        lEnvelopDetectionFilter.setImageInput(iRawImageData, Constants.PreProcParam.NUM_SAMPLES_PER_LINE, Constants.PreProcParam.NUM_LINES_PER_IMAGE);
        lEnvelopDetectionFilter.applyFilter();
        Integer[] lEnvelopImageData = lEnvelopDetectionFilter.getImageOutput();

        //TODO: filters has to be improve to support 16bit data values
        /*IntensityUniformGainFilter lIntensityGainFilter = new IntensityUniformGainFilter();
        lIntensityGainFilter.setImageInput(scannedArray, scannedArray.length);
        lIntensityGainFilter.applyFilter(iCurrentRenderingContext.getIntensityGain());
        int[] scannedGainArray = lIntensityGainFilter.getImageOutput();

        IntensityToRGBFilter lIntensityToRGBFilter = new IntensityToRGBFilter();
        lIntensityToRGBFilter.setImageInput(scannedGainArray, scannedGainArray.length);
        lIntensityToRGBFilter.applyFilter(iCurrentRenderingContext.getLookUpTable());
        int colors[] =  lIntensityToRGBFilter.getImageOutput();*/

        // TODO: remove image threshold on 8 bits
        Integer[] lresampledCartesianImage = scanconversion.applyScanConversionFilter(lEnvelopImageData);
        for (int i = 0; i < lresampledCartesianImage.length; i++) {
            if(lresampledCartesianImage[i]>255)
                lresampledCartesianImage[i] = 255;
        }

        int colors[] = new int[Constants.PreProcParam.N_x * Constants.PreProcParam.N_z];
        for(int i = 0; i < lresampledCartesianImage.length; i++){
            colors[i] = lresampledCartesianImage[i] | lresampledCartesianImage[i] << 8 | lresampledCartesianImage[i] << 16 | 0xFF000000;
        }
        // end Remove
 

        final Bitmap bitmap = Bitmap.createBitmap(colors, Constants.PreProcParam.N_x, Constants.PreProcParam.N_z, Bitmap.Config.ARGB_8888);
        try {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mainActionController.displayMainFrame(bitmap);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
