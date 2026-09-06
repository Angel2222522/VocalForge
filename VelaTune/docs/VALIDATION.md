# Κατάσταση επικύρωσης

**Release απόφαση: VALIDATION APK / όχι production certification.** Το Definition of Done του χρήστη δεν καλύπτεται. Η αποθήκευση αυτού του project είναι παράδοση της πραγματικής δουλειάς που έγινε, όχι πιστοποίηση λειτουργικής επαγγελματικής Android εφαρμογής.

## Τι εκτελέστηκε

| Έλεγχος | Επίπεδο | Αποτέλεσμα / αρχείο |
|---|---|---|
| Native C++ compilation με GCC13.3, C++17, O3 | host build | Πέρασε, `scripts/test-host.sh` |
| YIN + πραγματικό corrected output σε 8/16/44.1/48/96 kHz | host unit | Πέρασε, `evidence/dsp-tests.txt` |
| 9 επιπλέον σταθερά αρμονικά σήματα περίπου 57–1064 Hz | ανεξάρτητη αντικειμενική ανάλυση | Απόκλιση output <0,6 cent, `objective-analysis.json` |
| Σιωπή, λευκός θόρυβος, NaN, clipping bounds | host unit | Πέρασε |
| Σταθερότητα ως προς μέγεθος block | host analysis | Μέγιστη διαφορά 0 στο bypass σενάριο |
| Αλλαγές νότας, glissando, vibrato, συνθετικά breath/noise τμήματα | host analysis | Εξήχθη πραγματικός ήχος και επιθεωρήθηκε φασματογράφημα· δεν είναι ακουστικό panel |
| Κατανομή μνήμης μέσα στον DSP | host allocation counter | 0 δυναμικές κατανομές κατά την processing loop |
| Τυχαία buffers/controls, 9 sample rates, live/offline | host ASan/UBSan | Πέρασε, `sanitizer.txt` |
| 10 λεπτά συνθετικής ροής σε bounded buffers | host stress | Πεπερασμένο output, περίπου 8,44 s host wall time |
| PCM16/24/float32 WAV roundtrips, seek, recovery, malformed input | host Java | 50 checks πέρασαν, `wave-tests.txt` |
| Parse Java αρχείων | host syntax | 13 αρχεία πέρασαν· ΔΕΝ είναι Android type checking |
| Native delay | host impulse | 2304 samples / 48 ms στα 48 kHz στο live mode |

Το RMS threshold και οι accuracy gates δηλώνονται μέσα στα tests. Δεν αξιολογήθηκε το correction μόνο από το target meter: η συχνότητα μετρήθηκε και στο παραγόμενο output. Για την ανεξάρτητη ανάλυση χρησιμοποιήθηκε FFT autocorrelation με επιλογή της πρώτης σχεδόν ισοδύναμης κορυφής και interpolation. Αρχικά επέλεγε τη μεγαλύτερη ακέραιη κορυφή και έδινε ψευδή υποοκτάβα στα 880 Hz· έλεγχος φάσματος επιβεβαίωσε 880/1760/2640 Hz και ο estimator διορθώθηκε. Αυτό δεν ήταν διορθωμένο bug του DSP.

## Τι δεν εκτελέστηκε

- Upgrade από προηγούμενη υπογεγραμμένη έκδοση και εγκατάσταση σε φυσικό Android κινητό.
- Πραγματικό microphone/monitoring, DAC/ADC round-trip latency, USB/Bluetooth routing, ARM CPU.
- Κλήσεις, audio focus/route races, screen-off, process death, rotation, permissions σε συσκευή.
- Ακουστική δοκιμή με πραγματικούς rap/trap/singing/falsetto/breathy performers.
- Αντικειμενική πιστοποίηση formant preservation, intelligibility, phasiness ή naturalness σε πραγματική φωνή.
- TalkBack, μεγάλα fonts και πλήρης οπτική δοκιμή όλων των μεγεθών οθόνης.
- Θερμοκρασία, μπαταρία, sustained ARM throttling.
- LeakSanitizer: εκκίνηση δοκιμάστηκε, αλλά απέτυχε να ανοίξει `/proc/.../task` στο sandbox. Διατηρείται το πραγματικό log στο `leak-sanitizer-unavailable.txt`. Ο έλεγχος ASan/UBSan επαναλήφθηκε με leak detection disabled, χωρίς να κρυφτεί η ξεχωριστή εκκρεμότητα.

## Android build και εκτέλεση

Το αρχικό τοπικό περιβάλλον δεν είχε SDK/NDK και οι λήψεις toolchain απέτυχαν. Αυτό λύθηκε με εξωτερικό GitHub Actions build, μετά από εξουσιοδότηση του χρήστη, σε απομονωμένο branch του VocalForge. Δεν απαιτείται υπολογιστής για εγκατάσταση του APK.

Πέρασαν Java/NDK cross compilation για ARM64/ARM32/x86_64, Android lint (0 errors), D8/R8, debug και unsigned release packaging, instrumentation APK build, apksigner και zipalign με 16 KB. Οι 11 lint warnings καταγράφονται αυτούσιες. Η προειδοποίηση static Context αφορά application context, όχι αποθηκευμένη Activity.

