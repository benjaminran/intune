package com.bran.smarttuner;

import android.widget.TextView;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.util.PitchConverter;

/** TODO: lifecycle management
 * Handles microphone sampling, pitch detection (using YIN algorithm), and filtering then publishes currentPitch.
 */
public class PitchDetector {

    // Main component field
    private Pitch currentPitch; // TODO: run through high pass filter to eliminate noise (skip momentary note deviations and average several cent measurements?)
    // Filter for raw frequencies detected
    PitchFilter filter;

    public PitchDetector() {
        filter = new PitchFilter();
        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);
        AudioProcessor p = new PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.FFT_YIN, 22050, 1024, filter); // TODO: make more robust
        dispatcher.addAudioProcessor(p);
        new Thread(dispatcher,"Audio Dispatcher").start();
    }

    public Pitch getCurrentPitch() {
        return currentPitch;
    }

    public float getFilteredFrequency() {
        return filter.getFilteredFrequency();
    }

    public float getRawfrequency() {
        return filter.getRawFrequency();
    }

    /**
     * Receives raw pitch detection results, applies smoothing filter, and publishes
     */
    private class PitchFilter implements PitchDetectionHandler {
        // Current readings
        private float filteredFrequency;
        private float rawFrequency;
        // Filter configuration and fields
        private static final int BUFFER_SIZE = 16;
        private ArrayBlockingQueue<Float> buffer;
        private float bufferSum;
        private static final int MAX_CONSEC_NULL = 16;
        private int consecNull;

        public PitchFilter() {
            buffer = new ArrayBlockingQueue<Float>(BUFFER_SIZE);
            for(int i=0; i<BUFFER_SIZE; i++) buffer.add(0f);
            bufferSum = 0f;
        }

        @Override
        public void handlePitch(PitchDetectionResult result, AudioEvent audioEvent) {
            rawFrequency = result.getPitch();
            if(rawFrequency==-1) { // no pitch heard
                consecNull++;
                if(consecNull==MAX_CONSEC_NULL) { // true silence
                    currentPitch = null;
                    // clear buffer
                    while(buffer.size()!=0) {
                        buffer.remove();
                    }
                    bufferSum = 0f;
                    filteredFrequency = -1;
                }
            }
            else { // pitch heard
                consecNull = 0;
                // calculate filtered frequency
                if(buffer.size()==BUFFER_SIZE) {
                    bufferSum -= buffer.remove();
                }
                buffer.add(rawFrequency);
                bufferSum += rawFrequency;
                filteredFrequency = bufferSum/buffer.size();
                currentPitch = Pitch.fromFrequency(filteredFrequency);
            }
        }

        public float getFilteredFrequency() {
            return filteredFrequency;
        }

        public float getRawFrequency() {
            return rawFrequency;
        }
    }
}
