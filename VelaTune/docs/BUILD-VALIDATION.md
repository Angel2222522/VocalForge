# Πραγματικό Android παραδοτέο — 6 Σεπτεμβρίου 2026

Το VelaTune-0.1.0-validation.apk είναι εγκαταστάσιμη, debug-signed εφαρμογή με πραγματικό τοπικό pitch correction. Δεν αποτελεί πιστοποιημένη επαγγελματική έκδοση. Android 8.1+ (API 27), ARM64/ARM32/x86_64. Μέγεθος 5.247.283 bytes.

## Προέλευση και ακεραιότητα

- Πηγαίο commit του APK: `751960ed86a03ad7b8fa974c16894b4476f3b31a`.
- [Επιτυχές GitHub Actions build 33985276634](https://github.com/Angel2222522/VocalForge/actions/runs/33985276634).
- [Απομονωμένο PR 4](https://github.com/Angel2222522/VocalForge/pull/4), φάκελος VelaTune. Το main της προηγούμενης εφαρμογής δεν αντικαταστάθηκε.
- Package: `gr.anelix.velatune.validation`, versionCode 1, target/compile SDK 35.
- SHA-256 APK: `cfebcf50ad026a4829ec150f7db6d9ca0bdf0c20c550ee315fa05eb5c704c1ce`.
- SHA-256 πιστοποιητικού: `1e1b0a4557165e32275c18da605b4515a236c37eccbd1099820dcf9e34512a2b`.
- Η υπογραφή είναι Android debug, όχι production signing identity. Δεν υπάρχει εγγύηση αναβάθμισης με ίδιο κλειδί σε μελλοντικό build. Μην απεγκαταστήσεις χωρίς προηγούμενη εξαγωγή των λήψεων.

## Τι εκτελέστηκε πραγματικά

| Έλεγχος | Αποτέλεσμα |
|---|---|
| Android Java και native C++ compile, 3 ABIs | PASS |
| Android lint | PASS, 0 errors / 11 warnings |
| Debug APK, unsigned release APK, instrumentation APK build | PASS |
| apksigner verify / zipalign 16 KB | PASS |
| Έλεγχος ARM64 και x86_64 ELF load alignment | Όλα τα load segments τουλάχιστον 16 KB |
| Εγκατάσταση και εκκίνηση Android 15 emulator | PASS |
| Native offline WAV render, διατήρηση αρχικού και πλήθους samples | PASS |
| Απόρριψη μη έγκυρου sample rate | PASS |
| Έλεγχος ότι δεν ζητείται INTERNET permission | PASS |
| Πλοήγηση οθόνης μικροφώνου / ηχογραφήσεων | PASS |
| Foreground microphone stream, ενεργός callback, αποθήκευση WAV | PASS |
| Σύνολο instrumentation tests | 5/5, 0 αποτυχίες |
| Οπτικός έλεγχος πραγματικού screenshot αρχικής οθόνης | Αναγνώσιμα controls, διαθέσιμο record button, χωρίς εμφανή επικάλυψη |
| Αναζήτηση fatal exception / fatal signal στο καταγεγραμμένο logcat | Κανένας δείκτης crash στο συγκεκριμένο log |

Τα παραπάνω δεν σημαίνουν ότι δοκιμάστηκε η ακουστική ποιότητα του μικροφώνου ενός πραγματικού κινητού. Το emulator test επαληθεύει ότι εκτελείται η πραγματική ροή και γράφεται αρχείο, όχι το πόσο φυσικά ακούγεται η ανθρώπινη φωνή.

Οι lint warnings αφορούν target/dependency νεότερων εκδόσεων, backup configuration, static application context, εικονίδιο, constructors για layout editor και localization. Δεν απενεργοποιήθηκε το lint για να περάσει το build. Το application context δεν κρατά Activity.

## Μετρήσεις και όρια

Στον Linux host μετρήθηκε καθυστέρηση DSP 48 ms στα 48 kHz. Το χειρότερο μετρημένο block 192 frames χρειάστηκε περίπου 0,122 ms υπολογισμού. Εννέα σταθερά συνθετικά σήματα έδωσαν μέγιστο σφάλμα διορθωμένης νότας 0,575 cent. Πέρασαν 50 WAV checks και ASan/UBSan stress. Αναλυτικά στο BENCHMARKS.md και στα evidence αρχεία.

**Πραγματική end-to-end latency: δεν μετρήθηκε.** Χρειάζεται φυσικό Android, μικρόφωνο/ακουστικά ή loopback hardware. Τα 48 ms είναι ήδη μόνο ο DSP και μπορεί να είναι αισθητά σε live monitoring. Δεν μετρήθηκαν ARM CPU, μπαταρία, θερμοκρασία, μακρόχρονα dropouts, κλήσεις και πραγματικές αλλαγές USB/Bluetooth.

Η εφαρμογή είναι για μεμονωμένη μονοφωνική φωνή. Stereo γίνεται mono. Η διατήρηση formants είναι προσέγγιση TD-PSOLA, χωρίς ανεξάρτητο formant control ή πιστοποίηση φυσικότητας. Δεν υλοποιήθηκε automatic key detection. Δεν έγινε ακουστικό panel με πραγματικούς rap/trap/singing/falsetto/breathy performers. Το συνολικό αρχικό Definition of Done παραμένει ανοικτό ως προς αυτά τα gates.

## Αρχεία αποδεικτικών

- `evidence/github-final-build.log`: πλήρες επιτυχές build και emulator log.
- `evidence/android-final/android-build.txt`: signature, package και alignment checks.
- `evidence/android-final/reports/`: lint και Android test reports.
- `evidence/android-final/android-screen.png`: πραγματικό screenshot.
- `evidence/android-final/android-logcat.txt`: runtime logcat.
- `evidence/android-final/apk-audit.json`: τοπική επαλήθευση hash, libraries, licenses.

Οι μεταγενέστερες αλλαγές στην τεκμηρίωση δεν αλλάζουν τον πηγαίο κώδικα του παραπάνω APK.
