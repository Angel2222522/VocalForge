# Τεχνικός σχεδιασμός και έρευνα

Ημερομηνία έρευνας: 5 Σεπτεμβρίου 2026. Η επιλογή υλοποίησης είναι τεκμηριωμένη υπόθεση προς επικύρωση, όχι αποδεδειγμένος νικητής συγκριτικού shootout. Δεν κατέστη δυνατή η λήψη/μεταγλώττιση των ανταγωνιστικών βιβλιοθηκών για κοινό benchmark.

## Εναλλακτικές DSP

| Προσέγγιση | Ποιότητα, artifacts, formants | Latency / υπολογισμός | Offline / Android / άδεια | Απόφαση |
|---|---|---|---|---|
| YIN + TD-PSOLA | Κρατά χρονικά τη μορφή των παλμών, άρα αποφεύγει την απλή αλλαγή ταχύτητας και μετατόπιση όλων των formants. Ευάλωτο σε pitch marking, θορυβώδη φωνή, μεγάλες μετατοπίσεις και μεταβάσεις· μπορεί να πλατύνει formant bandwidths. | Προβλέψιμη εργασία, χωρίς FFT inference. Εδώ 48 ms DSP· κανένα αποδεδειγμένο πλεονέκτημα end-to-end χωρίς συσκευή. | Δική μας C++17 υλοποίηση MIT, πλήρως offline, portable host/NDK. | Υλοποιήθηκε για μονοφωνικά vocals. |
| Signalsmith Stretch | Φασματική pitch/time επεξεργασία με formant compensation. Χρειάζεται προσεκτικό scheduling αυτοματισμών και αποφυγή smearing. | Διακριτή input/output latency, προαιρετικό split computation αυξάνει latency και μοιράζει CPU spikes. Δεν μετρήθηκε εδώ. | C++, MIT, επιπλέον Signalsmith Linear. Offline συμβατό ως αρχιτεκτονική. | Ισχυρός υποψήφιος επόμενου συγκριτικού τεστ, όχι vendored dependency. |
| Rubber Band R3 / LiveShifter | Ώριμη φασματική επεξεργασία με formant preservation και ειδικές επιλογές φωνής. | Η επίσημη τεκμηρίωση LiveShifter αναφέρει τουλάχιστον περίπου 50 ms processing delay. Δεν είναι η συνολική Android latency. | C++, GPL-2.0-or-later δωρεάν ή εμπορική άδεια. Δωρεάν χρήση απαιτεί συμβατή GPL διανομή της εφαρμογής. | Υποψήφιο για offline render· δεν απορρίφθηκε επειδή είναι επί πληρωμή: υπάρχει δωρεάν GPL επιλογή. |
| SoundTouch | Γενική μεταβολή tempo/pitch, όχι εξειδικευμένος vocal corrector. Δεν βασίζουμε υπόσχεση formant preservation στο API του. | Η σελίδα αναφέρει streaming latency έως περίπου 100 ms· δεν έγινε δική μας μέτρηση. | Android/C++, LGPL-2.1. | Δεν επελέγη για τη βασική φωνητική μηχανή. |
| WORLD source/filter vocoder | Διαχωρίζει F0, spectral envelope και aperiodicity. Ενδιαφέρον για φωνή και έλεγχο formants, αλλά resynthesis δεν συνεπάγεται διαφανή ήχο σε όλες τις φωνές. | Ανάλυση/σύνθεση με περισσότερα στάδια. CPU/μπαταρία εδώ άγνωστα. | Native C++, permissive modified BSD upstream· δεν ενσωματώθηκε. | Υποψήφιο για offline συγκριτική ακρόαση. |
| CREPE / torchcrepe tracker | Periodicity και Viterbi μπορούν να βοηθήσουν έναν tracker· δεν κάνουν pitch shifting. | Απαιτεί μοντέλο και runtime· κόστος inference και causal lookahead θέλουν μετρήσεις σε ARM. | torchcrepe MIT, αλλά Android deployment και weights πρέπει να ελεγχθούν ξεχωριστά. | Δεν προστέθηκε μοντέλο χωρίς αποδεδειγμένο όφελος. |

