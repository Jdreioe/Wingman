import 'dart:convert';
import 'dart:ffi';
import 'dart:io';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';
import 'package:wingmate/models/voice_model.dart';
import 'package:wingmate/utils/app_database.dart';
import 'package:wingmate/utils/said_text_dao.dart';
import 'package:wingmate/utils/said_text_item.dart';
import 'package:hive/hive.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:wingmate/utils/speech_service_config.dart';

class AzureTts {
  final String subscriptionKey;
  final String region;
  final Box<dynamic> settingsBox;
  final TextEditingController messageController;
  final Box<dynamic> voiceBox;
  final player = AudioPlayer();
  final BuildContext context;
  AzureTts(
      {required this.subscriptionKey,
      required this.region,
      required this.settingsBox,
      required this.messageController,
      required this.voiceBox,
      required this.context});
  // Pauses the audio playback if it's active.
  Future<void> pause() async {
    player.pause();
  }

  // Converts given text to speech using the selected Voice and Azure TTS service.
  Future<void> speakText(String text) async {
    Voice voice = voiceBox.get('currentVoice');
    debugPrint("Den valgte stemme er: " +
        voice.name +
        " og sproget den skal tale er: " +
        voice.selectedLanguage);
    final selectedVoice = voice.name; // 'the selected voice name';
    final selectedLanguage = voice.selectedLanguage; // 'the selected language';
    final url = Uri.parse(
        'https://$region.tts.speech.microsoft.com/cognitiveservices/v1');

    final headers = {
      'Ocp-Apim-Subscription-Key': subscriptionKey,
      'Content-Type': 'application/ssml+xml',
      'X-Microsoft-OutputFormat': 'audio-16khz-128kbitrate-mono-mp3',
      'User-Agent': 'WingmateCrossPlatform',
    };

    final ssml =
        '<speak version="1.0" xml:lang="en-US"> <voice name="$selectedVoice"><lang xml:lang="$selectedLanguage">$text</lang > </voice> </speak>';

    debugPrint('URL: $url');
    debugPrint('Headers: $headers');
    debugPrint('SSML: $ssml');

    // Attempt to post the SSML body to Azure TTS endpoint
    try {
      final response = await http.post(url, headers: headers, body: ssml);

      if (response.statusCode == 200) {
        final directory = await getApplicationDocumentsDirectory();
        final file = File('${directory.path}/temp_audio.mp3');
        await file.writeAsBytes(response.bodyBytes);
        // Play the generated audio file
        await player.play(DeviceFileSource(file.path));
        player.onPlayerComplete.listen((event) {
          // Send isPlaying back to the main page with isPlaying = false
        });
        // Save the audio file to the device and database
        saveAudioFile(text, file.readAsBytesSync(), selectedVoice);
      } else {
        debugPrint('Error: ${response.statusCode}, ${response.body}');
      }
    } catch (e) {
      debugPrint('Exception: $e');
    }
  }

  // Persists the audio file and text record in the local database.
  Future<void> saveAudioFile(
    String text,
    List<int> audioData,
    String voice,
  ) async {
    final SaidTextDao saidTextDao = SaidTextDao(AppDatabase());
    try {
      final Directory directory = await getApplicationDocumentsDirectory();
      final String filePath =
          '${directory.path}/audio_${DateTime.now().millisecondsSinceEpoch}.mp3';
      final File file = File(filePath);
      await file.writeAsBytes(audioData);

      SaidTextItem saidTextItem = SaidTextItem(
          saidText: text,
          audioFilePath: filePath,
          date: DateTime.now().millisecondsSinceEpoch,
          voiceName: voice);
      saidTextDao.insertHistorik(saidTextItem);
      debugPrint('Audio file saved at: $filePath');
    } catch (e) {
      print('Error saving audio file: $e');
    }
  }
}
