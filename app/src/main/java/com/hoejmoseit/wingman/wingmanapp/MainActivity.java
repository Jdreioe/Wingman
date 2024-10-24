//
// Copyright (c) Jonas Højmose Dreiøe. All rights reserved.
// Licensed under the GPL 3.0 license. See LICENSE.md file in the project root for full license information.

// <code>
package com.hoejmoseit.wingman.wingmanapp;

import static android.Manifest.permission.INTERNET;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.room.Room;

import com.hoejmoseit.wingman.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.hoejmoseit.wingman.wingmanapp.backgroundtask.BluetoothSpeakerSoundChecker;
import com.hoejmoseit.wingman.wingmanapp.backgroundtask.ConnectionCheck;
import com.hoejmoseit.wingman.wingmanapp.database.AppDatabase;
import com.hoejmoseit.wingman.wingmanapp.database.SaidTextDao;
import com.hoejmoseit.wingman.wingmanapp.database.SaidTextItem;
import com.hoejmoseit.wingman.wingmanapp.database.SpeechItem;
import com.hoejmoseit.wingman.wingmanapp.database.SpeechItemAdapter;
import com.hoejmoseit.wingman.wingmanapp.database.SpeechItemDao;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService speechExecutor = Executors.newSingleThreadExecutor();

    private SpeechConfig speechConfig;
    private SpeechSynthesizer synthesizer;
    private MaterialButtonToggleGroup languageToggle;
    private SharedPreferences sharedPreferences;
    private String speechSubscriptionKey;
    private String serviceRegion;
    private String selectedVoice = "BrianMultiLingual";
    private float pitch;
    private float speed;
    private SpeechItem deletedItem = null;
    private int deletedIndex = -1;
    private SpeechItemDao speechItemDao;
    private SaidTextDao saidTextDao;
    private SpeechItemAdapter speechItemAdapter;
    private List<SpeechItem> speechItemsInCurrentFolder;
    private MaterialToolbar topAppBar;
    // Keeps track of folder selection
    private int currentFolderId = -1;
    private boolean isSomeFolderSelected = false;

    private int colorTertiary;
    private EditText speakText;
    private AudioConfig audioConfig;
    private String dynamicPath;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        speakText = this.findViewById(R.id.speak_text);
        topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setNavigationIcon(R.mipmap.ic_launcher);
        topAppBar.setNavigationOnClickListener(v -> {

            // Hvis jeg er i en mappe,
            if (isSomeFolderSelected) {


                databaseExecutor.execute(() -> {

                    // If currentFolderId == -1 we know that Historik was selected
                    boolean wasHistorikSelected = currentFolderId == -1;
                    if (wasHistorikSelected) {
                        selectRootFolder();

                        return;
                    }
                    SpeechItem currentFolder = speechItemDao.getItemById(currentFolderId);

                    if (currentFolder.parentId != null ) {
                        selectFolder(currentFolder.parentId, currentFolder.name);
                    } else {
                        selectRootFolder();
                    }
                });
            } else {

                topAppBar.setNavigationIcon(R.mipmap.ic_launcher);

                selectRootFolder();


            }

            sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);

            speechSubscriptionKey = sharedPreferences.getString("sub_key", "");
            serviceRegion = sharedPreferences.getString("sub_locale", "");

        });

        FirstTimeLaunchDialog.showFirstTimeLaunchDialog(this);




            // sets saidTextItems to id 0 as a folder  ;


        databaseExecutor.execute(() -> {

            AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "speech_database")
                    .fallbackToDestructiveMigration() // Allow destructive migrations
                    .build();
            speechItemDao = db.speechItemDao();
            saidTextDao = db.saidTextDao();

            speechItemsInCurrentFolder = speechItemDao.getAllRootItems();

            runOnUiThread(() -> {

                RecyclerView recyclerView = findViewById(R.id.speech_items_list);
                recyclerView.setLayoutManager(new LinearLayoutManager(this));

                // TODO: crashing whem trying to speak a history item
                speechItemAdapter = new SpeechItemAdapter(speechItemsInCurrentFolder, speechItem -> {





                    if (speechItem.isFolder) {
                        // Handle folder click
                        databaseExecutor.execute(() -> selectFolder(speechItem.id, speechItem.name));
                    } else {
                        // Handle item click
                        playText(speechItem.text);
                    }
                });
                recyclerView.setAdapter(speechItemAdapter);


                speechItemAdapter.notifyDataSetChanged();
                updateSpeechItems();



                ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                    private Drawable icon;
                    private final ColorDrawable background = new ColorDrawable(Color.RED);

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        deletedIndex = viewHolder.getAdapterPosition();
                        deletedItem = speechItemsInCurrentFolder.get(deletedIndex);
                        speechItemsInCurrentFolder.remove(deletedIndex);
                        speechItemAdapter.notifyItemRemoved(deletedIndex);
                        deleteItem(deletedItem);
                        // showUndoSnackbar();
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                        View itemView = viewHolder.itemView;
                        int backgroundCornerOffset = 20;

                        icon = ContextCompat.getDrawable(getApplicationContext(), R.drawable.ic_delete); // Replace with your delete icon
                        int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconBottom = iconTop + icon.getIntrinsicHeight();

                        if (dX < 0) { // Swiping to the left
                            int iconLeft = itemView.getRight() - iconMargin - icon.getIntrinsicWidth();
                            int iconRight = itemView.getRight() - iconMargin;
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                            background.setBounds(itemView.getRight() + ((int) dX) - backgroundCornerOffset,
                                    itemView.getTop(), itemView.getRight(), itemView.getBottom());
                        } else { // view is unswiped
                            background.setBounds(0, 0, 0, 0);
                        }

                        background.draw(c);
                        icon.draw(c);
                    }
                };

                ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleCallback);
                itemTouchHelper.attachToRecyclerView(recyclerView);


            });


        });




        languageToggle = this.findViewById(R.id.language_toggle);

        // Note: we need to request the permissions
        int requestCode = 5; // unique code for the permission request
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{INTERNET}, requestCode);



    }

    @SuppressLint("NotifyDataSetChanged")
    private void selectFolder(int folderId, String folderName) {
        runOnUiThread(() -> {
            topAppBar.setTitle(folderName);
            topAppBar.setNavigationIcon(R.drawable.ic_back);
            // topAppBar.setNavigationIconTint(getDynamicColor(android.R.attr.colorPrimary));
        });
        currentFolderId = folderId;
        //fjern fra recycler
        speechItemsInCurrentFolder.clear();
        isSomeFolderSelected = true;
        if (folderId == -1) {
            onHistorikSelected();
            return;
        }

        // find alle speechItems fra mappen og sæt dem ind i  speechItemsInCurrentFolder listen

        databaseExecutor.execute(() -> {
            speechItemsInCurrentFolder.addAll(speechItemDao.getAllItemsInFolder(folderId));

            //refresh recycler
            runOnUiThread(this::updateSpeechItems);

            // tilbageknap hvis isSomeFolderSelected == true:
            // isSomeFolderSelected = false
        });

        // vis tilbageknap
    }

    @SuppressLint("NotifyDataSetChanged")
    private void selectRootFolder() {
        isSomeFolderSelected = false;
        currentFolderId = -1;
        speechItemsInCurrentFolder.clear();

        runOnUiThread(() -> {
            topAppBar.setNavigationIcon(R.mipmap.ic_launcher);
            topAppBar.setTitle("Wingman");
        });

        databaseExecutor.execute(() -> {
            speechItemsInCurrentFolder.addAll(speechItemDao.getAllRootItems());
            runOnUiThread(this::updateSpeechItems);
        });
    }

    private void deleteItem(SpeechItem deletedItem) {
        databaseExecutor.execute(() -> {
            speechItemDao.deleteItems(List.of(deletedItem));
            speechItemsInCurrentFolder.remove(deletedItem);
            updateSpeechItems();
        });
    }

    private void insertItem(SpeechItem item) {
        databaseExecutor.execute(() -> {

            long ok = speechItemDao.insertItem(item);
            speechItemsInCurrentFolder.clear();
            if (isSomeFolderSelected) {
                speechItemsInCurrentFolder.addAll(speechItemDao.getAllItemsInFolder(currentFolderId));
            } else {
                speechItemsInCurrentFolder.addAll(speechItemDao.getAllRootItems());
            }
            updateSpeechItems();
        });
    }

    private int getDynamicColor(int colorAttribute) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(colorAttribute, typedValue, true);
        return typedValue.data;
    }

    public static @NonNull String getSsml(String text, Language language, String Voice, float pitch, float speed) throws Exception {

        if (language == Language.MULTI) {
            return "<speak version='1.0' xml:lang='da-DK' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='http://www.w3.org/2001/mstts'>"
                    .concat(String.format("<voice name='%s'>", Voice))
                    .concat("<prosody rate='" + speed + "' pitch='" + pitch + "%'>")
                    .concat(text)
                    .concat("</prosody>")
                    .concat("</voice>")
                    .concat("</speak>");
        } else {
            return "<speak version='1.0' xml:lang='da-DK' xmlns='http://www.w3.org/2001/10/synthesis' xmlns:mstts='http://www.w3.org/2001/mstts'>"
                    .concat(String.format("<voice name='%s'>", Voice))

                    .concat("<prosody rate='" + speed + "' pitch='" + pitch + "%'>")
                    .concat("<lang xml='" + getLanguageShortname(language) + "'>")

                    .concat(text)
                    .concat("</lang>")
                    .concat("</prosody>")
                    .concat("</voice>")
                    .concat("</speak>");
        }

    }

    private static @NonNull String getLanguageShortname(Language language) throws Exception {
        switch (language) {
            case DANISH:
                return "da-DK";
            case ENGLISH:
                return "en-US";

        }
        throw new IllegalArgumentException("Invalid language");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Release speech synthesizer and its dependencies
        synthesizer.close();
        speechConfig.close();
    }

    public void omDeleteButtonClicked(View v) {
        speakText.setText("");
        //String text1 = sharedPreferences.getString("text1", "");
        //String text2 = sharedPreferences.getString("text2", "");

    }

    public void onSettingsButtonClicked(View v) {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void onNewSpeechItemButtonClicked(View view) {

        AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_speech_item_title)
                .setView(R.layout.added_speech) // Inflate your custom layout
                .setPositiveButton("Save", (dialog, which) -> {
                    // Handle save action

                    EditText titleInput = ((AlertDialog) dialog).findViewById(R.id.title_input);
                    EditText textInput = ((AlertDialog) dialog).findViewById(R.id.text_input);
                    SwitchMaterial folderToggle = ((AlertDialog) dialog).findViewById(R.id.folder_toggle);
                    String title = titleInput.getText().toString();
                    String text = textInput.getText().toString();
                    boolean isFolder = folderToggle.isChecked();

                    SpeechItem speechItem = new SpeechItem();
                    speechItem.text = text;
                    speechItem.name = title;
                    speechItem.isFolder = isFolder;

                    if (isSomeFolderSelected) {
                        speechItem.parentId = currentFolderId;
                    }
                    System.out.println("currentFolderId: " + currentFolderId);

                    // hvis man trykker på currentfolder, så skal den gemmes i currentfolder

                    insertItem(speechItem);

                    // Update the SpeechItem object with the new values
                    // ...
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Handle cancel action (e.g., dismiss the dialog)
                })
                .show();
        ((EditText)alertDialog.findViewById(R.id.text_input))
                .setText(speakText.getText().toString());

    }


    public void onSpeechButtonClicked(View v) {
        EditText speakText = this.findViewById(R.id.speak_text);

        String s = speakText.getText().toString().trim();
        playText(s);


    }

    public void playText(String speakText) {
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        if (speakText.isEmpty()) {
            System.out.println("DER ER INTET ");
            Toast.makeText(this, R.string.ingen_text_til_at_l_se_op, Toast.LENGTH_SHORT).show();
            return;
        } else if (!ConnectionCheck.isInternetAvailable(this)) {
            Toast.makeText(this, R.string.du_har_ikke_internet_afspil_en_af_dine_gemte_s_tninger_eller_pr_v_igen_senere, Toast.LENGTH_SHORT).show();
            return;

        }else if (sharedPreferences.getBoolean("noVoice",true)) {
            Toast.makeText(this, R.string.noVoiceSelected, Toast.LENGTH_SHORT).show();
            return;

        }
        try {


            // Note: this will block the UI thread, so eventually, you want to register for the event
            selectedVoice = sharedPreferences.getString("voice", "en-US-BrianMultilingualNeural");
            System.out.println("Den valgte stemme er: " + selectedVoice);
            pitch = sharedPreferences.getFloat("pitch", 1f);
            speed = sharedPreferences.getFloat("speed", 1f);
            String ssml = getSsml(speakText, getSelectedLanguage(languageToggle), selectedVoice, pitch, speed);


            speechExecutor.execute(() -> {
                SaidTextItem saidTextItem = saidTextDao.getByText(speakText.trim());
                if (saidTextItem != null &&
                        Objects.equals(selectedVoice, saidTextItem.voiceName) &&
                        saidTextItem.audioFilePath != null &&
                        saidTextItem.pitch == pitch &&
                        saidTextItem.speed == speed
                ) {
                    // Get the audio file from saidTextItem and play it
                    MediaPlayer player = new MediaPlayer();
                    try {
                        player.setDataSource(saidTextItem.audioFilePath);
                        player.prepare();
                    } catch (IOException e) {


                    }
                    player.start();
                    return;
                }
                saidTextItem = new SaidTextItem();
                saidTextItem.saidText = speakText;
                saidTextItem.date = new Date();
                saidTextItem.voiceName = selectedVoice;
                saidTextItem.pitch = pitch;
                saidTextItem.speed = speed;

                Long whatever = saidTextDao.insertHistorik(saidTextItem);
                saidTextItem = saidTextDao.getByText(speakText);

                speechSubscriptionKey = sharedPreferences.getString("sub_key", "");
                serviceRegion = sharedPreferences.getString("sub_locale", "");
                speechConfig = SpeechConfig.fromSubscription(speechSubscriptionKey, serviceRegion);
                // Initialize speech synthesizer and its dependencies
                assert (speechConfig != null);
                audioConfig = AudioConfig.fromWavFileOutput(getFilesDir().getAbsolutePath() + "/" + saidTextItem.id + ".wav");
                synthesizer = new SpeechSynthesizer(speechConfig, audioConfig);

                assert (synthesizer != null);

                if (BluetoothSpeakerSoundChecker.isBluetoothSpeakerActive(this)) {

                    System.out.println("Venter 1 sekund med at afspille lyden");
                    BluetoothSpeakerSoundChecker.playSilentSound(); // Play silent sound for 1 second


                }
                try {
                    SpeechSynthesisResult result = synthesizer.SpeakSsmlAsync(ssml).get();
                    byte[] data = result.getAudioData();


                    // Use the SSML string for saidText-to-speech
                    assert (result != null);

                    if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
                        {
                            System.out.println("Speech synthesis completed.");
                            result.close();
                        }
                    }
                    saidTextItem.audioFilePath = getFilesDir().getAbsolutePath() + "/" + saidTextItem.id + ".wav";
                    System.out.println("File path: " + saidTextItem.audioFilePath);
                    saidTextItem.voiceName = selectedVoice;


                    saidTextDao.updateHistorik(saidTextItem);

                    MediaPlayer player = new MediaPlayer();
                    player.setDataSource(saidTextItem.audioFilePath);
                    System.out.println(saidTextItem.audioFilePath);
                    player.prepare();
                    player.start();

                } catch (ExecutionException e) {
                    Toast.makeText(this, e.getMessage() + " 1. catch", Toast.LENGTH_SHORT).show();

                } catch (InterruptedException e) {
                    Toast.makeText(this, e.getMessage() + "2. catch", Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    Toast.makeText(this, e.getMessage() + " IO exception", Toast.LENGTH_SHORT).show();

                }

            });


        } catch (IllegalArgumentException ex) {
            Toast.makeText(this, R.string.check_info, Toast.LENGTH_SHORT).show();


        } catch (Exception e) {
            Toast.makeText(this, R.string.check_info, Toast.LENGTH_SHORT).show();
        }
    }

    public Language getSelectedLanguage(MaterialButtonToggleGroup toggleGroup) {
        int checkedId = toggleGroup.getCheckedButtonId();
        if (checkedId == R.id.english_button) {
            return Language.ENGLISH;
        } else if (checkedId == R.id.auto_button) {
            return Language.MULTI;
        } else if (checkedId == R.id.danish_button) {
            return Language.DANISH;
        } else {
            return Language.MULTI;
        }
    }

    private void updateSpeechItems() {
        if (!isSomeFolderSelected) {
            SpeechItem historik = new SpeechItem();
            historik.name = "Historik";
            historik.id = -1;
            historik.isFolder = true;
            historik.parentId = -1;
            speechItemsInCurrentFolder.add(0, historik);
        }
        databaseExecutor.execute(() -> {
                    runOnUiThread(() -> speechItemAdapter.notifyDataSetChanged());
                }
        );
    }
    public void onFullscreenButronClicked(View v) {
        Intent intent = new Intent(MainActivity.this, displayText.class);
        sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        EditText speakText = this.findViewById(R.id.speak_text);

        SharedPreferences.Editor editor = sharedPreferences.edit();

        System.out.println(speakText.getText().toString());
        editor.putString("SPEAK_TEXT", speakText.getText().toString());
        editor.apply();
        startActivity(intent);
    }

    public void onHistorikSelected(){

        List<SaidTextItem> historik = saidTextDao.getAll();
        for (SaidTextItem item : historik) {
            SpeechItem speechItem = new SpeechItem();

            speechItem.text = item.saidText;;
            speechItem.id = item.id;
            speechItem.isFolder = false;
            speechItemsInCurrentFolder.add(speechItem);

        }
        if (((RecyclerView) findViewById(R.id.speech_items_list)).isComputingLayout())
        {
            findViewById(R.id.speech_items_list).post(new Runnable()
            {
                @Override
                public void run() {
                    speechItemAdapter.notifyDataSetChanged();
                }
            });
        } else {
            runOnUiThread(() -> {
                speechItemAdapter.notifyDataSetChanged();

            });
        }


    }
    enum Language {
        DANISH,
        ENGLISH,
        MULTI
    }
}