Πηγές: [YIN, de Cheveigné & Kawahara](https://pubs.aip.org/asa/jasa/article/111/4/1917/547221/YIN-a-fundamental-frequency-estimator-for-speech), [Moulines & Charpentier, PSOLA](https://courses.physics.illinois.edu/ece420/sp2019/5_PSOLA.pdf), [επεξήγηση TD-PSOLA από University of Edinburgh](https://speech.zone/courses/speech-processing/module-6-speech-synthesis-waveform-generation-and-connected-speech/videos/td-psola/), [Signalsmith README](https://github.com/Signalsmith-Audio/signalsmith-stretch/blob/main/README.md), [Rubber Band LiveShifter](https://breakfastquay.com/rubberband/code-doc/classRubberBand_1_1RubberBandLiveShifter.html), [Rubber Band integration](https://breakfastquay.com/rubberband/integration.html), [άδεια Rubber Band](https://breakfastquay.com/rubberband/license.html), [SoundTouch](https://www.surina.net/soundtouch/), [WORLD](https://github.com/mmorise/World), [torchcrepe](https://github.com/maxrmorrison/torchcrepe).

Δεν υπάρχουν έγκυρα συγκριτικά δεδομένα CPU, μπαταρίας, θερμοκρασίας ή σταθερότητας Android για τις παραπάνω επιλογές από αυτή τη δουλειά. Τα σχετικά συμπεράσματα παραμένουν μη μετρημένα. Τα host benchmarks αφορούν μόνο τον δικό μας πυρήνα.

## Android audio και UI

Επιλέχθηκε Oboe 1.9.3 (Apache-2.0) αντί αποκλειστικά AudioRecord/AudioTrack σε Java ή απευθείας AAudio. Προσφέρει κοινό native API και workarounds συσκευών. Ζητείται output callback, φυσικός sample rate εξόδου, mono float, LowLatency και Exclusive, με fallback Shared. Η είσοδος ζητά τον ίδιο sample rate μέσω Oboe conversion και Unprocessed preset, με VoiceRecognition fallback όταν αποτύχει το άνοιγμα. Η πραγματική συσκευή μπορεί να επιβάλει επεξεργασία.

Ο output callback κάνει nonblocking input reads. Ελλιπής ανάγνωση συμπληρώνεται με μηδενικά και μετριέται. Το service αυξάνει το output buffer από δύο μέχρι οκτώ bursts όταν αυξάνονται τα xruns. Η ανεξάρτητη διαφορά clocks εισόδου/εξόδου δεν έχει επιβεβαιωθεί σε πραγματικό hardware· παραμένει release gate.

Η UI υλοποίηση χρησιμοποιεί Java/platform Views. Το UI δεν εκτελεί τον DSP: συνεπώς η επιλογή αυτή δεν αλλάζει τον native αλγόριθμο ή τη sample latency. Δεν χρησιμοποιήθηκε webview. Οι έλεγχοι επικοινωνούν με atomics και ο ήχος μένει σε C++.

Πηγές: [Oboe](https://developer.android.com/games/sdk/oboe), [Android low-latency guidelines](https://developer.android.com/games/sdk/oboe/low-latency-audio), [Oboe 1.9.3](https://github.com/google/oboe/releases/tag/1.9.3), [AGP compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes).

## Pipeline που υλοποιήθηκε

1. Είσοδος float, έλεγχος μη πεπερασμένων τιμών, εξομάλυνση input gain.
2. Τετραπλό one-pole lowpass πριν το downsampling σε περίπου 12–16 kHz. Ο ήχος προς σύνθεση διατηρείται στον αρχικό sample rate. Η απλή αυτή antialias διαδρομή πρέπει να συγκριθεί με ισχυρότερο φίλτρο σε δύσκολο θορυβώδες input.
3. YIN κάθε 5 ms, ιστορικό 46 ms, αναζήτηση 55–1200 Hz, RMS floor και confidence threshold. Cubic fractional-lag refinement για σύντομες περιόδους.
4. Προσωρινή καθυστέρηση αποδοχής octave jumps 15 ms. Είναι heuristic, μπορεί να καθυστερήσει αληθινή αλλαγή οκτάβας.
5. Quantizer για χρωματική, μείζονα, φυσική/αρμονική ελάσσονα, ελάσσονα πεντατονική. Hysteresis για τη μετάβαση νότας.
6. Retune smoothing, Strength. Natural: επιβράδυνση κρατημένης νότας και διατήρηση μέρους της γρήγορης διακύμανσης. Hard: μικρότερη hysteresis, γρηγορότερη απόκριση, χωρίς επιστροφή vibrato. Είναι πραγματικά διαφορετικοί κανόνες controller.
7. Χρονική αντιστοίχιση περιοδικότητας/διόρθωσης με τα αποθηκευμένα δείγματα. Το confidence προσεγγίζει το κέντρο του analysis window· δεν αποτελεί ακριβή phoneme segmentation.
8. Εύρεση θετικού peak κοντά στον προβλεπόμενο παλμό. Hann grains δύο περιόδων, χωρίς resampling του εσωτερικού τους, αλλαγή απόστασης synthesis epochs, overlap-add με εξομάλυνση βάρους.
9. Crossfade προς χρονικά ευθυγραμμισμένο dry όταν δεν ανιχνεύεται περιοδικότητα. Wet/Dry και bypass εξομαλύνονται. Αυτό περιορίζει την αλλοίωση unvoiced τμημάτων, αλλά δεν είναι ειδικός sibilant/transient classifier.
10. Output gain και saturation στο ±1 με μετρητή clipping. Πρόκειται για προστασία ορίων, όχι mastering limiter.

Τα lookup windows και buffers προδεσμεύονται. Δεν γίνονται file I/O, JNI, mutex waits ή allocations στον δικό μας processing callback. Το host allocation test ελέγχει τον DSP, όχι ολόκληρο το Oboe/Android runtime. Δεν ενεργοποιείται fast-math που θα ακύρωνε τους ελέγχους NaN. Δεν γράφτηκε μη μετρημένο NEON assembly· compiler auto-vectorization είναι δυνατότητα του build, όχι επιβεβαιωμένη βελτιστοποίηση ARM.

## Ιδιοκτησία και αποθήκευση

`MainActivity / ControlPanel` → `AudioService` για live, `ProjectStore` για editor → `NativeAudio` → ανεξάρτητο `TuneEngine`.

Το service ανήκει στη ζωντανή συνεδρία και τρέχει με notification. Η ουρά εγγραφής είναι SPSC 1.048.576 float samples, περίπου 21,8 s στα 48 kHz. Η Java πλευρά αδειάζει την ουρά εκτός audio callback και γράφει WAV με checkpoint ανά περίπου 2 s. Overflow διακόπτει την εγγραφή ως σφάλμα· δεν προσποιείται ακέραιο take. `.part` λήψεις ανακτώνται στην επόμενη έναρξη από το πραγματικό μήκος δεδομένων.

Τα imports περνούν από SAF. WAV PCM16/24/32/float32 διαβάζεται απευθείας. Άλλοι τύποι (π.χ. MP3, AAC/M4A, FLAC, Ogg) περνούν από MediaExtractor/MediaCodec, εφόσον υπάρχει decoder συσκευής. Άγνωστο codec επιστρέφει σφάλμα. Δεν περιλαμβάνονται FFmpeg binaries ή codecs με ανεξέλεγκτη άδεια. [Android supported formats](https://developer.android.com/media/platform/supported-formats).

Τα imports/render είναι streaming και bounded σε μνήμη, με όριο 1,8 GB WAV data ανά αρχείο. Ιδιωτική αναπαράσταση: mono float WAV. Πρωτότυπη εξωτερική πηγή δεν γράφεται ποτέ. Render σε staging → close/fsync → rename. Το export χρησιμοποιεί ACTION_CREATE_DOCUMENT και PCM16/24 με TPDF dither ή float32. Δεν γίνεται upsampling για να παρουσιαστεί πλασματική βελτίωση ποιότητας.

Το offline mode προς το παρόν χρησιμοποιεί **τον ίδιο DSP με 80 ms delay**, όχι αποδεδειγμένα ανώτερο αλγόριθμο. Η μεγαλύτερη καθυστέρηση επιτρέπει ασφαλές scheduling, αλλά δεν βαφτίζεται μεγαλύτερη ακουστική ποιότητα.

## Key detection

Δεν ενεργοποιήθηκε automatic key detection. Η μονοφωνική μελωδία μπορεί να συμφωνεί με πολλές κλίμακες, ειδικά σχετική μείζονα/ελάσσονα, και spoken rap συχνά δεν προσφέρει σταθερό τονικό υλικό. Histogram φθόγγων από τον ίδιο tracker δεν αρκεί για υπόσχεση αξιόπιστης τονικότητας beat. Παραμένει χειροκίνητος ορισμός χωρίς ψεύτικο confidence score.

## Product UX

Σκοτεινός καμβάς, μία πράσινη κύρια ενέργεια, κόκκινο clipping, δύο οθόνες: Μικρόφωνο / Ηχογραφήσεις. Τονική πάνω από τη μεγάλη νότα, meters και monitoring, record σταθερά χαμηλά. Τα ρυθμιστικά έχουν τουλάχιστον 48 dp ύψος. Ελληνικά και λατινικά με bundled DejaVu Sans regular/bold και πλήρη άδεια. Waveform και ξεχωριστή προσβάσιμη seek bar. Μεγάλο μέγεθος κειμένου, rotation, TalkBack και insets απαιτούν ακόμη πραγματική οπτική επιθεώρηση Android. Δεν παραδόθηκε mockup ως απόδειξη εφαρμογής.

## Capability / permission matrix

| Λειτουργία | Αποθήκευση / άδεια | Όταν δεν είναι διαθέσιμη |
|---|---|---|
| Live μικρόφωνο | RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_MICROPHONE | Επεξήγηση/ρυθμίσεις, editor παραμένει διαθέσιμος |
| Notification | POST_NOTIFICATIONS από API33 | Η άρνηση δεν κάνει το permission μικροφώνου «εγκεκριμένο»· χειρίζονται χωριστά |
| Import/export | SAF URIs, χωρίς broad storage permission | Ορατό σφάλμα και ανέπαφη πηγή |
| Monitoring | Wired/USB έξοδος | Ηχείο/Bluetooth monitoring αποκλείεται στην τρέχουσα έκδοση |
| Ιστορικό | Ιδιωτικά αρχεία, versioned preferences | Έλεγχος ύπαρξης, recovery `.part` |
| Internet/account | Κανένα | Δεν χρειάζεται |
| Backup | allowBackup=false | Δεν υπόσχεται cloud backup ή κρυπτογραφημένο vault |

[Foreground microphone requirements](https://developer.android.com/develop/background-work/services/fgs/service-types).
