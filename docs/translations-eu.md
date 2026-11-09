# Basque translations

Propuestas; las revisa el autor.

Every text that Jaitsizazu shows, with its English source and the Basque proposal. Basque is the default language of the app; English is the second one.

- Source files: `ui/src/commonMain/composeResources/values/strings.xml` (Basque) and `values-en/strings.xml` (English). This table is a copy for review: change the XML files, then this table.
- `%1$s`, `%2$s` and so on are filled in by the app: a path, an address, a tool name, a number. In Basque, paths and names mostly go after a colon, so they never need a case ending.
- Text from the external tools (the error output of ffmpeg and yt-dlp, and system messages) is never translated: the app shows it as it is, on the line after the translated sentence.
- Technical names stay as they are: MP3, WAV, MP4, MKV, 4K, Opus, yt-dlp, ffmpeg, deno, PATH.
- Not translated on purpose: the app name and window title (Jaitsizazu), and the format names in the converter (WAV, MP3, FLAC, Ogg Vorbis).

| Key | English | Basque proposal |
|-----|---------|-----------------|
| `tab_convert` | Convert | Bihurtu |
| `tab_download` | Download | Deskargatu |
| `cancel` | Cancel | Utzi |
| `saved_as` | Saved as %1$s | Gordeta: %1$s |
| `convert_title` | Convert an audio file | Bihurtu audio-fitxategi bat |
| `choose_file` | Choose file | Aukeratu fitxategia |
| `no_file_chosen` | No file chosen yet. | Oraindik ez duzu fitxategirik aukeratu. |
| `convert_to` | Convert to | Formatu berria |
| `convert_button` | Convert | Bihurtu |
| `converting` | Converting… | Bihurtzen… |
| `conversion_cancelled` | Conversion cancelled. | Bihurketa bertan behera utzi da. |
| `download_title` | Download from a web address | Deskargatu web-helbide batetik |
| `legal_note` | Download only your own content, public domain or freely licensed works, or personal copies where the law allows. | Deskargatu zure edukia, jabari publikoko lanak, lizentzia libreko lanak edo, legeak baimentzen duenean, kopia pertsonalak soilik. |
| `web_address` | Web address | Web-helbidea |
| `save_as` | Save as | Gorde honela |
| `quality_audio_mp3` | Audio (MP3) | Audioa (MP3) |
| `quality_video_mp4` | Video (MP4, up to 1080p) | Bideoa (MP4, 1080p arte) |
| `change_folder` | Change folder | Aldatu karpeta |
| `no_folder_chosen` | No folder chosen yet. | Oraindik ez duzu karpetarik aukeratu. |
| `download_button` | Download | Deskargatu |
| `cancelling` | Cancelling… | Bertan behera uzten… |
| `download_cancelled` | Download cancelled. | Deskarga bertan behera utzi da. |
| `update_explainer` | Sites such as YouTube change often. If downloads stop working, update yt-dlp, the download engine. | YouTube bezalako webguneak sarri aldatzen dira. Deskargek huts egiten badute, eguneratu yt-dlp, deskargak egiten dituen programa. |
| `update_button` | Update yt-dlp | Eguneratu yt-dlp |
| `updating` | Updating yt-dlp… | yt-dlp eguneratzen… |
| `update_done` | Answer from yt-dlp: %1$s | Erantzuna (yt-dlp): %1$s |
| `update_unclear` | yt-dlp finished without saying which version it has now. If downloads still fail, click Update yt-dlp again. If that does not help, replace yt-dlp in the bin folder of the app with the latest release from github.com/yt-dlp/yt-dlp. | yt-dlp amaitu da, baina ez du esan zein bertsio duen orain. Deskargek huts egiten jarraitzen badute, sakatu berriro Eguneratu yt-dlp. Horrek ez badu konpontzen, ordeztu aplikazioaren bin karpetako yt-dlp azken bertsioarekin (github.com/yt-dlp/yt-dlp). |
| `update_unclear_last_line` | Its last message was: %1$s | Haren azken mezua: %1$s |
| `progress_starting` | Starting… | Hasten… |
| `progress_downloading` | Downloading… | Deskargatzen… |
| `progress_finishing` | Finishing the file… | Fitxategia amaitzen… |
| `progress_percent` | %1$s% | %%1$s |
| `progress_time_left` | %1$s left | %1$s falta |
| `decimal_separator` | . | , |
| `tool_purpose_ffmpeg` | convert files and to finish downloads | fitxategiak bihurtzeko eta deskargak amaitzeko |
| `tool_purpose_yt_dlp` | download | deskargatzeko |
| `tool_purpose_deno` | download from YouTube | YouTubetik deskargatzeko |
| `problem_no_file_chosen` | Choose a file to convert. | Aukeratu bihurtu nahi duzun fitxategia. |
| `problem_not_a_file` | %1$s is not a file. | Hau ez da fitxategi bat: %1$s |
| `problem_file_not_found` | The file %1$s does not exist. | Fitxategi hau ez dago: %1$s |
| `problem_no_free_name` | No free name left for the converted file next to %1$s. | Ez dago izen librerik bihurtutako fitxategiarentzat, jatorrizkoaren ondoan: %1$s |
| `problem_no_web_address` | Paste the web address of a video or a song first. | Lehenik, itsatsi bideo edo abesti baten web-helbidea. |
| `problem_not_a_web_address` | %1$s is not a web address. It must start with https:// or http://. | Hau ez da web-helbide bat: %1$s. https:// edo http:// hasiera izan behar du. |
| `problem_no_folder_chosen` | Choose a folder to save the download in. | Aukeratu deskarga zein karpetatan gorde. |
| `problem_folder_not_found` | The folder %1$s does not exist. | Karpeta hau ez dago: %1$s |
| `problem_tool_not_found` | %1$s was not found. Jaitsizazu needs it to %2$s. Install %1$s so that it is on your PATH, or set %3$s to the full path of the %1$s binary, or put it in the app folder as %4$s. Then try again. If you changed PATH or %3$s, restart Jaitsizazu first. | Ez da aurkitu %1$s. Jaitsizazuk %2$s behar du. Instalatu %1$s PATHeko karpeta batean, edo jarri %3$s aldagaian %1$s programaren bide-izen osoa, edo kopiatu aplikazioaren karpetara, %4$s gisa. Gero, saiatu berriro. PATH edo %3$s aldatu badituzu, berrabiarazi Jaitsizazu lehenago. |
| `problem_tool_not_at_configured_path` | %1$s is set to %2$s, but there is no executable %3$s there. | %1$s aldagaiaren balioa %2$s da, baina han ez dago %3$s programarik. |
| `problem_tool_did_not_start` | Could not start %1$s (%2$s). | Ezin izan da %1$s abiarazi (%2$s). |
| `problem_tool_read_only` | %1$s at %2$s is read-only for your user, so Jaitsizazu cannot update it. Update it the way it was installed (for example, with your package manager), or put a copy you can write to in the bin folder of the app. | Zure erabiltzaileak ezin du %1$s aldatu (%2$s), eta beraz Jaitsizazuk ezin du eguneratu. Eguneratu instalatu zen modu berean (adibidez, pakete-kudeatzailearekin), edo jarri zuk alda dezakezun kopia bat aplikazioaren bin karpetan. |
| `problem_file_already_exists` | %1$s already exists. Nothing was overwritten. | Fitxategi hau badago jada: %1$s. Ez da ezer gainidatzi. |
| `problem_cannot_create_file` | Could not create %1$s. | Ezin izan da fitxategi hau sortu: %1$s |
| `problem_cannot_save_into` | Cannot save into %1$s. | Ezin da karpeta honetan gorde: %1$s |
| `problem_conversion_failed` | The conversion failed (ffmpeg exit code %1$s). | Bihurketak huts egin du (ffmpeg irteera-kodea: %1$s). |
| `problem_download_failed` | The download failed (yt-dlp exit code %1$s). | Deskargak huts egin du (yt-dlp irteera-kodea: %1$s). |
| `problem_no_file_reported` | yt-dlp finished, but it reported no file. | yt-dlp amaitu da, baina ez du fitxategirik adierazi. |
| `problem_already_in_folder` | %1$s is already in this folder. Nothing was overwritten. | Karpeta honetan badago jada: %1$s. Ez da ezer gainidatzi. |
| `problem_already_in_folder_as` | %1$s is already in this folder, as %2$s. Nothing was overwritten. | Karpeta honetan badago jada, %2$s izenarekin: %1$s. Ez da ezer gainidatzi. |
| `problem_update_failed` | yt-dlp could not update itself (exit code %1$s). | yt-dlp ezin izan da eguneratu (irteera-kodea: %1$s). |
| `problem_unexpected` | Unexpected error: %1$s | Ustekabeko errorea: %1$s |
| `leftover_unfinished_file` | The unfinished file %1$s could not be removed. | Ezin izan da amaitu gabeko fitxategi hau ezabatu: %1$s |
| `leftover_temporary_folder` | Some temporary files could not be removed: %1$s. You can delete that folder. | Aldi baterako fitxategi batzuk ezin izan dira ezabatu: %1$s. Karpeta hori zuk ezaba dezakezu. |
| `choose_file_title` | Choose an audio file | Aukeratu audio-fitxategi bat |
| `choose_folder_title` | Choose where to save downloads | Aukeratu deskargak non gorde |
| `choose_folder_approve` | Choose | Aukeratu |
| `crash_intro` | Jaitsizazu ran into an unexpected error: | Jaitsizazuk ustekabeko errore bat izan du: |
| `crash_details_saved` | Details were saved to %1$s. | Xehetasunak hemen gorde dira: %1$s |
| `crash_details_not_saved` | Details could not be saved to %1$s. | Ezin izan dira xehetasunak hemen gorde: %1$s |
