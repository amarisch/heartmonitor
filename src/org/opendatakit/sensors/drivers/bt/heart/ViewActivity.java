package org.opendatakit.sensors.drivers.bt.heart;

import org.achartengine.ChartFactory;
import org.achartengine.GraphicalView;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.model.XYSeries;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ViewActivity extends Activity{
	
	private static final String TAG = "ViewActivity";
	
	private static final int DETECTION_TIME = 7500;
	
	// For view.xml in layout folder
    private LinearLayout plot;
	private TextView heartRateField, qrs_durationField, regularityField;
	
	private int heartRate;
	private int qrs_duration;
	private String regularity;
	
	// For AChartEngine main plot
	private static int[] voltageArray;
	private XYSeries voltageSeries;
    private static XYMultipleSeriesDataset dataset;
    private static XYMultipleSeriesRenderer renderer;
    private static XYSeriesRenderer rendererSeries;
    private static GraphicalView waveform;



    private static int index = 0;
    
 	protected void onCreate(Bundle savedInstanceState) {
	 	super.onCreate(savedInstanceState);
		// automatically set orientation to landscape
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.view);

		heartRateField = (TextView) findViewById(R.id.heartRateField);
		regularityField = (TextView) findViewById(R.id.regularityField);     
		qrs_durationField = (TextView) findViewById(R.id.qrs_durationField);  
        
        voltageArray = getIntent().getIntArrayExtra("xyseries");
        heartRate = getIntent().getIntExtra("heartrate", 0);
        qrs_duration = getIntent().getIntExtra("qrs_duration", 0);
        regularity = getIntent().getStringExtra("regularity");
        		
        Log.d(TAG,"item count: " + voltageArray.length);
        
		plot = (LinearLayout) findViewById(R.id.voltage);
		// Initialize Plot setting
		plot_init();
		
		for (int i = 0; i < DETECTION_TIME; i++) {
			voltageSeries.add(i, voltageArray[i]);
		}
		waveform.repaint();
		
		heartRateField.setText("  " + String.valueOf(heartRate));
		qrs_durationField.setText("  " + String.valueOf(qrs_duration));

		if (regularity == null) {
			regularityField.setText("  " + "Nothing Detected");
		} else {
			regularityField.setText("  " + String.valueOf(regularity));
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
        renderer.setPanLimits(new double[] { 0, DETECTION_TIME, -100, 100 });
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
	
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.viewactivity_options_menu, menu);
	    return true;
	}
	
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case R.id.patientList:
            Intent j = new Intent(this,DatabaseActivity_old.class);
            startActivity(j);
            return true;            
        case R.id.preferences:
        	doPreferences();
        	//registerForContextMenu(view_setting);
        	return true;
        }        
        return false;
    }
    
	private void doPreferences() {
        startActivity(new Intent(this, SetPreferenceActivity.class));
    }

}
