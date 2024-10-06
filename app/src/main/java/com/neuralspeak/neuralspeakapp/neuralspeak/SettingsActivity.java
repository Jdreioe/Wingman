package com.neuralspeak.neuralspeakapp.neuralspeak;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.elvishew.xlog.XLog;
import com.example.neuralspeak.R;
import com.google.android.material.slider.Slider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.room.Room;

public class SettingsActivity extends AppCompatActivity {
    private List<String> voices = Collections.emptyList();

    private EditText subscriptionID;
    private EditText resourceLocale;
    private SharedPreferences sharedPreferences;

    private static final ExecutorService restExecutor = Executors.newSingleThreadExecutor();
    private Spinner voiceSpinner;
    private String selectedVoice;
    private Integer azureWPM;
    private Integer azurePitch;
    private Slider pitchSlider;
    private Slider speedSlider;
    private int selectedVoiceIndex;
    private SharedPreferences.Editor editor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        setContentView(R.layout.settings_activity);
        subscriptionID = this.findViewById(R.id.subscriptionKey);
        resourceLocale = this.findViewById(R.id.resourceLocale);
        speedSlider = this.findViewById(R.id.speed_slider);
        pitchSlider = this.findViewById(R.id.pitch_slider);
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);


        speedSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
            editor = sharedPreferences.edit();
            editor.putFloat("speed", value);
            editor.commit();
            }

            // ... other listener methods ...
        });

        pitchSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                editor = sharedPreferences.edit();

                editor.putFloat("pitch", value);

                editor.commit();
            }

        });

        String text1 = sharedPreferences.getString("sub_key", "");
        String text2 = sharedPreferences.getString("sub_locale", "");
        sharedPreferences.getString("voice", "");
        float speed = sharedPreferences.getFloat("speed", 1f);


        subscriptionID.setText(text1);
        resourceLocale.setText(text2);
        speedSlider.setValue(speed);

        voiceSpinner = findViewById(R.id.voice_spinner);

        restExecutor.execute(() -> {
            retrieveVoicesAndSetupVoiceSpinner();
            runOnUiThread(() -> {
                System.out.println("Selected voice index: " + selectedVoiceIndex);
            });
        });
    }


    private void retrieveVoicesAndSetupVoiceSpinner() {
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "speech_database").fallbackToDestructiveMigration().build();
        VoiceDao voiceDao = db.voiceDao();
        List<VoiceItem> downloadedVoices = voiceDao.getAllVoices();

        if (downloadedVoices.isEmpty()) {

            voices = getVoices(); // Your API call and parsing logic
        }
        else {

            for (VoiceItem voice : downloadedVoices) {
                voices.add(voice.name);
            }
        }
            runOnUiThread(() -> {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, voices);
                voiceSpinner.setAdapter(adapter);
                voiceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                        selectedVoice = voices.get(position);
                        // Store selectedVoice in SharedPreferences or other storage
                        editor = sharedPreferences.edit();
                        editor.putString("voice", selectedVoice);
                        editor.commit();


                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Handle case where nothing is selected
                    }
                });

            });
        selectedVoiceIndex = sharedPreferences.getInt("selected_voice_index", 0);

        runOnUiThread(() -> {
            voiceSpinner.setSelection(selectedVoiceIndex);
        });



    }


    public void onSaveButtonClicked(View v) {
        String azureSubscriptionKey = subscriptionID.getText().toString();
        String azureSubscriptionLocale = resourceLocale.getText().toString();
        // Save the values.
        // You can use SharedPreferences, a database, or any other suitable method.
        // Example using SharedPreferences:
        editor = sharedPreferences.edit();

        editor.putString("sub_key", azureSubscriptionKey);
        editor.putString("sub_locale", azureSubscriptionLocale);
        System.out.println("Sub_locale er sat ind " + voiceSpinner.getSelectedItemPosition());
        editor.putInt("selected_voice_index", voiceSpinner.getSelectedItemPosition());
        editor.commit();


        // Optionally, display a toast message or navigate back to the previous activity.
        Toast.makeText(this, "Oplysninger opdateret!" + azureSubscriptionKey + azureSubscriptionLocale, Toast.LENGTH_SHORT).show();
        finish();

        }


    /**
     * This method MUST be called from a background thread
     *
     * @return the voices that are available
     */
    private List<String> getVoices() {
        // Ensure this method is called from a background thread

        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("This must be called from a background thread.");
        }




        String speechSubKey = subscriptionID.getText().toString();


        List<String> voices = new ArrayList<>();
        try {
            URL url = new URL("https://swedencentral.tts.speech.microsoft.com/cognitiveservices/voices/list");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Ocp-Apim-Subscription-Key", speechSubKey);
            connection.setRequestProperty("Content-Type", "ssml+xml");
            connection.setRequestProperty("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm");
            connection.setRequestProperty("User-Agent","NeuralSpeak 1.0");

            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String line;
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse the JSON response and extract voice names
                JSONArray voicesArray = new JSONArray(response.toString());
                System.out.println(voicesArray);

                for (int i = 0; i < voicesArray.length(); i++) {
                    JSONObject voiceObject = voicesArray.getJSONObject(i);
                    String voiceName = voiceObject.getString("ShortName");
                    voices.add(voiceName);

                }

            } else {

                // Handle error response
                XLog.d("Fejl ved oprettelse af stemmer");
            }
            connection.disconnect();
        } catch (Exception e) {
            // Handle exceptions (e.g., network errors, JSON parsing errors)
            e.printStackTrace();
        }
        return voices;
    }

    }

