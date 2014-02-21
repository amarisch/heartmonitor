package org.opendatakit.sensors.drivers.bt.heart;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ViewActivity extends Activity{
	
	private static final String TAG = "ViewActivity";
	
	private TextView heartRateField, conditionField;
	private int heartRate;
	private char[] condition;
	
	// For AChartEngine main plot
	private static int[] voltageArray;
	private XYSeries voltageSeries;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries;
    private static GraphicalView waveform;

    // plot of the ecg
    private LinearLayout plot;

    private static int index = 0;
    
 	protected void onCreate(Bundle savedInstanceState) {
	 	super.onCreate(savedInstanceState);
		// automatically set orientation to landscape
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.view);

		heartRateField = (TextView) findViewById(R.id.heartRateField);
		conditionField = (TextView) findViewById(R.id.conditionField);       
        
        voltageArray = getIntent().getIntArrayExtra("xyseries");
        heartRate = getIntent().getIntExtra("heartrate", 0);
        condition = getIntent().getCharArrayExtra("condition");
        Log.d(TAG,"item count: " + voltageArray.length);
        
		plot = (LinearLayout) findViewById(R.id.voltage);
		// Initialize Plot setting
		plot_init();
		
		for (int i = 0; i < 7500; i++) {
			voltageSeries.add(i, voltageArray[i]);
		}
		waveform.repaint();
		
		heartRateField.setText(String.valueOf(heartRate));

		if (condition == null) {
			conditionField.setText("detecting");
		} else {
			conditionField.setText(String.valueOf(condition));
		}


 	}
	public void plot_init() {
        dataset = new XYMultipleSeriesDataset();
        renderer = new XYMultipleSeriesRenderer();
        renderer.setApplyBackgroundColor(true);
        renderer.setBackgroundColor(Color.argb(255, 255, 255, 255));
        renderer.setAxisTitleTextSize(16);
        renderer.setChartTitleTextSize(20);
        renderer.setLabelsTextSize(15);
        renderer.setLegendTextSize(15);
        renderer.setMargins(new int[] { 20, 30, 15, 0 });
        // don't show zoom buttons
        renderer.setZoomButtonsVisible(false);
        renderer.setPointSize(10);
        renderer.setShowGrid(true);
        renderer.setGridColor(Color.RED);
        
        /* 25small squares/sec
         * we are fitting 1000 samples(4 sec) in a screen
         * we need to have 100 small squares
         */
        renderer.setXLabels(100);
        
        /* 25small squares/sec
         * we are fitting 1000 samples(4 sec) in a screen
         * we need to have 100 small squares
         */
        renderer.setYLabels(50);
        
        // x and y axis labels
        renderer.setShowLabels(false);

        
        //renderer.setInitialRange(new double[] {0, 1000, -100, 100});
        renderer.setYAxisMin(-100);
        renderer.setYAxisMax(100);
        renderer.setXAxisMin(0);
        renderer.setXAxisMax(1000);
        renderer.setPanLimits(new double[] { 0, 7500, -100, 100 });
        //renderer.setZoomLimits(new double[] { 0, 0, 0, 0 });

        rendererSeries = new XYSeriesRenderer();
        rendererSeries.setColor(Color.BLACK);
        rendererSeries.setLineWidth(2);

        renderer.addSeriesRenderer(rendererSeries);

        voltageSeries = new XYSeries("ECG waveform");

        dataset.addSeries(voltageSeries);

        waveform = ChartFactory.getLineChartView(this, dataset, renderer);
        waveform.refreshDrawableState();
        waveform.repaint();

        plot.addView(waveform);
        
        
    }
}
