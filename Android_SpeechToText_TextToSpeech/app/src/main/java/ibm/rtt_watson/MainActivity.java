package ibm.rtt_watson;

import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.android.library.audio.MicrophoneInputStream;
import com.ibm.watson.developer_cloud.android.library.audio.StreamPlayer;
import com.ibm.watson.developer_cloud.android.library.audio.utils.ContentType;
import com.ibm.watson.developer_cloud.language_translator.v2.LanguageTranslator;
import com.ibm.watson.developer_cloud.language_translator.v2.model.Language;
import com.ibm.watson.developer_cloud.service.exception.UnauthorizedException;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.RecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static com.ibm.watson.developer_cloud.language_translator.v2.model.Language.ENGLISH;

public class MainActivity extends AppCompatActivity {

    // UI Components
    private TextView transcription;
    private Spinner targetLanguage;
    private Language selectedTargetLanguage;
    private ImageButton micButton;
    private Boolean recording;

    // Peripherals
    private MicrophoneInputStream capture;
    private StreamPlayer player = new StreamPlayer();

    // Services
    private SpeechToText speechService;
    private TextToSpeech textService;
    private LanguageTranslator translationService;

    // Credentials file
    Properties properties = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recording = true;
        // Initialize services below
        speechService = initSpeechToTextService();
        textService = initTextToSpeechService();
        translationService = initLanguageTranslatorService();

        transcription = (TextView) findViewById(R.id.transcription);
        targetLanguage = (Spinner) findViewById(R.id.targetLanguage);
        micButton = (ImageButton) findViewById(R.id.micButton);

        ArrayAdapter adapter = (ArrayAdapter) targetLanguage.getAdapter();
        int position = adapter.getPosition("Spanish");
        targetLanguage.setSelection(position);

        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) {
                    try {
                        capture.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Microphone Off",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                    recording = false;
                } else {
                    initRecognition();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Microphone On",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                    recording = true;
                }
            }
        });


        initRecognition();
    }

    private TextToSpeech initTextToSpeechService(){
        TextToSpeech service = new TextToSpeech();
        String username = getProperty("TTS-User");
        String password = getProperty("TTS-Password");
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private void initRecognition() {
        capture = new MicrophoneInputStream(true);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Call STT service here
                    speechService.recognizeUsingWebSocket(
                            capture,
                            getRecognizeOptions(),
                            new MicrophoneCallback()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private LanguageTranslator initLanguageTranslatorService() {
        LanguageTranslator service = new LanguageTranslator();
        String username = getProperty("LT-User");
        String password = getProperty("LT-Password");
        service.setUsernameAndPassword(username, password);
        return service;
    }

    private SpeechToText initSpeechToTextService() {
        SpeechToText service = new SpeechToText();
        String username = getProperty("STT-User");
        String password = getProperty("STT-Password");
        service.setUsernameAndPassword(username, password);
        service.setEndPoint("https://stream.watsonplatform.net/speech-to-text/api");
        return service;
    }

    //returns an instance of the class RecognizeOptions
    private RecognizeOptions getRecognizeOptions() {
        return new RecognizeOptions.Builder()
                .continuous(true)
                .contentType(ContentType.OPUS.toString())
                .model("es-ES_BroadbandModel")
                .interimResults(true)
                .inactivityTimeout(2000)
                .build();
    }

    private String getProperty(String key) {
        if (properties == null) {
            properties = new Properties();
            AssetManager assetManager = this.getAssets();

            try {
                InputStream inputStream = assetManager.open("config.properties");
                properties.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        return properties.getProperty(key);
    }

    private class MicrophoneCallback implements RecognizeCallback {

        @Override
        public void onTranscription(SpeechResults speechResults) {
            // Retrieve the text, display to the user and
            // call the LT and TTS services

            if (!speechResults.getResults().isEmpty()) {
                final String recognizedText =
                        speechResults.getResults().get(0).getAlternatives().get(0).getTranscript();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        transcription.setText(recognizedText);
                    }
                });
                if (speechResults.isFinal()) {
                    try {
                        capture.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String selected = (String) targetLanguage.getSelectedItem();
                    switch (selected) {
                        case "Spanish":
                            selectedTargetLanguage = Language.SPANISH;
                            break;
                        case "Portuguese":
                            selectedTargetLanguage = Language.PORTUGUESE;
                            break;
                        case "Italian":
                            selectedTargetLanguage = Language.ITALIAN;
                            break;
                        case "French":
                            selectedTargetLanguage = Language.FRENCH;
                    }

                      new TranslateAndSynthesizeTask().execute(recognizedText,
                            selectedTargetLanguage);


            }

            }

        }

        @Override
        public void onConnected() {
        }

        @Override
        public void onError(Exception e) {
            e.printStackTrace();
        }

        @Override
        public void onDisconnected() {
        }
    }

    private class TranslateAndSynthesizeTask extends AsyncTask<Object, Void, Void> {

        @Override
        protected Void doInBackground(Object... params) {

            String recognizedText = (String) params[0];
            Language selectedLanguage = (Language) params[1];

            String translatedText;
            try {
                translatedText = translationService
                        .translate(recognizedText, ENGLISH, selectedLanguage)
                        .execute()
                        .getFirstTranslation();
            } catch (UnauthorizedException e){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                MainActivity.this,
                                "Invalid credentials for LT service.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
                return null;
            }
            // Select voice and call TTS service
            Voice respectiveVoice = null;
            switch (selectedLanguage)
            {
                case SPANISH:
                    respectiveVoice = Voice.ES_ENRIQUE;
                    break;
                case PORTUGUESE:
                    respectiveVoice = Voice.PT_ISABELA;
                    break;
                case ITALIAN:
                    respectiveVoice = Voice.IT_FRANCESCA;
                    break;
                case FRENCH:
                    respectiveVoice = Voice.FR_RENEE;
            }

            InputStream audio;
            try {
                audio = textService.synthesize(
                        translatedText, respectiveVoice).execute();
            } catch (UnauthorizedException e){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(
                                MainActivity.this,
                                "Invalid credentials for TTS service.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
                return null;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            MainActivity.this,
                            "Playing Translated Speech",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
            player.playStream(audio);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(
                            MainActivity.this,
                            "Playing Translated Speech",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

            // Play audio to the user
            player.playStream(audio);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            // Restart speech recognition
            initRecognition();
        }
    }

}