Σε Android 15 x86_64 emulator πέρασαν 5 instrumentation tests: native offline render με διατήρηση source και sample count, έλλειψη INTERNET permission, απόρριψη μη έγκυρου sample rate, πλοήγηση μικρόφωνο/ηχογραφήσεις, και foreground εγγραφή WAV με ενεργό native callback. Δεν ελέγχθηκε ανθρώπινη φωνή ή φυσική ακουστική έξοδος.

Η αρχική εκτέλεση αυτών των 5 tests πέρασε, αλλά το μεταγενέστερο screenshot step απέτυχε επειδή το Gradle είχε απεγκαταστήσει το test target. Προστέθηκε επανεγκατάσταση του APK πριν από το screenshot. Τα ακριβή τελικά στοιχεία υπάρχουν στο BUILD-VALIDATION.md.

## Known limitations και ανοικτά τεχνικά θέματα

1. **Επαγγελματική ποιότητα μη αποδεδειγμένη.** Το spectral/temporal μοντέλο είναι πραγματικό αλλά νεότερη υλοποίηση, όχι ισοδύναμο εμπορικού Auto-Tune.
2. **48 ms latency μόνο του DSP.** Μπορεί να είναι ενοχλητική για live monitoring και αυξάνεται από hardware/Android. Η μικρότερη output buffer ρύθμιση δεν εξαφανίζει την καθυστέρηση ανάλυσης/σύνθεσης.
3. **Μόνο μονοφωνική φωνή 55–1200 Hz.** Δεν διαχωρίζει φωνή από beat, δεν διορθώνει ανεξάρτητα διπλές φωνές και δεν κάνει stereo phase-coherent correction. Stereo εισαγωγή γίνεται mono και μπορεί να ακυρωθούν αντικρουόμενα κανάλια.
4. **Formant approximation.** Χρησιμοποιεί μη επαναδειγματοληπτημένα grains, όχι ρητή εκτίμηση vocal tract. Δεν υπάρχει ανεξάρτητος formant slider. Transient/sibilant προστασία είναι periodicity-based crossfade· difficult consonants και rough/breathy voice μπορεί να δώσουν artifacts.
5. **Offline ίδιος αλγόριθμος.** Τα 80 ms offline scheduling δεν τεκμηριώνουν ανώτερη ποιότητα. Απαιτείται συγκριτικό render με Signalsmith/Rubber Band/WORLD.
6. **Δεν υπάρχει αυτόματη κλίμακα.** Δεν εμφανίζεται επινοημένη βεβαιότητα.
7. **Live take preroll.** Το αποθηκευμένο live take περιλαμβάνει τον αρχικό DSP χρόνο και το flushed tail. Ακριβής ευθυγράμμιση με εξωτερικό beat χρειάζεται μετρημένο routing latency.
8. **Περιορισμένη κάλυψη Android συσκευών.** Έγινε πραγματικό build και εκτέλεση σε emulator Android 15. Αυτό δεν αποκλείει lifecycle/routing προβλήματα σε διαφορετικούς κατασκευαστές ή φυσικό hardware.
9. **Ουρά recording / clocks.** Αλλαγές συσκευής διακόπτουν τη συνεδρία αντί αυτόματου hot-switch. Input/output clock drift και error callback teardown χρειάζονται ιδιαίτερο physical stress test.
10. **Εξαγωγή/ακύρωση.** Ακυρωμένη εγγραφή σε SAF destination μπορεί να αφήσει μερικό αρχείο στον προορισμό. Το τοπικό source/result παραμένει ανέπαφο. Δεν υπάρχει διαχείριση/διαγραφή παλαιών projects από το UI· αρχεία καταλαμβάνουν χώρο μέχρι export/διαχείριση εφαρμογής.
11. **Codec support εξαρτώμενο από Android.** Ο κώδικας απορρίπτει μη υποστηριζόμενα formats. Δεν υπόσχεται κάθε παραλλαγή AAC/FLAC/OGG σε κάθε κατασκευαστή.
12. **No release identity.** Δεν δημιουργήθηκαν/δεσμεύτηκαν production signing secrets. Το επόμενο signed validation artifact πρέπει να επισημανθεί debug, όχι release.

## Επόμενα αναγκαία gates

1. Build/compiler/lint gates ολοκληρώθηκαν. Διατήρηση των ίδιων gates στις επόμενες αλλαγές.
2. APK signature/package/ABI/alignment gates ολοκληρώθηκαν. Production signing identity και ασφαλής δυνατότητα upgrade παραμένουν ξεχωριστά εκκρεμή.
3. Εγκατάσταση σε Android27/30/35 και τουλάχιστον μία πραγματική ARM64 συσκευή. Εκτέλεση instrumentation tests.
4. Mic denied/revoked, μικρός χώρος, malformed import, μακρύ render, cancel, κλήση, USB unplug/replug, rotate, screen-off, process kill, γρήγορα start/stop 100 φορές.
5. Ακουστικό loopback και τουλάχιστον 20 μετρήσεις routing σε κάθε έξοδο. Έπειτα real vocal A/B ακρόαση και optimization latency/quality.
6. Πραγματικό visual review κάθε οθόνης σε μικρό κινητό, landscape, μεγάλα fonts και TalkBack.

Το APK παραδίδεται για εγκατάσταση και πραγματική δοκιμή. Απόφαση επαγγελματικού production release απαιτεί τα εκκρεμή physical και acoustic gates.
